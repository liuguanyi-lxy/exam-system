import request from '@/utils/request'

/** GET /api/my/classes 我的班级列表（STUDENT） */
export function getMyClasses() {
  return request.get('/api/my/classes')
}

/** GET /api/my/classes/{classId}/classmates 我的同学列表（STUDENT-own） */
export function getMyClassmates(classId) {
  return request.get(`/api/my/classes/${classId}/classmates`)
}
