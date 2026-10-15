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

// 回放按"年"进行: 选定某年后在 该年 1/1 → 12/31 区间内逐日回放
const DEFAULT_YEAR = 2022                 // 默认年份 (2022-09 有汛期暴雨/滑坡峰值)
const AVAILABLE_YEARS = [2020, 2021, 2022, 2023]
// 关键锚点: 2022-09 汛期暴雨/滑坡窗口 (仅 2022 年内有效)
const PEAK_DAY = '2022-09-06T00:00:00Z'
const PEAK_YEAR = 2022

export const useReplayStore = defineStore('replay', {
  state: () => ({
    /** 当前回放年份 */
    year: DEFAULT_YEAR,
    /** 可选年份列表 (供时间轴年份选择器) */
    availableYears: AVAILABLE_YEARS,

    /** ISO 字符串 */
    windowStart: null,
    windowEnd: null,
    description: '',

    /** Date 对象 */
    virtualNow: null,

    /** 倍速: 日尺度回放, 14400 = 6s 真实 = 1 天虚拟 (数据按天, 地图每 6s 推进一天; 放慢默认节奏避免通知刷屏) */
    speed: 14400,

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
    /** 当前虚拟日期 YYYY-MM-DD (供按日推送 /disaster-eval/push) */
    virtualDate(state) {
      return state.virtualNow ? state.virtualNow.toISOString().slice(0, 10) : null
    },
  },
  actions: {
    async init() {
      if (this.ready) return
      this.applyYear(this.year)
      this.ready = true
    },
    /** 内部: 依据当前 year 设定 [1/1, 12/31] 窗口并把虚拟时间归位到年初 */
    applyYear(year) {
      this.year = year
      this.windowStart = `${year}-01-01T00:00:00Z`
      this.windowEnd = `${year}-12-31T00:00:00Z`
      this.description = `${year} 年 四川逐日气象地质灾害回放`
      this.virtualNow = new Date(this.windowStart)
    },
    /** 切换回放年份: 暂停并回到该年年初 */
    setYear(year) {
      if (year === this.year) return
      this.pause()
      this.applyYear(year)
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
    /** 跳到汛期暴雨窗口 (2022-09-06); 若不在 2022 年先切到 2022 */
    jumpToPeak() {
      if (this.year !== PEAK_YEAR) this.applyYear(PEAK_YEAR)
      this.pause()
      this.jumpTo(PEAK_DAY)
    },
    reset() {
      this.pause()
      if (this.windowStart) this.virtualNow = new Date(this.windowStart)
    },
  },
})
