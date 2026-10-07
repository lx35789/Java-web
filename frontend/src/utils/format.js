import dayjs from 'dayjs'
import { CONFIDENCE_LEVELS, DIAGNOSIS_CONFIDENCE } from './constants'

export function formatDate(v, fmt = 'YYYY-MM-DD') {
  return v ? dayjs(v).format(fmt) : '—'
}

export function formatDateTime(v) {
  return v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '—'
}

export function formatRelative(v) {
  if (!v) return '—'
  const d = dayjs(v)
  const diffDay = d.startOf('day').diff(dayjs().startOf('day'), 'day')
  if (diffDay === 0) return '今天'
  if (diffDay === 1) return '明天'
  if (diffDay === -1) return '昨天'
  if (diffDay > 1) return `${diffDay} 天后`
  return `${Math.abs(diffDay)} 天前`
}

/** 0.8734 -> 87.3% */
export function toPercent(v, digits = 1) {
  if (v === null || v === undefined) return '—'
  return `${(Number(v) * 100).toFixed(digits)}%`
}

/** 置信度 -> { label, type } 用于 el-tag */
export function confidenceLevel(v) {
  const n = Number(v) || 0
  return CONFIDENCE_LEVELS.find((i) => n >= i.min) || CONFIDENCE_LEVELS[CONFIDENCE_LEVELS.length - 1]
}

/** 判因置信度 -> { label, type }，区分"给结论 / 引导补充信息" */
export function diagnosisConfidence(v) {
  const n = Number(v) || 0
  if (n >= DIAGNOSIS_CONFIDENCE.HIGH.min) return DIAGNOSIS_CONFIDENCE.HIGH
  if (n >= DIAGNOSIS_CONFIDENCE.MEDIUM.min) return DIAGNOSIS_CONFIDENCE.MEDIUM
  return DIAGNOSIS_CONFIDENCE.LOW
}

/** 毫秒 -> 人类可读 */
export function formatLatency(ms) {
  if (!ms && ms !== 0) return '—'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

/** 生成唯一 key（列表渲染用） */
let _uid = 0
export function uid(prefix = 'id') {
  return `${prefix}_${Date.now().toString(36)}_${(_uid++).toString(36)}`
}

/** 深拷贝（仅用于 mock / 表单回填） */
export function clone(v) {
  return v === undefined ? v : JSON.parse(JSON.stringify(v))
}
