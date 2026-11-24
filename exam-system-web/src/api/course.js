import request from '@/utils/request'

/** GET /api/courses/list — 管理视角课程分页列表 */
export function getCourseList(params) {
  return request.get('/api/courses/list', { params })
}

/** POST /api/courses — 创建课程（DRAFT） */
export function createCourse(data) {
  return request.post('/api/courses', data)
}

/** PUT /api/courses/{id} — 更新课程基本信息 */
export function updateCourse(id, data) {
  return request.put(`/api/courses/${id}`, data)
}

/** DELETE /api/courses/{id} — 删除课程 */
export function deleteCourse(id) {
  return request.delete(`/api/courses/${id}`)
}

/** PUT /api/courses/{id}/status — 切换草稿/发布状态 */
export function updateCourseStatus(id, status) {
  return request.put(`/api/courses/${id}/status`, { status })
}

/** GET /api/courses/{id}/classes — 查询课程班级分配 */
export function getCourseClasses(id) {
  return request.get(`/api/courses/${id}/classes`)
}

/** PUT /api/courses/{id}/classes — 整体替换课程班级分配 */
export function assignCourseClasses(id, classIds) {
  return request.put(`/api/courses/${id}/classes`, { classIds })
}

/** GET /api/courses/{courseId}/chapters — 管理视角章节列表 */
export function getCourseChapters(courseId) {
  return request.get(`/api/courses/${courseId}/chapters`)
}

/** POST /api/courses/{courseId}/chapters — 新增章节 */
export function createCourseChapter(courseId, data) {
  return request.post(`/api/courses/${courseId}/chapters`, data)
}

/** PUT /api/chapters/{id} — 更新章节 */
export function updateCourseChapter(id, data) {
  return request.put(`/api/chapters/${id}`, data)
}

/** DELETE /api/chapters/{id} — 删除章节 */
export function deleteCourseChapter(id) {
  return request.delete(`/api/chapters/${id}`)
}

/** PUT /api/chapters/{id}/move — 章节上下移动 */
export function moveCourseChapter(id, direction) {
  return request.put(`/api/chapters/${id}/move`, { direction })
}

/** GET /api/chapters/{chapterId}/videos — 管理视角章节视频列表 */
export function getChapterVideos(chapterId) {
  return request.get(`/api/chapters/${chapterId}/videos`)
}

/** POST /api/chapters/{chapterId}/videos — 添加已有视频或新建外链 */
export function addChapterVideo(chapterId, data) {
  return request.post(`/api/chapters/${chapterId}/videos`, data)
}

/** PUT /api/chapter-videos/{id} — 更新章节视频配置 */
export function updateChapterVideo(id, data) {
  return request.put(`/api/chapter-videos/${id}`, data)
}

/** DELETE /api/chapter-videos/{id} — 从章节移除视频 */
export function deleteChapterVideo(id) {
  return request.delete(`/api/chapter-videos/${id}`)
}

/** PUT /api/chapter-videos/{id}/move — 章节视频上下移动 */
export function moveChapterVideo(id, direction) {
  return request.put(`/api/chapter-videos/${id}/move`, { direction })
}

/** GET /api/my/courses — 学员我的课程列表 */
export function getMyCourses() {
  return request.get('/api/my/courses')
}

/** GET /api/my/courses/{courseId} — 课程目录及章节完成状态 */
export function getMyCourse(courseId) {
  return request.get(`/api/my/courses/${courseId}`)
}

/** GET /api/my/courses/{courseId}/chapters/{chapterId} — 章节视频及学习状态 */
export function getMyCourseChapter(courseId, chapterId) {
  return request.get(`/api/my/courses/${courseId}/chapters/${chapterId}`)
}

/** POST /api/learning/chapter-videos/{chapterVideoId}/heartbeat — 登录态学习心跳 */
export function sendLearningHeartbeat(chapterVideoId, data) {
  return request.post(`/api/learning/chapter-videos/${chapterVideoId}/heartbeat`, data)
}

/** POST /api/learning/chapter-videos/{chapterVideoId}/external-complete — 外链学习完成确认 */
export function completeExternalLearning(chapterVideoId) {
  return request.post(`/api/learning/chapter-videos/${chapterVideoId}/external-complete`)
}

/** GET /api/courses/{courseId}/class-progress?classId= — 班级学员课程进度 */
export function getCourseClassProgress(courseId, classId) {
  return request.get(`/api/courses/${courseId}/class-progress`, { params: { classId } })
}
