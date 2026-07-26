/**
 * 统一消息提示服务，替代各组件直接调用 ElMessage / ElMessageBox。
 *
 * <p>封装错误提取、去重、限流等横切关注点，所有业务组件通过此 composable
 * 发出提示，避免 51 处重复的 {@code err instanceof Error ? err.message : fallback} 样板。</p>
 */

import { ElMessage, ElMessageBox, type MessageParams } from 'element-plus'

/** 去重窗口：同一消息在此时长（ms）内不重复弹出。 */
const DEDUP_INTERVAL_MS = 3000

/** 最近弹出过的消息 key 及其时间戳，用于去重。 */
const recentMessages = new Map<string, number>()

/** 定期清理过期的去重记录，避免内存泄漏。 */
const CLEANUP_INTERVAL_MS = 60_000
let cleanupTimer: ReturnType<typeof setInterval> | null = null

function ensureCleanup() {
  if (cleanupTimer) return
  cleanupTimer = setInterval(() => {
    const now = Date.now()
    for (const [key, ts] of recentMessages) {
      if (now - ts > DEDUP_INTERVAL_MS) recentMessages.delete(key)
    }
    if (recentMessages.size === 0 && cleanupTimer) {
      clearInterval(cleanupTimer)
      cleanupTimer = null
    }
  }, CLEANUP_INTERVAL_MS)
}

/**
 * 判断消息是否应被去重（同一内容在窗口期内已弹出过）。
 */
function shouldDedup(type: string, content: string): boolean {
  const key = `${type}:${content}`
  const now = Date.now()
  const lastTs = recentMessages.get(key)
  if (lastTs !== undefined && now - lastTs < DEDUP_INTERVAL_MS) return true
  recentMessages.set(key, now)
  ensureCleanup()
  return false
}

/**
 * 从异常或任意值中提取错误信息。
 *
 * <p>优先取 Error.message；若传入的是非空字符串则直接使用；
 * 否则返回 fallback。与 useCrudStore 中的同名函数逻辑一致，
 * 此处重新导出以供组件层直接使用，无需再引入 Store 模块。</p>
 */
export function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof Error) return err.message
  if (typeof err === 'string' && err) return err
  return fallback
}

/** 确认对话框选项。 */
export interface ConfirmOptions {
  /** 确认按钮文本，默认"确定"。 */
  confirmButtonText?: string
  /** 取消按钮文本，默认"取消"。 */
  cancelButtonText?: string
  /** 对话框类型，默认 'warning'。 */
  type?: 'success' | 'warning' | 'info' | 'error'
}

/**
 * 统一消息提示 composable。
 *
 * <p>提供 success / error / warning / info / confirm 五个方法，
 * 组件不再直接 import ElMessage，所有提示经此统一出口。</p>
 *
 * @example
 * const notify = useNotify()
 * // 错误提示 — 自动提取 Error.message
 * catch (err) { notify.error(err, '创建角色失败') }
 * // 成功提示
 * notify.success('角色已创建')
 * // 确认对话框
 * const ok = await notify.confirm('确认删除？', '删除确认')
 */
export function useNotify() {
  function success(message: string) {
    if (shouldDedup('success', message)) return
    ElMessage.success(message)
  }

  /**
   * 错误提示。自动从异常中提取 message，无需手写 instanceof 判断。
   *
   * @param err 异常对象或任意值
   * @param fallback err 非 Error 时的兜底消息
   */
  function error(err: unknown, fallback: string) {
    const message = extractErrorMessage(err, fallback)
    if (shouldDedup('error', message)) return
    ElMessage.error(message)
  }

  function warning(message: string) {
    if (shouldDedup('warning', message)) return
    ElMessage.warning(message)
  }

  function info(message: string) {
    if (shouldDedup('info', message)) return
    ElMessage.info(message)
  }

  /**
   * 确认对话框，封装 ElMessageBox.confirm。
   *
   * @param message 确认提示内容
   * @param title 对话框标题，默认"确认"
   * @param options 选项
   * @returns 用户点击确认返回 true，取消返回 false
   */
  async function confirm(
    message: string,
    title = '确认',
    options: ConfirmOptions = {}
  ): Promise<boolean> {
    const { confirmButtonText, cancelButtonText, type = 'warning' } = options
    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText,
        cancelButtonText,
        type,
      })
      return true
    } catch {
      return false
    }
  }

  return { success, error, warning, info, confirm }
}
