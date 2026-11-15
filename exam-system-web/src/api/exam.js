import request from '@/utils/request'

/** POST /api/exams/start */
export function startExam(data) {
  return request.post('/api/exams/start', data)
}

/** POST /api/exams/{examRecordId}/submit */
export function submitExam(examRecordId, answers) {
  return request.post(`/api/exams/${examRecordId}/submit`, answers)
}

/** POST /api/exams/{examRecordId}/grade */
export function gradeExam(examRecordId) {
  return request.post(`/api/exams/${examRecordId}/grade`, null, { timeout: 120000 })
}
