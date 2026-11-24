import request from '@/utils/request'

export function getDiagnosticReport(recordId) {
  return request.get(`/api/diagnostics/${recordId}`)
}

export function reviewDiagnosticReport(recordId, note) {
  return request.post(`/api/diagnostics/${recordId}/review`, { note })
}

export function retryDiagnosticReport(recordId) {
  return request.post(`/api/diagnostics/${recordId}/retry`)
}

export function getQuestionChats(recordId, questionId) {
  return request.get(`/api/diagnostics/${recordId}/questions/${questionId}/chats`)
}

export function askQuestionChat(recordId, questionId, data) {
  return request.post(`/api/diagnostics/${recordId}/questions/${questionId}/chats`, data)
}
