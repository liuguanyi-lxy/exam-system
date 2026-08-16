import request from '@/utils/request'

/** GET /api/notices/active */
export function getActiveNotices() {
  return request.get('/api/notices/active')
}

/** GET /api/notices/latest */
export function getLatestNotices(limit) {
  return request.get('/api/notices/latest', { params: { limit } })
}

/** GET /api/notices/list */
export function getNotices() {
  return request.get('/api/notices/list')
}

/** GET /api/notices/{id} */
export function getNoticeById(id) {
  return request.get(`/api/notices/${id}`)
}

/** POST /api/notices/add */
export function addNotice(data) {
  return request.post('/api/notices/add', data)
}

/** PUT /api/notices/update */
export function updateNotice(data) {
  return request.put('/api/notices/update', data)
}

/** DELETE /api/notices/delete/{id} */
export function deleteNotice(id) {
  return request.delete(`/api/notices/delete/${id}`)
}

/** PUT /api/notices/toggle/{id} */
export function toggleNotice(id, isActive) {
  return request.put(`/api/notices/toggle/${id}`, null, { params: { isActive } })
}
