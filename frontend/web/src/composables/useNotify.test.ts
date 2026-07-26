/**
 * useNotify 单元测试。
 *
 * <p>验证错误提取、去重、确认对话框返回值等关键行为。</p>
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// 必须在 import useNotify 之前 mock element-plus
const ElMessageMock = {
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
}
const ElMessageBoxMock = {
  confirm: vi.fn(),
}
vi.mock('element-plus', () => ({
  ElMessage: ElMessageMock,
  ElMessageBox: ElMessageBoxMock,
}))

const { useNotify, extractErrorMessage } = await import('./useNotify')

describe('extractErrorMessage', () => {
  it('returns Error.message when err is Error', () => {
    expect(extractErrorMessage(new Error('boom'), 'fallback')).toBe('boom')
  })

  it('returns string directly when err is non-empty string', () => {
    expect(extractErrorMessage('bad request', 'fallback')).toBe('bad request')
  })

  it('returns fallback for empty string', () => {
    expect(extractErrorMessage('', 'fallback')).toBe('fallback')
  })

  it('returns fallback for undefined / null / other values', () => {
    expect(extractErrorMessage(undefined, 'fb')).toBe('fb')
    expect(extractErrorMessage(null, 'fb')).toBe('fb')
    expect(extractErrorMessage(42, 'fb')).toBe('fb')
  })
})

describe('useNotify', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    ElMessageMock.success.mockClear()
    ElMessageMock.error.mockClear()
    ElMessageMock.warning.mockClear()
    ElMessageMock.info.mockClear()
    ElMessageBoxMock.confirm.mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('success / warning / info delegate to ElMessage', () => {
    const notify = useNotify()
    notify.success('ok')
    notify.warning('warn')
    notify.info('info')
    expect(ElMessageMock.success).toHaveBeenCalledWith('ok')
    expect(ElMessageMock.warning).toHaveBeenCalledWith('warn')
    expect(ElMessageMock.info).toHaveBeenCalledWith('info')
  })

  it('error extracts message from Error and falls back when not Error', () => {
    const notify = useNotify()
    notify.error(new Error('boom'), 'fallback')
    notify.error('raw string', 'fallback')
    expect(ElMessageMock.error).toHaveBeenNthCalledWith(1, 'boom')
    expect(ElMessageMock.error).toHaveBeenNthCalledWith(2, 'raw string')
  })

  it('error falls back to fallback message when err is null / undefined', () => {
    const notify = useNotify()
    notify.error(null, '默认失败')
    notify.error(undefined, '另一种失败')
    expect(ElMessageMock.error).toHaveBeenNthCalledWith(1, '默认失败')
    expect(ElMessageMock.error).toHaveBeenNthCalledWith(2, '另一种失败')
  })

  it('dedups same content within 3s window', () => {
    const notify = useNotify()
    notify.success('已保存')
    notify.success('已保存')
    notify.success('已保存')
    expect(ElMessageMock.success).toHaveBeenCalledTimes(1)
  })

  it('dedup is per-type — same content in success vs warning both fire', () => {
    const notify = useNotify()
    notify.success('xxx')
    notify.warning('xxx')
    expect(ElMessageMock.success).toHaveBeenCalledTimes(1)
    expect(ElMessageMock.warning).toHaveBeenCalledTimes(1)
  })

  it('after dedup window expires, same message can fire again', () => {
    const notify = useNotify()
    notify.warning('hint')
    expect(ElMessageMock.warning).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(3100)
    notify.warning('hint')
    expect(ElMessageMock.warning).toHaveBeenCalledTimes(2)
  })

  it('confirm returns true when user confirms', async () => {
    ElMessageBoxMock.confirm.mockResolvedValueOnce(undefined)
    const notify = useNotify()
    const result = await notify.confirm('继续？', '确认')
    expect(result).toBe(true)
    expect(ElMessageBoxMock.confirm).toHaveBeenCalledWith('继续？', '确认', {
      confirmButtonText: undefined,
      cancelButtonText: undefined,
      type: 'warning',
    })
  })

  it('confirm returns false when user cancels', async () => {
    ElMessageBoxMock.confirm.mockRejectedValueOnce(new Error('cancel'))
    const notify = useNotify()
    const result = await notify.confirm('继续？', '确认')
    expect(result).toBe(false)
  })

  it('confirm forwards custom options', async () => {
    ElMessageBoxMock.confirm.mockResolvedValueOnce(undefined)
    const notify = useNotify()
    await notify.confirm('msg', 'title', {
      confirmButtonText: '是',
      cancelButtonText: '否',
      type: 'error',
    })
    expect(ElMessageBoxMock.confirm).toHaveBeenCalledWith('msg', 'title', {
      confirmButtonText: '是',
      cancelButtonText: '否',
      type: 'error',
    })
  })
})