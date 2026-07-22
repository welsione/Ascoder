/**
 * LLM 调用与配置工具：供 ai-review.mjs 与 release-notes.mjs 共享，
 * 避免 mustGet / getOptional / LLM client 初始化 / 响应提取等逻辑重复。
 *
 * 所有脚本通过 `import { ... } from './llm-utils.mjs'` 引用。
 */
import Anthropic from '@anthropic-ai/sdk'

export const MAX_DIFF_CHARS = 120_000
export const MAX_OUTPUT_TOKENS = 8192

/** 读取必填环境变量，缺失则抛错。 */
export function mustGet(name) {
  const v = process.env[name]
  if (!v) {
    throw new Error(`Missing env var ${name}`)
  }
  return v
}

/**
 * 读取可选环境变量，未设置或空字符串时回退到默认值。
 * 用 || 而非 ??，兼容 GitHub vars 未配置时返回空字符串的情况。
 */
export function getOptional(name, def) {
  const v = process.env[name]
  return v || def
}

/**
 * 截断 diff 到 MAX_DIFF_CHARS，超出部分追加截断标记。
 */
export function truncateDiff(diff) {
  if (diff.length > MAX_DIFF_CHARS) {
    return diff.slice(0, MAX_DIFF_CHARS) + '\n\n... [diff truncated due to size limit] ...'
  }
  return diff
}

/**
 * 构造 Anthropic 客户端。
 */
export function createLlmClient() {
  const apiKey = mustGet('LLM_API_KEY')
  const baseUrl = getOptional('BASE_URL', 'https://api.minimaxi.com/anthropic')
  return new Anthropic({ apiKey, baseURL: baseUrl })
}

/**
 * 调用 LLM 生成消息，处理 thinking 耗尽输出 token 导致空文本的情况。
 *
 * 如果模型使用了 extended thinking 且没有产出文本（thinking 占满了 max_tokens），
 * 自动以 thinking=disabled 重试一次，确保拿到实际输出。
 *
 * @param {Anthropic} client - Anthropic 客户端
 * @param {object} createParams - messages.create 参数（model/max_tokens/system/messages）
 * @returns {Promise<object>} message 响应
 */
export async function callLlm(client, createParams) {
  let message = await client.messages.create(createParams)

  const hasThinking = message.content.some((b) => b.type === 'thinking')
  const hasEmptyText = message.content.some(
    (b) => b.type === 'text' && (!b.text || b.text.trim() === '')
  )
  if (hasThinking && hasEmptyText) {
    console.log('Model used thinking and left no text, retrying with thinking disabled...')
    message = await client.messages.create({ ...createParams, thinking: { type: 'disabled' } })
  }

  // Debug: 打印响应结构，便于定位 provider 兼容性问题
  console.log('API response stop_reason:', message.stop_reason)
  console.log('API response content blocks:', message.content.length)
  for (const [i, block] of message.content.entries()) {
    console.log(`  block[${i}] type=${block.type} text_length=${block.text?.length ?? 'N/A'}`)
  }

  return message
}

/**
 * 从 LLM 响应中提取文本，兼容标准与非标准 Anthropic provider。
 *
 * 标准格式：content blocks with type='text'；
 * 非标准 provider：block 的 type 可能不是 'text'，或直接返回字符串而非对象。
 * 先按标准提取，为空时再从任意含 text 的 block 或纯字符串提取。
 */
export function extractText(message) {
  let text = message.content
    .filter((b) => b.type === 'text')
    .map((b) => b.text)
    .join('\n')
    .trim()

  if (!text && message.content.length > 0) {
    console.log('No type=text blocks found, trying fallback extraction...')
    text = message.content
      .map((b) => (typeof b === 'string' ? b : b.text ?? ''))
      .filter(Boolean)
      .join('\n')
      .trim()
  }

  return text
}
