import axios from 'axios'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getToken } from '@/utils/auth'

/** GET /api/classes/list 班级分页列表（ADMIN 全量 / TEACHER 仅负责的） */
export function getClassList(params) {
  return request.get('/api/classes/list', { params })
}

/** POST /api/classes 创建班级 */
export function createClass(data) {
  return request.post('/api/classes', data)
}

/** PUT /api/classes/{id} 更新班级 */
export function updateClass(id, data) {
  return request.put(`/api/classes/${id}`, data)
}

/** PUT /api/classes/{id}/status 启用/禁用班级 */
export function updateClassStatus(id, status) {
  return request.put(`/api/classes/${id}/status`, { status })
}

/** GET /api/classes/{id} 班级详情（含教师与计数） */
export function getClassDetail(id) {
  return request.get(`/api/classes/${id}`)
}

/** DELETE /api/classes/{id} 删除班级（逻辑删除） */
export function deleteClass(id) {
  return request.delete(`/api/classes/${id}`)
}

/** GET /api/classes/{id}/members 成员分页列表 */
export function getClassMembers(classId, params) {
  return request.get(`/api/classes/${classId}/members`, { params })
}

/** POST /api/classes/{id}/members 手动添加学员（已存在用户） */
export function addClassMember(classId, userId) {
  return request.post(`/api/classes/${classId}/members`, { userId })
}

/** POST /api/classes/{id}/members/import Excel 批量导入学员 */
export function importClassMembers(classId, file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`/api/classes/${classId}/members/import`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** GET /api/classes/members/import-template 下载学员导入模板 */
export async function downloadMemberTemplate() {
  const token = getToken()
  let response
  try {
    response = await axios.get('/api/classes/members/import-template', {
      baseURL: import.meta.env.VITE_API_BASE_URL || '',
      responseType: 'blob',
      timeout: 30000,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
  } catch (error) {
    const status = error.response?.status
    if (status === 401) ElMessage.error('登录已过期，请重新登录后下载模板')
    else if (status === 403) ElMessage.error('没有权限下载学员导入模板')
    else ElMessage.error('模板下载失败，请稍后重试')
    throw error
  }
  const blob = response.data

  if (!blob.size || blob.type?.includes('json') || blob.type?.startsWith('text/')) {
    let message = '模板下载失败'
    try {
      const payload = JSON.parse(await blob.text())
      message = payload.message || message
    } catch {}
    ElMessage.error(message)
    throw new Error(message)
  }

  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'student_template.xlsx'
  a.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** DELETE /api/classes/{id}/members/{userId} 移除学员 */
export function removeClassMember(classId, userId) {
  return request.delete(`/api/classes/${classId}/members/${userId}`)
}

/** POST /api/classes/{id}/teachers 分配教师（可批量） */
export function assignClassTeachers(classId, userIds) {
  return request.post(`/api/classes/${classId}/teachers`, { userIds })
}

/** DELETE /api/classes/{id}/teachers/{userId} 移除教师 */
export function removeClassTeacher(classId, userId) {
  return request.delete(`/api/classes/${classId}/teachers/${userId}`)
}

/** PUT /api/user/{id}/password/reset 重置密码为默认值 123456 */
export function resetUserPassword(userId) {
  return request.put(`/api/user/${userId}/password/reset`)
}

/** PUT /api/user/{id}/status 禁用/启用账号 */
export function updateUserStatus(userId, enabled) {
  return request.put(`/api/user/${userId}/status`, { enabled })
}
