export function formatDateTime(date) {
  if (!date) return ''
  const d = new Date(date)
  const pad = (n) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function formatDate(date) {
  if (!date) return ''
  const d = new Date(date)
  const pad = (n) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function formatDuration(minutes) {
  if (!minutes && minutes !== 0) return ''
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h > 0 && m > 0) return `${h}小时${m}分钟`
  if (h > 0) return `${h}小时`
  return `${m}分钟`
}

export function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(1)} ${units[i]}`
}

/** 难度映射 */
export const difficultyMap = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
export const difficultyColors = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }

/** 题目类型映射 */
export const questionTypeMap = { CHOICE: '选择题', JUDGE: '判断题', TEXT: '简答题', CODE: '手写代码题' }

/** 试卷/考试状态映射 */
export const paperStatusMap = { DRAFT: '草稿', PUBLISHED: '已发布', STOPPED: '已停止' }
export const examStatusMap = { '进行中': 'warning', '已完成': 'info', '已批阅': 'success' }

/** 视频状态映射 */
export const videoStatusMap = { 0: '待审核', 1: '已发布', 2: '已拒绝', 3: '已下架' }
export const videoStatusColors = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }
