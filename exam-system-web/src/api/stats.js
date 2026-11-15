import request from '@/utils/request'

/** GET /api/stats/overview */
export function getStatsOverview() {
  return request.get('/api/stats/overview')
}

/** GET /api/stats/test */
export function testDbConnection() {
  return request.get('/api/stats/test')
}
