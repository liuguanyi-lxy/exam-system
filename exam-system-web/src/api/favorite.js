import request from '@/utils/request'

/** POST /api/question-favorites */
export function addFavorite(questionId) {
  return request.post('/api/question-favorites', { questionId })
}

/** DELETE /api/question-favorites/{questionId} */
export function removeFavorite(questionId) {
  return request.delete(`/api/question-favorites/${questionId}`)
}

/** GET /api/question-favorites/list */
export function getFavoriteList(params) {
  return request.get('/api/question-favorites/list', { params })
}

/** GET /api/question-favorites/{questionId} */
export function getFavoriteStatus(questionId) {
  return request.get(`/api/question-favorites/${questionId}`)
}
