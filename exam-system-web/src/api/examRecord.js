import request from '@/utils/request'

/** GET /api/exam-records/list */
export function getExamRecordList(params) {
  return request.get('/api/exam-records/list', { params })
}

/** GET /api/exam-records/{id} */
export function getExamRecordById(id) {
  return request.get(`/api/exam-records/${id}`)
}

/** DELETE /api/exam-records/{id} */
export function deleteExamRecord(id) {
  return request.delete(`/api/exam-records/${id}`)
}

/** GET /api/exam-records/ranking */
export function getExamRanking(params) {
  return request.get('/api/exam-records/ranking', { params })
}
