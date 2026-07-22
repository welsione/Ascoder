#!/usr/bin/env node
/**
 * AI Code Review via any Anthropic-compatible LLM endpoint.
 *
 * Required env:
 *   LLM_API_KEY    API key for the chosen provider
 *   BASE_URL       default https://api.minimaxi.com/anthropic (MiniMax) /
 *                  https://api.anthropic.com (Anthropic) / etc.
 *   MODEL_ID       default MiniMax-M3
 *   PR_NUMBER      PR number to comment on
 *   PR_TITLE       PR title
 *   PR_BODY        PR body (may be null)
 *   PR_BASE_REF    e.g. master
 *   GITHUB_REPOSITORY  e.g. welsione/Ascoder
 *   GITHUB_TOKEN   GitHub Actions token (pull-requests: write)
 */
import { readFile } from 'node:fs/promises'
import { execSync } from 'node:child_process'
import {
  MAX_OUTPUT_TOKENS,
  mustGet,
  getOptional,
  truncateDiff,
  createLlmClient,
  callLlm,
  extractText,
} from './llm-utils.mjs'

async function main() {
  const modelId = getOptional('MODEL_ID', 'MiniMax-M3')

  const prNumber = mustGet('PR_NUMBER')
  const prTitle = mustGet('PR_TITLE')
  const prBody = getOptional('PR_BODY', '')
  const baseRef = mustGet('PR_BASE_REF')
  const repo = mustGet('GITHUB_REPOSITORY')
  const token = mustGet('GITHUB_TOKEN')

  // 1. git diff against base ref, truncated
  let diff
  try {
    diff = execSync(`git diff --no-color origin/${baseRef}...HEAD`, {
      maxBuffer: 32 * 1024 * 1024,
      encoding: 'utf8',
    })
  } catch (e) {
    diff = ''
  }
  diff = truncateDiff(diff)

  // 2. changed file list
  let fileList
  try {
    fileList = execSync(
      `git diff --name-only origin/${baseRef}...HEAD`,
      { encoding: 'utf8' }
    ).trim()
  } catch (e) {
    fileList = '(unable to list files)'
  }

  // 3. load prompt + render
  const promptTemplate = await readFile(new URL('../ai-review-prompt.md', import.meta.url), 'utf8')
  const userPrompt = promptTemplate
    .replace('{{DIFF}}', `\n\`\`\`diff\n${diff}\n\`\`\``) +
    `\n\n实际 PR 标题：${prTitle}\n\n实际 PR 描述：\n${prBody || '(无)'}\n\n改动文件清单：\n${fileList}\n`

  // 4. call LLM via Anthropic SDK
  const client = createLlmClient()
  const message = await callLlm(client, {
    model: modelId,
    max_tokens: MAX_OUTPUT_TOKENS,
    system:
      '你是 Ascoder 仓库的代码评审员，严格按照 user prompt 中给出的输出格式与评审维度返回中文评审。',
    messages: [
      {
        role: 'user',
        content: userPrompt,
      },
    ],
  })

  const reviewText = extractText(message)

  if (!reviewText) {
    console.error('Full API response (for debugging):')
    console.error(JSON.stringify(message, null, 2))
    throw new Error('Empty reviewer response')
  }

  // 5. find bot's previous review comment and update or create new
  const commentHeader = '## 🤖 AI Code Review'
  const finalBody = `${commentHeader}\n\n${reviewText}`

  const listUrl = `https://api.github.com/repos/${repo}/issues/${prNumber}/comments?per_page=100`
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'ascoder-ai-review',
  }
  const listRes = await fetch(listUrl, { headers })
  if (!listRes.ok) {
    throw new Error(`List comments failed: ${listRes.status} ${await listRes.text()}`)
  }
  const comments = await listRes.json()
  const existing = comments.find((c) => (c.body ?? '').includes(commentHeader))

  if (existing) {
    const updateUrl = `https://api.github.com/repos/${repo}/issues/comments/${existing.id}`
    const updateRes = await fetch(updateUrl, {
      method: 'PATCH',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ body: finalBody }),
    })
    if (!updateRes.ok) {
      throw new Error(`Update comment failed: ${updateRes.status} ${await updateRes.text()}`)
    }
    console.log(`Updated existing comment ${existing.id}`)
  } else {
    const createUrl = `https://api.github.com/repos/${repo}/issues/${prNumber}/comments`
    const createRes = await fetch(createUrl, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ body: finalBody }),
    })
    if (!createRes.ok) {
      throw new Error(`Create comment failed: ${createRes.status} ${await createRes.text()}`)
    }
    console.log('Posted new review comment')
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
