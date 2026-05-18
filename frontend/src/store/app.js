import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: false,
    theme: 'light',
    title: import.meta.env.VITE_APP_TITLE || 'GCSJ'
  }),
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
    setTheme(t) {
      this.theme = t
      document.documentElement.classList.toggle('dark', t === 'dark')
    }
  }
})
