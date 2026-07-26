import type { Directive } from 'vue'
import { useAuthStore } from '../stores/auth'

/**
 * 权限指令：v-permission="'USER:MANAGE'" 或 v-permission="['USER:MANAGE', 'ROLE:MANAGE']"
 * 无权限时移除元素。
 */
export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const auth = useAuthStore()
    const value = binding.value
    const codes = Array.isArray(value) ? value : [value]
    const hasPermission = codes.some((code) => auth.hasPermission(code))
    if (!hasPermission) {
      el.parentNode?.removeChild(el)
    }
  },
}
