import request from '@/utils/request'

/** POST /api/user/login */
export function login(data) {
  return request.post('/api/user/login', data)
}

/** POST /api/user/register */
export function register(data) {
  return request.post('/api/user/register', data)
}

/** GET /api/user/check-admin/{userId} */
export function checkAdmin(userId) {
  return request.get(`/api/user/check-admin/${userId}`)
}
