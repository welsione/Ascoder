import { describe, it, expect } from 'vitest'
import { isMermaidErrorSvg, mermaidConfig, renderMarkdown } from '../utils/markdown'

describe('renderMarkdown', () => {
  it('returns empty string for null/undefined/blank input', () => {
    expect(renderMarkdown(null)).toBe('')
    expect(renderMarkdown(undefined)).toBe('')
    expect(renderMarkdown('   ')).toBe('')
  })

  it('renders basic markdown', () => {
    const result = renderMarkdown('**bold**')
    expect(result).toContain('<strong>bold</strong>')
  })

  it('adds target=_blank to external links', () => {
    const result = renderMarkdown('[link](https://example.com)')
    expect(result).toContain('target="_blank"')
    expect(result).toContain('rel="noopener noreferrer"')
  })

  it('does not add target=_blank to non-http links', () => {
    const result = renderMarkdown('[local](/path)')
    expect(result).not.toContain('target="_blank"')
  })

  it('renders mermaid fences as renderable placeholders', () => {
    const result = renderMarkdown('```mermaid\ngraph TD\n  A --> B\n```')
    expect(result).toContain('class="mermaid-placeholder"')
    expect(result).toContain('data-mermaid-source=')
    expect(result).not.toContain('<pre><code')
  })

  it('detects mermaid fences with uppercase language and options', () => {
    const result = renderMarkdown('```Mermaid title\nflowchart LR\n  A --> B\n```')
    expect(result).toContain('class="mermaid-placeholder"')
  })

  it('detects unlabeled fences that contain mermaid source', () => {
    const result = renderMarkdown('```\nflowchart TD\n  A --> B\n```')
    expect(result).toContain('class="mermaid-placeholder"')
    expect(result).not.toContain('<pre><code')
  })

  it('detects indented code blocks that contain mermaid source', () => {
    const result = renderMarkdown('    flowchart TD\n      A --> B')
    expect(result).toContain('class="mermaid-placeholder"')
    expect(result).not.toContain('<pre><code')
  })

  it('uses Mermaid default HTML labels for centered node text', () => {
    expect(mermaidConfig.theme).toBe('default')
    expect(mermaidConfig.htmlLabels).toBe(true)
  })

  it('does not treat Mermaid version text as a render error by itself', () => {
    expect(isMermaidErrorSvg('<svg><text>mermaid version 11.15.0</text></svg>')).toBe(false)
    expect(isMermaidErrorSvg('<svg><g id="error-icon"></g><text>valid diagram</text></svg>')).toBe(false)
    expect(isMermaidErrorSvg('<svg><text>Syntax error in text</text></svg>')).toBe(true)
  })

  it('detects Mermaid parser error SVG with style noise', () => {
    const result = isMermaidErrorSvg(`
      <svg>
        <style>.errorText { fill: red; }</style>
        <g>
          <text>Syntax error in text</text>
          <text>mermaid version 11.15.0</text>
        </g>
      </svg>
    `)

    expect(result).toBe(true)
  })
})
