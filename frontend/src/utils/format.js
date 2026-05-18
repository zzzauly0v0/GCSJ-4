import dayjs from 'dayjs'

export function formatDateTime(v, fmt = 'YYYY-MM-DD HH:mm:ss') {
  if (!v) return '-'
  return dayjs(v).format(fmt)
}

export function formatDate(v, fmt = 'YYYY-MM-DD') {
  if (!v) return '-'
  return dayjs(v).format(fmt)
}

/** 预警等级 → 颜色 / 文字 */
export const ALERT_LEVELS = {
  1: { label: '蓝色', color: '#3B82F6', tag: 'info' },
  2: { label: '黄色', color: '#FBBF24', tag: 'warning' },
  3: { label: '橙色', color: '#F97316', tag: 'warning' },
  4: { label: '红色', color: '#EF4444', tag: 'danger' }
}

export function levelMeta(level) {
  return ALERT_LEVELS[level] || { label: '未知', color: '#9CA3AF', tag: 'info' }
}
