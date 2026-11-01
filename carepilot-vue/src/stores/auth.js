import { defineStore } from 'pinia'
import { authApi } from '../api'
import { ACCESS_KEY, clearSession, saveSession } from '../utils/token'

function toUser(data) {
  return { userId: data.userId, username: data.username, nickname: data.nickname, role: data.role }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(ACCESS_KEY) || '',
    user: JSON.parse(localStorage.getItem('cp_user') || 'null'),
  }),
  getters: {
    isLogin: (s) => !!s.token,
    role: (s) => s.user?.role || '',
    isStaff: (s) => ['AGENT', 'ADMIN'].includes(s.user?.role),
    isAdmin: (s) => s.user?.role === 'ADMIN',
  },
  actions: {
    applySession(data) {
      this.token = data.token
      this.user = toUser(data)
      saveSession({ token: data.token, refreshToken: data.refreshToken, user: this.user })
      return data
    },
    async login(username, password) {
      return this.applySession(await authApi.login({ username, password }))
    },
    async register(payload) {
      return this.applySession(await authApi.register(payload))
    },
    logout() {
      this.token = ''
      this.user = null
      clearSession()
    },
  },
})