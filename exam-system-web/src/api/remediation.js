import request from '@/utils/request'

/** GET /api/remediation/tasks — 我的补课任务列表（status: IN_PROGRESS/COMPLETED，可不传） */
export function getRemediationTasks(status) {
  const params = {}
  if (status) params.status = status
  return request.get('/api/remediation/tasks', { params })
}

/** GET /api/remediation/tasks/{id} — 任务详情（四步状态 + 策略快照 + 推荐视频） */
export function getRemediationTask(id) {
  return request.get(`/api/remediation/tasks/${id}`)
}

/** POST /api/remediation/tasks/{id}/video/complete — 幂等标记视频步骤完成 */
export function completeRemediationVideo(id) {
  return request.post(`/api/remediation/tasks/${id}/video/complete`)
}

// 练习/变式/复测三步共用的路径段
const STEP_PATHS = { practice: 'practice', variation: 'variation', retest: 'retest' }

/** GET /api/remediation/tasks/{id}/{step} — 取题（脱敏不下发答案解析） */
export function getRemediationPaper(id, step) {
  return request.get(`/api/remediation/tasks/${id}/${STEP_PATHS[step]}`)
}

/** POST /api/remediation/tasks/{id}/{step} — 批量提交作答 */
export function submitRemediationStep(id, step, answers) {
  return request.post(`/api/remediation/tasks/${id}/${STEP_PATHS[step]}`, { answers })
}
