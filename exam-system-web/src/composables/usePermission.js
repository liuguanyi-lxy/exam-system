import { useUserStore } from '@/stores/user'

export function usePermission() {
  const userStore = useUserStore()
  return {
    isAdmin: userStore.role === 'ADMIN',
    isTeacher: userStore.role === 'TEACHER',
    isStudent: userStore.role === 'STUDENT',
    hasRole(roles) {
      return roles.includes(userStore.role)
    },
  }
}
