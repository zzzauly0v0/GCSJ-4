import { useUserStore } from '@/store/user'

export function hasPermission(code) {
  const userStore = useUserStore()
  if (!userStore.profile) return false
  if (userStore.profile.roleCodes?.includes('ROLE_ADMIN')) return true
  return userStore.profile.permissions?.includes(code) ?? false
}

/**
 * v-permission 指令
 * <el-button v-permission="'alert:create'">新增</el-button>
 */
export const permissionDirective = {
  mounted(el, binding) {
    if (!hasPermission(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  }
}
