import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { useAsyncAction } from './useAsyncAction'

describe('useAsyncAction', () => {
  it('sets activeId during execution and clears after', async () => {
    const error = ref('')
    const { activeId, run } = useAsyncAction(error)
    let idDuringRun: number | null = null

    await run(42, async () => {
      idDuringRun = activeId.value
      return 'done'
    }, 'fallback')

    expect(idDuringRun).toBe(42)
    expect(activeId.value).toBeNull()
  })

  it('returns result and calls onSuccess on success', async () => {
    const error = ref('')
    const { run } = useAsyncAction(error)
    const onSuccess = vi.fn()

    const result = await run(1, async () => ({ id: 1, name: 'test' }), 'fallback', onSuccess)

    expect(result).toEqual({ id: 1, name: 'test' })
    expect(onSuccess).toHaveBeenCalledWith({ id: 1, name: 'test' })
    expect(error.value).toBe('')
  })

  it('extracts error message and returns null on failure', async () => {
    const error = ref('')
    const { run } = useAsyncAction(error)

    const result = await run(1, async () => {
      throw new Error('网络错误')
    }, '操作失败')

    expect(result).toBeNull()
    expect(error.value).toBe('网络错误')
  })

  it('uses fallback message for non-Error throws', async () => {
    const error = ref('')
    const { run } = useAsyncAction(error)

    const result = await run(1, async () => {
      throw 'string error'
    }, '操作失败')

    expect(result).toBeNull()
    expect(error.value).toBe('操作失败')
  })

  it('does not call onSuccess on failure', async () => {
    const error = ref('')
    const { run } = useAsyncAction(error)
    const onSuccess = vi.fn()

    await run(1, async () => {
      throw new Error('fail')
    }, 'fallback', onSuccess)

    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('clears error before execution', async () => {
    const error = ref('previous error')
    const { run } = useAsyncAction(error)

    await run(1, async () => 'ok', 'fallback')

    expect(error.value).toBe('')
  })

  it('supports async onSuccess', async () => {
    const error = ref('')
    const { run } = useAsyncAction(error)
    const onSuccess = vi.fn().mockResolvedValue(undefined)

    await run(1, async () => 'result', 'fallback', onSuccess)

    expect(onSuccess).toHaveBeenCalledWith('result')
  })
})
