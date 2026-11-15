import { defineStore } from 'pinia'
import { login as loginApi, checkAdmin as checkAdminApi } from '@/api/auth'
import { setToken, getToken, removeToken, setUser, getUser, removeUser } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userId: null,
    username: '',
    realName: '',
    role: '',
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.role === 'ADMIN' || state.role === 'TEACHER',
    isStudent: (state) => state.role === 'STUDENT',
  },

  actions: {
    async login(loginData) {
      const res = await loginApi(loginData)
      if (res) {
        this.token = res.token || ''
        this.userId = res.userId
        this.username = res.username
        this.realName = res.realName
        this.role = res.role
        setToken(res.token || '')
        setUser({
          userId: res.userId,
          username: res.username,
          realName: res.realName,
          role: res.role,
        })
      }
    },

    logout() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.realName = ''
      this.role = ''
      removeToken()
      removeUser()
    },

    loadFromStorage() {
      const user = getUser()
      if (user) {
        this.userId = user.userId
        this.username = user.username
        this.realName = user.realName
        this.role = user.role
      }
    },

    async checkAdmin() {
      if (!this.userId) return false
      try {
        const res = await checkAdminApi(this.userId)
        return res
      } catch {
        return false
      }
    },
  },
})
