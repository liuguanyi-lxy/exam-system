import request from '@/utils/request'

/** GET /api/knowledge-points/list 知识点分页列表（管理页） */
export function getKnowledgePointList(params) {
  return request.get('/api/knowledge-points/list', { params })
}

/** GET /api/knowledge-points/all 知识点全量列表（选择器） */
export function getAllKnowledgePoints() {
  return request.get('/api/knowledge-points/all')
}

/** POST /api/knowledge-points 创建知识点 */
export function createKnowledgePoint(data) {
  return request.post('/api/knowledge-points', data)
}

/** PUT /api/knowledge-points/{id} 更新知识点 */
export function updateKnowledgePoint(id, data) {
  return request.put(`/api/knowledge-points/${id}`, data)
}

/** DELETE /api/knowledge-points/{id} 删除知识点（被引用时禁止） */
export function deleteKnowledgePoint(id) {
  return request.delete(`/api/knowledge-points/${id}`)
}

/** POST /api/knowledge-points/sort 批量排序 */
export function sortKnowledgePoints(items) {
  return request.post('/api/knowledge-points/sort', items)
}

/** PUT /api/questions/{id}/knowledge-points 给题目整体替换知识点集合 */
export function setQuestionKnowledgePoints(questionId, knowledgePointIds) {
  return request.put(`/api/questions/${questionId}/knowledge-points`, { knowledgePointIds })
}

/** PUT /api/knowledge-points/{id}/videos 整体替换知识点推荐视频（videoIds 去重保序，空数组=清空） */
export function setKnowledgePointVideos(pointId, videoIds) {
  return request.put(`/api/knowledge-points/${pointId}/videos`, { videoIds })
}
