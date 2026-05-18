import { defineStore } from 'pinia'
import { apiAlertLatest } from '@/api/alert'

export const useAlertStore = defineStore('alert', {
  state: () => ({
    latest: [],
    unreadCount: 0
  }),
  actions: {
    async fetchLatest(limit = 10) {
      this.latest = await apiAlertLatest({ limit })
      return this.latest
    },
    pushAlert(alert) {
      this.latest.unshift(alert)
      if (this.latest.length > 50) this.latest.pop()
      this.unreadCount++
    },
    markAllRead() {
      this.unreadCount = 0
    }
  }
})
