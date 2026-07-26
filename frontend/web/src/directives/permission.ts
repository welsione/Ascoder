import type { Directive } from 'vue'
import { useAuthStore } from '../stores/auth'

/**
 * 权限指令：v-permission="'USER:MANAGE'" 或 v-permission="['USER:MANAGE', 'ROLE:MANAGE']"
 * 无权限时隐藏元素（display: none），支持响应式更新。
 */
export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    updateVisibility(el, binding)
  },
  updated(el, binding) {
    updateVisibility(el, binding)
  },
}

function updateVisibility(el: HTMLElement, binding: { value: string | string[] }) {
  const auth = useAuthStore()
  const value = binding.value
  const codes = Array.isArray(value) ? value : [value]
  const hasPermission = codes.some((code) => auth.hasPermission(code))
  el.style.display = hasPermission ? '' : 'none'
}
