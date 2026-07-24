/**
 * useAppleMotion — Apple 风格弹簧动画封装
 *
 * 基于 motion-v 的 spring 动画，对应 Apple WWDC 的 damping + response 参数：
 *   bounce: 0   ↔ damping 1.0（临界阻尼，无回弹）
 *   bounce: 0.2 ↔ damping ~0.8（带回弹，用于有 velocity 的手势）
 *   duration: 0.35 ↔ response 0.35s
 *
 * 自动尊重 prefers-reduced-motion：开启时退化为 200ms opacity cross-fade。
 */

import { onMounted, type Ref } from 'vue'
import { animate } from 'motion-v'
import { usePreferredReducedMotion } from '@vueuse/core'

export interface AppleSpringOptions {
  /** 0 = 临界阻尼（默认），0.2 = 带回弹 */
  bounce?: number
  /** 秒；默认 0.35 */
  duration?: number
  /** 是否尊重 reduced-motion（默认 true） */
  respectReducedMotion?: boolean
}

export interface SpringAnimateOptions {
  /** 延迟（秒） */
  delay?: number
}

/**
 * 对单个元素应用 Apple 风格弹簧入场动画。
 * 在组件 onMounted 时自动执行。
 *
 * @param elRef - 模板 ref（HTMLElement）
 * @param keyframes - motion-v keyframes，如 { opacity: [0, 1], y: [10, 0] }
 * @param options - 弹簧参数
 * @param animateOptions - 延迟等
 */
export function useAppleSpring(
  elRef: Ref<HTMLElement | null>,
  keyframes: Record<string, unknown[]>,
  options?: AppleSpringOptions,
  animateOptions?: SpringAnimateOptions,
) {
  const prefersReducedMotion = usePreferredReducedMotion()
  const bounce = options?.bounce ?? 0
  const duration = options?.duration ?? 0.35
  const respectReduced = options?.respectReducedMotion !== false

  onMounted(() => {
    const el = elRef.value
    if (!el) return

    const isReduced = respectReduced && prefersReducedMotion.value === 'reduce'

    if (isReduced) {
      // Reduced motion：简短 opacity cross-fade
      animate(el, { opacity: [0, 1] }, { duration: 0.2, delay: animateOptions?.delay ?? 0 })
      return
    }

    animate(el, keyframes, {
      type: 'spring',
      bounce,
      duration,
      delay: animateOptions?.delay ?? 0,
    })
  })
}

/**
 * 手动对一个元素触发 Apple 风格弹簧动画（不绑定生命周期）。
 * 适用于用户交互触发的动画（如侧栏滑入、卡片按压回弹等）。
 */
export function appleSpringAnimate(
  el: HTMLElement,
  keyframes: Record<string, unknown[]>,
  options?: AppleSpringOptions & SpringAnimateOptions,
) {
  const prefersReducedMotion = usePreferredReducedMotion()
  const bounce = options?.bounce ?? 0
  const duration = options?.duration ?? 0.35
  const delay = options?.delay ?? 0

  if (prefersReducedMotion.value === 'reduce') {
    animate(el, { opacity: [0, 1] }, { duration: 0.2, delay })
    return
  }

  animate(el, keyframes, {
    type: 'spring',
    bounce,
    duration,
    delay,
  })
}
