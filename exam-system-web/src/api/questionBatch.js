import request from '@/utils/request'

/** GET /api/questions/batch/template */
export function downloadTemplate() {
  return request.get('/api/questions/batch/template', { responseType: 'blob' }).then((blob) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'question_template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  })
}

/** POST /api/questions/batch/preview-excel */
export function previewExcel(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/api/questions/batch/preview-excel', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** POST /api/questions/batch/import-excel */
export function importExcel(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/api/questions/batch/import-excel', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** POST /api/questions/batch/ai-generate */
export function aiGenerateQuestions(data) {
  return request.post('/api/questions/batch/ai-generate', data, { timeout: 120000 })
}

/** POST /api/questions/batch/import-questions */
export function batchImportQuestions(data) {
  return request.post('/api/questions/batch/import-questions', data)
}

/** POST /api/questions/batch/validate */
export function validateQuestions(data) {
  return request.post('/api/questions/batch/validate', data)
}
