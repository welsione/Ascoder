import { describe, it, expect } from 'vitest'
import { flushEvents, dispatchEvent } from '../services/questionApi'
import type { StreamEvent } from '../services/questionApi'

describe('flushEvents', () => {
  function collectEvents(buf: string, final = false): { events: StreamEvent[]; remaining: string } {
    const events: StreamEvent[] = []
    const remaining = flushEvents(buf, (e) => events.push(e), final)
    return { events, remaining }
  }

  it('returns empty buffer when no complete event', () => {
    const { events, remaining } = collectEvents('event:status\ndata:{}')
    expect(events).toHaveLength(0)
    expect(remaining).toBe('event:status\ndata:{}')
  })

  it('dispatches a single complete event', () => {
    const { events } = collectEvents('event:status\ndata:{"status":"RUNNING"}\n\n')
    expect(events).toHaveLength(1)
    expect(events[0].type).toBe('status')
  })

  it('returns trailing partial after a complete event', () => {
    const { events, remaining } = collectEvents('event:status\ndata:{"status":"RUNNING"}\n\npartial')
    expect(events).toHaveLength(1)
    expect(remaining).toBe('partial')
  })

  it('handles multiple events in one buffer', () => {
    const buf = 'event:heartbeat\ndata:{"ts":1}\n\nevent:heartbeat\ndata:{"ts":2}\n\n'
    const { events } = collectEvents(buf)
    expect(events).toHaveLength(2)
    expect(events[0].type).toBe('heartbeat')
    expect(events[1].type).toBe('heartbeat')
  })

  it('flushes trailing content when final=true', () => {
    const { events, remaining } = collectEvents('event:status\ndata:{"status":"RUNNING"}', true)
    expect(events).toHaveLength(1)
    expect(remaining).toBe('')
  })

  it('ignores blank-only trailing when final=true', () => {
    const { events } = collectEvents('  \n  ', true)
    expect(events).toHaveLength(0)
  })
})

describe('dispatchEvent', () => {
  it('parses event name and JSON data', () => {
    const events: StreamEvent[] = []
    dispatchEvent('event:status\ndata:{"status":"RUNNING"}', (e) => events.push(e))
    expect(events).toHaveLength(1)
    expect(events[0].type).toBe('status')
    expect((events[0].data as { status: string }).status).toBe('RUNNING')
  })

  it('defaults event name to "message" when event: line is absent', () => {
    const events: StreamEvent[] = []
    dispatchEvent('data:{"status":"RUNNING"}', (e) => events.push(e))
    expect(events).toHaveLength(1)
    expect(events[0].type).toBe('message')
  })

  it('joins multi-line data fields', () => {
    const events: StreamEvent[] = []
    dispatchEvent('data:line1\ndata:line2', (e) => events.push(e))
    expect(events).toHaveLength(1)
    // data joined with newline, then fallback to raw string since it's not JSON
    expect((events[0].data as unknown as string)).toBe('line1\nline2')
  })

  it('ignores raw block with no data lines', () => {
    const events: StreamEvent[] = []
    dispatchEvent('event:status', (e) => events.push(e))
    expect(events).toHaveLength(0)
  })

  it('falls back to raw string for non-JSON data', () => {
    const events: StreamEvent[] = []
    dispatchEvent('event:reasoning\ndata:not-json', (e) => events.push(e))
    expect(events).toHaveLength(1)
    expect(events[0].type).toBe('reasoning')
    expect((events[0].data as unknown as string)).toBe('not-json')
  })
})
