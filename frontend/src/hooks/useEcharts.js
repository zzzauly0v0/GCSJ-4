import { onMounted, onBeforeUnmount, ref } from 'vue'
import * as echarts from 'echarts'

export function useEcharts(target) {
  const instance = ref(null)
  let resizeFn = null

  onMounted(() => {
    const el = typeof target === 'string' ? document.querySelector(target) : target.value
    if (!el) return
    instance.value = echarts.init(el)
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
