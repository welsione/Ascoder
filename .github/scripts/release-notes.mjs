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
import Anthropic from '@anthropic-ai/sdk'
import { readFile, writeFile } from 'node:fs/promises'

const MAX_DIFF_CHARS = 120_000
const MAX_OUTPUT_TOKENS = 4096
const DIFF_FILE = process.env.DIFF_FILE || '/tmp/release-diff.txt'
const FALLBACK_FILE = '/tmp/release-notes-fallback.md'

function mustGet(name) {
  const v = process.env[name]
  if (!v) {
    throw new Error(`Missing env var ${name}`)
  }
  return v
}

function getOptional(name, def) {
  return process.env[name] ?? def
}

async function main() {
  const apiKey = mustGet('LLM_API_KEY')
  const baseUrl = getOptional('BASE_URL', 'https://api.minimaxi.com/anthropic')
  const modelId = getOptional('MODEL_ID', 'MiniMax-M3')
  const newTag = mustGet('NEW_TAG')
  const commitList = mustGet('COMMIT_LIST')
  const repo = mustGet('GITHUB_REPOSITORY')
  const token = mustGet('GITHUB_TOKEN')

  // 1. read diff
  let diff = ''
  try {
    diff = await readFile(DIFF_FILE, 'utf8')
  } catch {
    diff = '(unable to read diff)'
  }
  if (diff.length > MAX_DIFF_CHARS) {
    diff = diff.slice(0, MAX_DIFF_CHARS) + '\n\n... [diff truncated due to size limit] ...'
  }

  // 2. generate fallback notes (commit list only)
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
  const client = new Anthropic({ apiKey, baseURL: baseUrl })
  let reviewText
  let message
  try {
    message = await client.messages.create({
      model: modelId,
      max_tokens: MAX_OUTPUT_TOKENS,
      system:
        '你是 Ascoder 仓库的发布说明撰写员，严格按照 user prompt 中给出的输出格式生成中文 release notes。',
      messages: [{ role: 'user', content: userPrompt }],
    })

    // Debug: log raw response structure
    console.log('API response stop_reason:', message.stop_reason)
    console.log('API response content blocks:', message.content.length)
    for (const [i, block] of message.content.entries()) {
      console.log(`  block[${i}] type=${block.type} text_length=${block.text?.length ?? 'N/A'}`)
    }

    // Extract review text - handle both standard Anthropic format and non-standard providers
    reviewText = message.content
      .filter((b) => b.type === 'text')
      .map((b) => b.text)
      .join('\n')
      .trim()

    // Fallback: if no 'text' blocks found, try extracting text from any block that has it
    if (!reviewText && message.content.length > 0) {
      console.log('No type=text blocks found, trying fallback extraction...')
      reviewText = message.content
        .map((b) => (typeof b === 'string' ? b : b.text ?? ''))
        .filter(Boolean)
        .join('\n')
        .trim()
    }
  } catch (err) {
    console.error('LLM call failed, using fallback:', err.message)
    console.log(`FALLBACK_NOTES=${FALLBACK_FILE}`)
    return
  }

  if (!reviewText) {
    console.error('Empty LLM response, using fallback')
    console.error('Full API response (for debugging):')
    console.error(JSON.stringify(message, null, 2))
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
