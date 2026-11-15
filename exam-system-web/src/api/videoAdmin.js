import request from '@/utils/request'

/** GET /api/admin/videos */
export function getAdminVideos(params) {
  return request.get('/api/admin/videos', { params })
}

/** POST /api/admin/videos/upload */
export function adminUploadVideo(data) {
  return request.post('/api/admin/videos/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** POST /api/admin/videos/{videoId}/audit */
export function auditVideo(videoId, status, reason) {
  return request.post(`/api/admin/videos/${videoId}/audit`, null, { params: { status, reason } })
}

/** POST /api/admin/videos/{videoId}/offline */
export function offlineVideo(videoId) {
  return request.post(`/api/admin/videos/${videoId}/offline`)
}

/** DELETE /api/admin/videos/{videoId} */
export function deleteVideo(videoId) {
  return request.delete(`/api/admin/videos/${videoId}`)
}

/** GET /api/admin/videos/statistics */
export function getVideoStatistics() {
  return request.get('/api/admin/videos/statistics')
}

/** GET /api/admin/videos/{videoId}/stats */
export function getVideoStats(videoId, days) {
  return request.get(`/api/admin/videos/${videoId}/stats`, { params: { days } })
}
