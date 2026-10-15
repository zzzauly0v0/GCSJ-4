import dayjs from 'dayjs'

export function formatDateTime(v, fmt = 'YYYY-MM-DD HH:mm:ss') {
  if (!v) return '—'
  return dayjs(v).format(fmt)
}

export function formatDate(v, fmt = 'YYYY-MM-DD') {
  if (!v) return '—'
  return dayjs(v).format(fmt)
}

/**
 * Warning level metadata
 * stormColor: fluorescent variant matching the STORMWATCH design system
 * cssVar:     references the CSS variable token
 */
export const ALERT_LEVELS = {
  1: { label: '蓝色', color: '#3B8BF0',  stormColor: '#3B8BF0',  cssVar: 'var(--level-1-color)', tag: 'info' },
  2: { label: '黄色', color: '#FFD60A',  stormColor: '#FFD60A',  cssVar: 'var(--level-2-color)', tag: 'warning' },
  3: { label: '橙色', color: '#FF6A1A',  stormColor: '#FF6A1A',  cssVar: 'var(--level-3-color)', tag: 'warning' },
  4: { label: '红色', color: '#FF2D2D',  stormColor: '#FF2D2D',  cssVar: 'var(--level-4-color)', tag: 'danger' },
}

export function levelMeta(level) {
  return ALERT_LEVELS[level] || { label: '未知', color: '#3D5A73', stormColor: '#3D5A73', cssVar: 'var(--text-tertiary)', tag: 'info' }
}
