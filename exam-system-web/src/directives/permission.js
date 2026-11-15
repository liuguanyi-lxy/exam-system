import { useUserStore } from '@/stores/user'

export default {
  mounted(el, binding) {
    const { value } = binding
    const userStore = useUserStore()
    if (value && !value.includes(userStore.role)) {
      el.parentNode?.removeChild(el)
    }
  },
}
