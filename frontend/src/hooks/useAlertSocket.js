import { onMounted, onBeforeUnmount } from 'vue'
import { useWS } from '@/utils/ws'
import { useAlertStore } from '@/store/alert'
import { ElNotification } from 'element-plus'
import { levelMeta } from '@/utils/format'

export function useAlertSocket(onAlert) {
  const ws = useWS()
  const alertStore = useAlertStore()
  let unsubAlerts = null

  onMounted(async () => {
    try {
      await ws.connect()
      unsubAlerts = ws.subscribe('/topic/alerts', (alert) => {
        alertStore.pushAlert(alert)
        const meta = levelMeta(alert.level)
        ElNotification({
          title: `${meta.label}预警 - ${alert.title}`,
          message: alert.content || '',
          type: meta.tag === 'danger' ? 'error' : meta.tag,
          duration: 4000
        })
        if (typeof onAlert === 'function') onAlert(alert)
      })
    } catch (e) {
      // ignore: WS 不可用不阻塞
      console.warn('WebSocket connect failed:', e)
    }
  })

  onBeforeUnmount(() => {
    if (unsubAlerts) unsubAlerts()
  })
}
