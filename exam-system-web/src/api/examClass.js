import request from '@/utils/request'
import { getClassList } from '@/api/classManage'

export function normalizeWindowTime(value) {
  if (value == null || value === '') return null
  const time = String(value).replace('T', ' ').slice(0, 19)
  return time.length === 16 ? `${time}:00` : time
}

export function parseExamTime(value) {
  const time = normalizeWindowTime(value)
  return time === null ? NaN : Date.parse(`${time.replace(' ', 'T')}+08:00`)
}

export async function getExamClassOptions() {
  const options = []
  const size = 100
  for (let page = 1; ; page++) {
    const result = await getClassList({ page, size })
    if (!Array.isArray(result?.records) || !Number.isSafeInteger(result.total) || result.total < 0) {
      throw new Error('班级列表响应格式不正确')
    }
    options.push(...result.records)
    if (!result.records.length || options.length >= result.total) return options
  }
}

export function formatExamClassOption(item) {
  const name = item.startDate ? `${item.name} · ${item.startDate}` : `${item.name} · #${item.id}`
  return item.status === 'DISABLED' ? `${name}（已停用）` : name
}

/** POST /api/exam-classes 创建分配（classIds 可多班，仅 PUBLISHED 试卷） */
export function createExamClass(data) {
  return request.post('/api/exam-classes', {
    ...data,
    startTime: normalizeWindowTime(data.startTime),
    endTime: normalizeWindowTime(data.endTime),
  })
}

/** PUT /api/exam-classes/{id} 修改时间窗口/重考次数（已有人开考时改次数后端拒绝） */
export function updateExamClass(id, { startTime, endTime, maxAttempts }) {
  return request.put(`/api/exam-classes/${id}`, {
    startTime: normalizeWindowTime(startTime),
    endTime: normalizeWindowTime(endTime),
    maxAttempts,
  })
}

/** DELETE /api/exam-classes/{id} 撤销分配（有进行中考试时后端拒绝，逻辑删除） */
export function deleteExamClass(id) {
  return request.delete(`/api/exam-classes/${id}`)
}

/** GET /api/exam-classes?examId=&classId=&page=&size= 分配列表（按试卷/按班级双视角） */
export function getExamClassList(params) {
  return request.get('/api/exam-classes', { params })
}

/** GET /api/exam-classes/{id} 分配详情（含班级名、试卷名、各状态人数） */
export function getExamClassDetail(id) {
  return request.get(`/api/exam-classes/${id}`)
}

/** GET /api/exam-classes/{id}/students 分配维度学员进度（姓名/尝试次数/最高分/最近状态） */
export function getExamClassStudents(id) {
  return request.get(`/api/exam-classes/${id}/students`)
}
