import request from '@/utils/request'

/** GET /api/questions/list */
export function getQuestionList(params) {
  return request.get('/api/questions/list', { params })
}

/** GET /api/questions/{id} */
export function getQuestionById(id) {
  return request.get(`/api/questions/${id}`)
}

/** POST /api/questions */
export function createQuestion(data) {
  return request.post('/api/questions', data)
}

/** PUT /api/questions/{id} */
export function updateQuestion(id, data) {
  return request.put(`/api/questions/${id}`, data)
}

/** DELETE /api/questions/{id} */
export function deleteQuestion(id) {
  return request.delete(`/api/questions/${id}`)
}

/** GET /api/questions/random */
export function getRandomQuestions(count, categoryId, difficulty) {
  return request.get('/api/questions/random', { params: { count, categoryId, difficulty } })
}

/** GET /api/questions/popular */
export function getPopularQuestions(size) {
  return request.get('/api/questions/popular', { params: { size } })
}

/** POST /api/questions/popular/refresh */
export function refreshPopularQuestions() {
  return request.post('/api/questions/popular/refresh')
}
