import { defineStore } from 'pinia'
import { apiLogin, apiProfile, apiLogout } from '@/api/auth'

const TOKEN_KEY = 'gcsj_token'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    profile: null
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    permissions: (s) => s.profile?.permissions || [],
    roleCodes: (s) => s.profile?.roleCodes || []
  },
  actions: {
    async login({ username, password }) {
      const data = await apiLogin({ username, password })
      this.token = data.token
      this.profile = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      return data
    },
    async fetchProfile() {
      const data = await apiProfile()
      this.profile = data
      return data
    },
    async logout() {
      try {
        await apiLogout()
      } catch (_) { /* ignore */ }
      this.token = ''
      this.profile = null
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})
