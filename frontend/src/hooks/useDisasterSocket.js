import { onMounted, onBeforeUnmount } from 'vue'
import { useWS } from '@/utils/ws'
import { ElNotification } from 'element-plus'
import { levelMeta } from '@/utils/format'

/**
 * 订阅 /topic/disasters — 灾害判别结果按日推送。
 *
 * 后端 POST /api/disaster-eval/push?date= 广播的 payload 形如:
 *   { date, count, events: [{ station_code, station_name, lon, lat, comp_level, ... }] }
 *
 * 用法 (时间轴回放到某日时驱动):
 *   const { pushDay } = useDisasterSocket((payload) => renderOnMap(payload.events))
 *   watch(() => replay.virtualNow, (t) => pushDay(t.toISOString().slice(0, 10)))
 */
export function useDisasterSocket(onDisasters) {
  const ws = useWS()
  let unsub = null

  onMounted(async () => {
    try {
      await ws.connect()
      unsub = ws.subscribe('/topic/disasters', (payload) => {
        const events = payload?.events || []
        // 只在有橙/红 (comp_level>=3) 高风险时弹通知, 避免日尺度回放每天蓝/黄级也刷屏
        const top = events[0]
        if (top && (top.comp_level || 0) >= 3) {
          const meta = levelMeta(top.comp_level)
          ElNotification({
            title: `${payload.date} 高风险预警 (${events.length} 站)`,
            message: `${top.station_name || top.station_code} 等站达${meta.label}级综合风险`,
            type: meta.tag === 'danger' ? 'error' : meta.tag,
            duration: 4000
          })
        }
        if (typeof onDisasters === 'function') onDisasters(payload)
      })
    } catch (e) {
      // WS 不可用不阻塞
      console.warn('WebSocket connect failed:', e)
    }
  })

  onBeforeUnmount(() => {
    if (unsub) unsub()
  })
}
