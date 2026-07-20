import { describe, expect, it } from 'vitest'
import { sanitizeMermaidSvg } from './sanitize'

describe('sanitizeMermaidSvg', () => {
  it('preserves Mermaid marker geometry attributes for arrowheads', () => {
    const result = sanitizeMermaidSvg(`
      <svg>
        <defs>
          <marker
            id="arrow"
            markerWidth="8"
            markerHeight="8"
            refX="5"
            refY="5"
            orient="auto"
            markerUnits="strokeWidth"
            viewBox="0 0 10 10"
          >
            <path d="M 0 0 L 10 5 L 0 10 z"></path>
          </marker>
        </defs>
        <path marker-end="url(#arrow)" d="M 0 0 L 10 10"></path>
      </svg>
    `)

    expect(result).toContain('markerWidth="8"')
    expect(result).toContain('markerHeight="8"')
    expect(result).toContain('refX="5"')
    expect(result).toContain('refY="5"')
    expect(result).toContain('orient="auto"')
    expect(result).toContain('markerUnits="strokeWidth"')
  })
})
