// 页面里展示时间统一走这里：后端返回 ISO（2026-10-29T02:04:27），直接显示太长
export function fmtTime(value) {
  if (!value) return '—'
  const d = value instanceof Date ? value : new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(value).replace('T', ' ')
  const p = (n) => String(n).padStart(2, '0')
  const sameYear = d.getFullYear() === new Date().getFullYear()
  const date = (sameYear ? '' : d.getFullYear() + '-') + p(d.getMonth() + 1) + '-' + p(d.getDate())
  return date + ' ' + p(d.getHours()) + ':' + p(d.getMinutes())
}

export function fmtMoney(value) {
  const n = Number(value ?? 0)
  return n.toFixed(2)
}
