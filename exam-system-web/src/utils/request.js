import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
})

// 错误提示去重：相同文案在 2 秒窗口内只弹一次，避免并发请求失败时 toast 刷屏盖住导航
const DEDUP_WINDOW_MS = 2000
const recentErrors = new Map()
function showErrorOnce(message) {
  const now = Date.now()
  if (now - (recentErrors.get(message) || 0) < DEDUP_WINDOW_MS) {
    return
  }
  recentErrors.set(message, now)
  ElMessage.error(message)
}

// 从 axios 错误对象提取用户可读的错误文案（优先后端返回的 message）
function resolveErrorMessage(error) {
  if (error.response) {
    const status = error.response.status
    const backendMessage = error.response.data?.message
    if (backendMessage) {
      return backendMessage
    }
    if (status === 500) {
      return '服务器开小差了，请稍后重试'
    }
    if (status === 403) {
      return '没有权限执行该操作'
    }
    if (status === 404) {
      return '请求的资源不存在'
    }
    return `请求失败（${status}）`
  }
  if (error.message?.includes('timeout')) {
    return '请求超时，请稍后重试'
  }
  return '网络异常，请检查网络连接'
}

// 请求拦截器：自动附加 token
service.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器：统一解包 Result 和错误处理
service.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    }
    if (code === 401) {
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/login'
      return Promise.reject(new Error(message || '未登录'))
    }
    showErrorOnce(message || '请求失败')
    return Promise.reject(new Error(message || '请求失败'))
  },
  (error) => {
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/login'
    } else {
      showErrorOnce(resolveErrorMessage(error))
    }
    return Promise.reject(error)
  },
)

export default service
