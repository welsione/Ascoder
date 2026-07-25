import { ref, type Ref } from 'vue'
import { extractErrorMessage } from './useCrudStore'

/**
 * 单 ID 跟踪的异步操作辅助函数，封装「设置 loading ID → 清空错误 →
 * 调用 API → 成功时 upsert 到列表 → 失败时提取错误 → finally 清除 loading」
 * 的重复样板。
 *
 * <p>各业务 Store 中 prepare/refresh/pullRemote/index 等动作共享此模式，
 * 区别仅在于 API 调用和成功后的副作用。此 composable 将副作用通过回调注入，
 * 避免每个动作重复 8 行 try/catch/finally 样板。</p>
 *
 * @param error 错误信息 ref，与 Store 的 crud.error 共享
 */
export function useAsyncAction(error: Ref<string>) {
  const activeId = ref<number | null>(null)

  /**
   * 执行异步操作。
   *
   * @param id 当前操作的业务 ID，用于跟踪 loading 状态
   * @param apiFn API 调用函数，返回操作结果
   * @param fallback 错误兜底消息
   * @param onSuccess 成功回调（可选），接收 API 结果执行 upsert 等副作用
   * @returns 成功返回 API 结果，失败返回 null
   */
  async function run<T>(
    id: number,
    apiFn: () => Promise<T>,
    fallback: string,
    onSuccess?: (result: T) => void | Promise<void>
  ): Promise<T | null> {
    activeId.value = id
    error.value = ''
    try {
      const result = await apiFn()
      if (onSuccess) await onSuccess(result)
      return result
    } catch (err) {
      error.value = extractErrorMessage(err, fallback)
      return null
    } finally {
      activeId.value = null
    }
  }

  return { activeId, run }
}
