<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

onMounted(async () => {
  if (userStore.token && !userStore.profile) {
    try {
      await userStore.fetchProfile()
    } catch (_) {
      // ignore — guard will redirect to login
    }
  }
})
</script>
