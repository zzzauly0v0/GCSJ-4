/**
 * 时间轴回放 Pinia store
 *
 * 维护 "虚拟当前时间" virtualNow 在 [windowStart, windowEnd] 区间内行进。
 * 默认每 1s 推进 N 倍速秒数; 暂停时停滞。
 *
 * 任意大屏组件用法:
 *   const replay = useReplayStore()
 *   replay.start()
 *   watch(() => replay.virtualNow, (t) => loadByTime(t))
 */
import { defineStore } from 'pinia'
import { apiReplayWindow } from '@/api/replay'

export const useReplayStore = defineStore('replay', {
  state: () => ({
    /** ISO 字符串 */
    windowStart: null,
    windowEnd: null,
    description: '',

    /** Date 对象 */
    virtualNow: null,

    /** 倍速: 1 = 实时, 60 = 1s 真实 = 1min 虚拟, 1800 = 1s = 30min */
    speed: 600,

    /** 状态 */
    playing: false,

    /** 内部 setInterval 句柄 */
    _timer: null,

    /** 准备就绪 (元信息加载完) */
    ready: false,
  }),
  getters: {
    /** 0..1 当前进度 */
    progress(state) {
      if (!state.windowStart || !state.virtualNow) return 0
      const a = new Date(state.windowStart).getTime()
      const b = new Date(state.windowEnd).getTime()
      const t = state.virtualNow.getTime()
      return Math.max(0, Math.min(1, (t - a) / (b - a)))
    },
    /** 给后端的 ISO 串 (UTC) */
    virtualNowIso(state) {
      return state.virtualNow ? state.virtualNow.toISOString() : null
    },
  },
  actions: {
    async init() {
      if (this.ready) return
      const w = await apiReplayWindow()
      this.windowStart = w.startAt
      this.windowEnd = w.endAt
      this.description = w.description || ''
      this.virtualNow = new Date(w.startAt)
      this.ready = true
    },
    start() {
      if (!this.ready || this.playing) return
      this.playing = true
      this._timer = setInterval(() => this.tick(1), 1000)
    },
    pause() {
      this.playing = false
      if (this._timer) {
        clearInterval(this._timer)
        this._timer = null
      }
    },
    toggle() {
      this.playing ? this.pause() : this.start()
    },
    tick(realSeconds = 1) {
      if (!this.virtualNow) return
      const next = new Date(this.virtualNow.getTime() + realSeconds * this.speed * 1000)
      const end = new Date(this.windowEnd).getTime()
      if (next.getTime() >= end) {
        this.virtualNow = new Date(end)
        this.pause()
        return
      }
      this.virtualNow = next
    },
    setSpeed(x) {
      this.speed = x
    },
    seek(progress01) {
      if (!this.windowStart) return
      const a = new Date(this.windowStart).getTime()
      const b = new Date(this.windowEnd).getTime()
      const t = a + (b - a) * Math.max(0, Math.min(1, progress01))
      this.virtualNow = new Date(t)
    },
    jumpTo(isoOrDate) {
      this.virtualNow = isoOrDate instanceof Date ? isoOrDate : new Date(isoOrDate)
    },
    /** 跳到主震时刻 (2022-09-05 04:52 UTC = 北京 12:52) */
    jumpToQuake() {
      this.jumpTo('2022-09-05T04:52:00Z')
    },
    reset() {
      this.pause()
      if (this.windowStart) this.virtualNow = new Date(this.windowStart)
    },
  },
})
