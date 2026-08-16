import request from '@/utils/request'

/** GET /api/categories */
export function getCategories() {
  return request.get('/api/categories')
}

/** GET /api/categories/tree */
export function getCategoryTree() {
  return request.get('/api/categories/tree')
}

/** POST /api/categories */
export function addCategory(data) {
  return request.post('/api/categories', data)
}

/** PUT /api/categories */
export function updateCategory(data) {
  return request.put('/api/categories', data)
}

/** DELETE /api/categories/{id} */
export function deleteCategory(id) {
  return request.delete(`/api/categories/${id}`)
}
