import request from '@/utils/request'

/** GET /api/videos */
export function getVideos(params) {
  return request.get('/api/videos', { params })
}

/** GET /api/videos/{id} */
export function getVideoById(id) {
  return request.get(`/api/videos/${id}`)
}

/** GET /api/videos/popular */
export function getPopularVideos(limit) {
  return request.get('/api/videos/popular', { params: { limit } })
}

/** GET /api/videos/latest */
export function getLatestVideos(limit) {
  return request.get('/api/videos/latest', { params: { limit } })
}

/** POST /api/videos/{videoId}/view — 会话化观看上报：首次上报计一次观看，同会话心跳累加时长 */
export function recordVideoView(videoId, sessionId, viewDuration) {
  return request.post(`/api/videos/${videoId}/view`, null, { params: { sessionId, viewDuration } })
}

/** POST /api/videos/{videoId}/like */
export function toggleVideoLike(videoId) {
  return request.post(`/api/videos/${videoId}/like`)
}

/** POST /api/videos/submit */
export function submitVideo(data) {
  return request.post('/api/videos/submit', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
