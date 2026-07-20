import DOMPurify from 'dompurify'

const ALLOWED_TAGS = [
  'p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li', 'blockquote', 'pre', 'code',
  'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'a', 'strong', 'em', 'span', 'div', 'br', 'hr', 'img',
  'details', 'summary', 'section',
  // mermaid SVG output
  'svg', 'path', 'rect', 'circle', 'ellipse', 'line', 'polyline', 'polygon',
  'text', 'tspan', 'g', 'defs', 'clippath', 'marker', 'foreignobject',
]

const ALLOWED_ATTR = [
  'href', 'target', 'rel', 'class', 'id', 'src', 'alt',
  // SVG / mermaid
  'viewbox', 'preserveaspectratio', 'xmlns', 'd', 'fill', 'stroke',
  'stroke-width', 'stroke-dasharray', 'transform', 'x', 'y', 'width', 'height',
  'rx', 'ry', 'cx', 'cy', 'r', 'x1', 'y1', 'x2', 'y2', 'points',
  'font-size', 'font-family', 'font-weight', 'text-anchor', 'dominant-baseline',
  'marker-end', 'marker-start', 'marker-mid', 'id', 'clip-path',
  'markerwidth', 'markerheight', 'markerunits', 'refx', 'refy', 'orient',
  'style', 'role', 'aria-label', 'aria-hidden',
  // mermaid placeholder
  'data-mermaid-id', 'data-mermaid-source',
]

export function sanitizeHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ADD_ATTR: ['target', 'rel'],
    FORCE_BODY: true,
  })
}

/**
 * 清理 Mermaid 生成的 SVG。Mermaid 图依赖内联 <style> 定义节点和文字样式，
 * 普通 Markdown 仍不允许 style 标签，避免扩大用户输入的样式能力。
 */
export function sanitizeMermaidSvg(svg: string): string {
  return DOMPurify.sanitize(svg, {
    ALLOWED_TAGS: [...ALLOWED_TAGS, 'style'],
    ALLOWED_ATTR,
    HTML_INTEGRATION_POINTS: { foreignobject: true },
    FORCE_BODY: true,
  })
}
