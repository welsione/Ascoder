#!/usr/bin/env node
/**
 * AI Release Notes Generator via any Anthropic-compatible LLM endpoint.
 *
 * Required env:
 *   LLM_API_KEY    API key for the chosen provider
 *   BASE_URL       default https://api.minimaxi.com/anthropic
 *   MODEL_ID       default MiniMax-M3
 *   NEW_TAG        the new version tag (e.g. v0.1.3)
 *   COMMIT_LIST    commit list since last tag (oneline format)
 *   GITHUB_REPOSITORY  e.g. welsione/Ascoder
 *   GITHUB_TOKEN   GitHub Actions token
 *
 * Optional env:
 *   DIFF_FILE      path to diff file (default: /tmp/release-diff.txt)
 */
import { readFile, writeFile } from 'node:fs/promises'
import {
  MAX_OUTPUT_TOKENS,
  mustGet,
  getOptional,
  truncateDiff,
  createLlmClient,
  callLlm,
  extractText,
} from './llm-utils.mjs'

const DIFF_FILE = process.env.DIFF_FILE || '/tmp/release-diff.txt'
const FALLBACK_FILE = '/tmp/release-notes-fallback.md'

async function main() {
  const modelId = getOptional('MODEL_ID', 'MiniMax-M3')
  const newTag = mustGet('NEW_TAG')
  const commitList = mustGet('COMMIT_LIST')
  // repo / token 读取用于校验配置存在，实际写 release 由 workflow 侧 gh CLI 完成
  mustGet('GITHUB_REPOSITORY')
  mustGet('GITHUB_TOKEN')

  // 1. read diff
  let diff = ''
  try {
    diff = await readFile(DIFF_FILE, 'utf8')
  } catch {
    diff = '(unable to read diff)'
  }
  diff = truncateDiff(diff)

  // 2. generate fallback notes (commit list only) -- LLM 失败时使用
  const fallbackNotes = `## 变更列表\n\n${commitList
    .split('\n')
    .filter(Boolean)
    .map((line) => `- ${line}`)
    .join('\n')}\n`
  await writeFile(FALLBACK_FILE, fallbackNotes, 'utf8')

  // 3. load prompt + render
  const promptTemplate = await readFile(
    new URL('../release-notes-prompt.md', import.meta.url),
    'utf8'
  )
  const userPrompt =
    promptTemplate
      .replace('{{DIFF}}', `\n\`\`\`diff\n${diff}\n\`\`\``) +
    `\n\n版本号：${newTag}\n\n自上次发布以来的 commit 列表：\n${commitList}\n`

  // 4. call LLM
  const client = createLlmClient()
  let reviewText
  try {
    const message = await callLlm(client, {
      model: modelId,
      max_tokens: MAX_OUTPUT_TOKENS,
      system:
        '你是 Ascoder 仓库的发布说明撰写员，严格按照 user prompt 中给出的输出格式生成中文 release notes。',
      messages: [{ role: 'user', content: userPrompt }],
    })
    reviewText = extractText(message)
    if (!reviewText) {
      console.error('Empty LLM response, using fallback')
      console.error('Full API response (for debugging):')
      console.error(JSON.stringify(message, null, 2))
    }
  } catch (err) {
    console.error('LLM call failed, using fallback:', err.message)
    reviewText = ''
  }

  if (!reviewText) {
    console.log(`FALLBACK_NOTES=${FALLBACK_FILE}`)
    return
  }

  // 5. write notes to file
  const notesFile = '/tmp/release-notes.md'
  await writeFile(notesFile, reviewText, 'utf8')
  console.log(`NOTES_FILE=${notesFile}`)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
