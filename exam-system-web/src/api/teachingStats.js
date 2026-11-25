import request from '@/utils/request'

/** GET /api/teaching-stats?classId=&days= — 班级教学统计聚合（days 仅 7/30；TEACHER 限负责班级） */
export function getTeachingStats(classId, days = 7) {
  return request.get('/api/teaching-stats', { params: { classId, days } })
}

/** GET /api/teaching-stats/compare — 班级对比（教师 = 负责班级，管理员 = 全部） */
export function getTeachingStatsCompare() {
  return request.get('/api/teaching-stats/compare')
}
