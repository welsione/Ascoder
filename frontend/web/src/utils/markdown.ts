import MarkdownIt from 'markdown-it'
import { sanitizeHtml, sanitizeMermaidSvg } from './sanitize'

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: true,
})

type MermaidApi = typeof import('mermaid').default

let mermaidPromise: Promise<MermaidApi> | null = null

async function loadMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then((module) => {
      const mermaid = module.default
      mermaid.initialize(mermaidConfig)
      return mermaid
    })
  }
  return mermaidPromise
}

export const mermaidConfig = {
  startOnLoad: false,
  theme: 'default',
  securityLevel: 'loose',
  htmlLabels: true,
} as const

markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const href = token.attrGet('href') ?? ''
  if (/^https?:\/\//i.test(href)) {
    token.attrSet('target', '_blank')
    token.attrSet('rel', 'noopener noreferrer')
  }
  return self.renderToken(tokens, idx, options)
}

function isMermaidFence(info: string) {
  return info.trim().split(/\s+/)[0]?.toLowerCase() === 'mermaid'
}

function isMermaidSource(content: string) {
  const firstLine = content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find(Boolean)
    ?.toLowerCase()

  return !!firstLine && /^(flowchart|graph|sequencediagram|classdiagram|statediagram(?:-v2)?|erdiagram|gantt|pie|journey|gitgraph|mindmap|timeline|quadrantchart|requirementdiagram|c4context|block-beta|architecture-beta)\b/.test(firstLine)
}

function renderMermaidPlaceholder(content: string, idx: number) {
  const uniqueId = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`
  const id = `mermaid-${idx}-${uniqueId}`
  return `<div class="mermaid-placeholder" data-mermaid-id="${id}" data-mermaid-source="${encodeURIComponent(content)}"></div>`
}

/** 将 mermaid 代码块渲染为占位 div，由 `renderMermaidBlocks` 异步替换 */
markdown.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const info = token.info?.trim() ?? ''
  if (isMermaidFence(info) || (!info && isMermaidSource(token.content))) {
    return renderMermaidPlaceholder(token.content, idx)
  }
  const langClass = info ? ` class="language-${info}"` : ''
  return `<pre><code${langClass}>${markdown.utils.escapeHtml(token.content)}</code></pre>`
}

markdown.renderer.rules.code_block = (tokens, idx) => {
  const token = tokens[idx]
  if (isMermaidSource(token.content)) {
    return renderMermaidPlaceholder(token.content, idx)
  }
  return `<pre><code>${markdown.utils.escapeHtml(token.content)}</code></pre>`
}

export function renderMarkdown(content: string | null | undefined) {
  if (!content?.trim()) {
    return ''
  }
  return sanitizeHtml(markdown.render(content))
}

export function isMermaidErrorSvg(svg: string) {
  const text = stripTags(svg)
  return /Syntax error in text|Parse error on line|Lexical error on line|No diagram type detected|error-icon|parsererror/i.test(text)
}

function stripTags(value: string) {
  return value.replace(/<style[\s\S]*?<\/style>/gi, '').replace(/<[^>]+>/g, ' ')
}

function cleanupMermaidErrorArtifacts() {
  const errorPattern = /Syntax error in text|Parse error on line|Lexical error on line|No diagram type detected|mermaid version \d/i
  document.body?.querySelectorAll<HTMLElement>(':scope > *').forEach((el) => {
    if (el.id === 'app' || el.matches('script, style, link')) {
      return
    }
    if (errorPattern.test(el.textContent ?? '')) {
      el.remove()
    }
  })
}

/** 在 DOM 中查找 mermaid 占位 div 并异步渲染为 SVG */
export async function renderMermaidBlocks(container: HTMLElement) {
  const placeholders = container.querySelectorAll<HTMLElement>('.mermaid-placeholder')
  if (!placeholders.length) {
    return
  }
  const mermaid = await loadMermaid()
  for (const el of placeholders) {
    const source = decodeURIComponent(el.dataset.mermaidSource ?? '')
    if (!source) continue
    try {
      await mermaid.parse(source)
      cleanupMermaidErrorArtifacts()
      const { svg } = await mermaid.render(el.dataset.mermaidId ?? 'mermaid', source)
      if (isMermaidErrorSvg(svg)) {
        throw new Error('Mermaid syntax error')
      }
      cleanupMermaidErrorArtifacts()
      el.innerHTML = sanitizeMermaidSvg(svg)
      el.classList.remove('mermaid-placeholder')
      el.classList.add('mermaid-rendered')
    } catch (error) {
      console.warn('Mermaid render failed, fallback to code block.', error)
      cleanupMermaidErrorArtifacts()
      el.innerHTML = `<pre><code>${markdown.utils.escapeHtml(source)}</code></pre>`
      el.classList.remove('mermaid-placeholder')
      el.classList.add('mermaid-fallback')
    }
  }
}
