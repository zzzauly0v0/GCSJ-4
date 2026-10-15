/**
 * useEcharts — composable for ECharts instances
 * Automatically uses the 'stormwatch' dark theme.
 * Handles mount/unmount lifecycle and window resize.
 */
import { onMounted, onBeforeUnmount, ref } from 'vue'
import * as echarts from 'echarts'
import { registerStormTheme } from '@/utils/echartsTheme'

// Ensure theme is registered before any chart init
registerStormTheme()

export function useEcharts(target) {
  const instance = ref(null)
  let resizeFn = null

  onMounted(() => {
    const el = typeof target === 'string' ? document.querySelector(target) : target.value
    if (!el) return
    // Always init with 'stormwatch' theme — never use ECharts default
    instance.value = echarts.init(el, 'stormwatch')
    resizeFn = () => instance.value && instance.value.resize()
    window.addEventListener('resize', resizeFn)
  })

  onBeforeUnmount(() => {
    if (resizeFn) window.removeEventListener('resize', resizeFn)
    if (instance.value) {
      instance.value.dispose()
      instance.value = null
    }
  })

  function setOption(opt) {
    if (instance.value) instance.value.setOption(opt, true)
  }

  return { instance, setOption }
}
