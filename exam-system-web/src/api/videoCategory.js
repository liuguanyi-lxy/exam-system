import request from '@/utils/request'

/** GET /api/video-categories */
export function getVideoCategories() {
  return request.get('/api/video-categories')
}

/** GET /api/video-categories/tree */
export function getVideoCategoryTree() {
  return request.get('/api/video-categories/tree')
}

/** GET /api/video-categories/top */
export function getTopVideoCategories() {
  return request.get('/api/video-categories/top')
}

/** GET /api/video-categories/children/{parentId} */
export function getVideoCategoryChildren(parentId) {
  return request.get(`/api/video-categories/children/${parentId}`)
}

/** GET /api/video-categories/{id} */
export function getVideoCategoryById(id) {
  return request.get(`/api/video-categories/${id}`)
}

/** POST /api/video-categories */
export function addVideoCategory(data) {
  return request.post('/api/video-categories', data)
}

/** PUT /api/video-categories */
export function updateVideoCategory(data) {
  return request.put('/api/video-categories', data)
}

/** DELETE /api/video-categories/{id} */
export function deleteVideoCategory(id) {
  return request.delete(`/api/video-categories/${id}`)
}
