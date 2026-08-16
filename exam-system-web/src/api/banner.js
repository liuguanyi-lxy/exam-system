import request from '@/utils/request'

/** POST /api/banners/upload-image */
export function uploadBannerImage(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/api/banners/upload-image', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** GET /api/banners/active */
export function getActiveBanners() {
  return request.get('/api/banners/active')
}

/** GET /api/banners/list */
export function getBanners() {
  return request.get('/api/banners/list')
}

/** GET /api/banners/{id} */
export function getBannerById(id) {
  return request.get(`/api/banners/${id}`)
}

/** POST /api/banners/add */
export function addBanner(data) {
  return request.post('/api/banners/add', data)
}

/** PUT /api/banners/update */
export function updateBanner(data) {
  return request.put('/api/banners/update', data)
}

/** DELETE /api/banners/delete/{id} */
export function deleteBanner(id) {
  return request.delete(`/api/banners/delete/${id}`)
}

/** PUT /api/banners/toggle/{id} */
export function toggleBanner(id, isActive) {
  return request.put(`/api/banners/toggle/${id}`, null, { params: { isActive } })
}
