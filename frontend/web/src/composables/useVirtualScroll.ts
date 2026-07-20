import { ref, computed, watch, onUnmounted, type Ref } from 'vue'

export interface VirtualScrollItem {
  /** 预估高度（px） */
  estimatedHeight: number
}

export interface VirtualScrollOptions {
  /** 滚动容器引用 */
  containerRef: Ref<HTMLElement | null>
  /** 数据项数组（响应式） */
  items: Ref<VirtualScrollItem[]>
  /** 视口上下缓冲区数量 */
  buffer?: number
  /** 默认预估项高度 */
  defaultItemHeight?: number
}

/**
 * 虚拟滚动 composable，基于预估高度实现窗口化渲染，
 * 适用于不等高的聊天消息列表场景。
 */
export function useVirtualScroll(options: VirtualScrollOptions) {
  const { containerRef, items, buffer = 5, defaultItemHeight = 200 } = options

  const scrollTop = ref(0)
  const viewportHeight = ref(0)
  const rafId = ref(0)

  /** 每项的累计偏移量，长度为 items.length + 1 */
  const offsets = computed(() => {
    const result: number[] = [0]
    for (let i = 0; i < items.value.length; i++) {
      const h = items.value[i].estimatedHeight || defaultItemHeight
      result.push(result[i] + h)
    }
    return result
  })

  const totalHeight = computed(() => offsets.value[items.value.length] || 0)

  /** 二分查找 scrollTop 对应的起始索引 */
  function findStartIndex(scroll: number): number {
    let lo = 0
    let hi = items.value.length - 1
    while (lo <= hi) {
      const mid = (lo + hi) >>> 1
      if (offsets.value[mid + 1] <= scroll) {
        lo = mid + 1
      } else {
        hi = mid - 1
      }
    }
    return Math.max(0, lo)
  }

  const visibleRange = computed(() => {
    const count = items.value.length
    if (count === 0) return { start: 0, end: 0 }

    const start = Math.max(0, findStartIndex(scrollTop.value) - buffer)
    const bottom = scrollTop.value + viewportHeight.value
    let end = start
    while (end < count && offsets.value[end] < bottom) {
      end++
    }
    end = Math.min(count, end + buffer)

    return { start, end }
  })

  function isItemVisible(index: number): boolean {
    const { start, end } = visibleRange.value
    return index >= start && index < end
  }

  /** 获取指定索引的 Y 偏移量 */
  function getItemOffset(index: number): number {
    return offsets.value[index] || 0
  }

  function onScroll() {
    cancelAnimationFrame(rafId.value)
    rafId.value = requestAnimationFrame(() => {
      const el = containerRef.value
      if (el) {
        scrollTop.value = el.scrollTop
        viewportHeight.value = el.clientHeight
      }
    })
  }

  function measure() {
    const el = containerRef.value
    if (el) {
      scrollTop.value = el.scrollTop
      viewportHeight.value = el.clientHeight
    }
  }

  watch(containerRef, (el, _, onCleanup) => {
    if (el) {
      measure()
      el.addEventListener('scroll', onScroll, { passive: true })
      onCleanup(() => el.removeEventListener('scroll', onScroll))
    }
  }, { immediate: true })

  /** 项数变化时重新测量视口 */
  watch(() => items.value.length, () => measure())

  onUnmounted(() => cancelAnimationFrame(rafId.value))

  return {
    visibleRange,
    totalHeight,
    isItemVisible,
    getItemOffset,
    measure,
  }
}
