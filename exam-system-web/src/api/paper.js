import request from '@/utils/request'

/** GET /api/papers/list */
export function getPaperList(params) {
  return request.get('/api/papers/list', { params })
}

/** POST /api/papers */
export function createPaper(data) {
  return request.post('/api/papers', data)
}

/** PUT /api/papers/{id} */
export function updatePaper(id, data) {
  return request.put(`/api/papers/${id}`, data)
}

/** POST /api/papers/ai */
export function aiCreatePaper(data) {
  return request.post('/api/papers/ai', data, { timeout: 120000 })
}

/** GET /api/papers/{id} */
export function getPaperById(id) {
  return request.get(`/api/papers/${id}`)
}

/** POST /api/papers/{id}/status */
export function updatePaperStatus(id, status) {
  return request.post(`/api/papers/${id}/status`, null, { params: { status } })
}

/** DELETE /api/papers/{id} */
export function deletePaper(id) {
  return request.delete(`/api/papers/${id}`)
}
