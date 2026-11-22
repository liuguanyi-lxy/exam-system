import request from '@/utils/request'

/**
 * GET /api/wrong-questions/list
 *
 * @param {Object} params 查询参数
 * @param {number} [params.page=1] 页码，从 1 开始
 * @param {number} [params.size=10] 每页条数
 * @param {string} [params.type] 题型筛选：CHOICE/JUDGE/TEXT/CODE
 * @param {string} [params.difficulty] 难度筛选：EASY/MEDIUM/HARD
 * @param {number} [params.categoryId] 分类 ID
 * @param {boolean} [params.masteredOnly=false] true 时仅返回"已掌握"的题（已掌握 Tab 服务端分页）
 * @param {boolean} [params.includeMastered=false] true 时同时返回已掌握的题，由记录 mastered 字段区分
 */
export function getWrongQuestionList(params) {
  return request.get('/api/wrong-questions/list', { params })
}

/** POST /api/wrong-questions/{questionId}/mastered */
export function markMastered(questionId) {
  return request.post(`/api/wrong-questions/${questionId}/mastered`)
}

/** DELETE /api/wrong-questions/{questionId}/mastered */
export function unmarkMastered(questionId) {
  return request.delete(`/api/wrong-questions/${questionId}/mastered`)
}
