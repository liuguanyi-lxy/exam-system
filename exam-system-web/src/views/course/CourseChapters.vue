<template>
  <div class="page-container course-chapters" v-loading="loading">
    <div class="page-header">
      <div>
        <el-button link type="primary" class="back-link" @click="router.push('/admin/courses')">
          <el-icon><ArrowLeft /></el-icon> 返回课程列表
        </el-button>
        <h2>{{ course?.name || '课程内容' }}</h2>
        <p class="subtitle">管理章节目录与学习视频，章节顺序决定学员学习路径</p>
      </div>
      <div v-if="canEdit" class="header-actions">
        <el-button @click="openCourseInfo">编辑课程信息</el-button>
        <el-button type="primary" @click="openChapterDialog()"><el-icon><Plus /></el-icon> 新增章节</el-button>
      </div>
    </div>

    <el-alert v-if="!loading && !course" type="warning" :closable="false" show-icon class="permission-alert">
      无法打开课程内容。请确认课程仍存在，且你是课程创建者或管理员。
    </el-alert>

    <el-empty v-if="!loading && course && !chapters.length" description="课程还没有章节" :image-size="100">
      <el-button v-if="canEdit" type="primary" @click="openChapterDialog()">添加第一章</el-button>
    </el-empty>

    <section v-for="(chapter, chapterIndex) in chapters" :key="chapter.id" class="chapter-card">
      <header class="chapter-header">
        <div class="chapter-order">{{ String(chapterIndex + 1).padStart(2, '0') }}</div>
        <div class="chapter-heading">
          <h3>{{ chapter.title }}</h3>
          <p>{{ chapter.description || '暂无章节介绍' }}</p>
          <div class="chapter-stats">
            <span><el-icon><VideoPlay /></el-icon>{{ chapter.videoCount || 0 }} 个视频</span>
            <span><el-icon><CircleCheck /></el-icon>{{ chapter.requiredVideoCount || 0 }} 个必学</span>
          </div>
        </div>
        <div v-if="canEdit" class="chapter-actions">
          <el-tooltip content="上移" :disabled="chapterIndex > 0"><el-button text circle :disabled="chapterIndex === 0" @click="moveChapter(chapter, 'UP')"><el-icon><ArrowUp /></el-icon></el-button></el-tooltip>
          <el-tooltip content="下移"><el-button text circle :disabled="chapterIndex === chapters.length - 1" @click="moveChapter(chapter, 'DOWN')"><el-icon><ArrowDown /></el-icon></el-button></el-tooltip>
          <el-button text circle @click="openChapterDialog(chapter)"><el-icon><Edit /></el-icon></el-button>
          <el-button text circle type="danger" @click="removeChapter(chapter)"><el-icon><Delete /></el-icon></el-button>
        </div>
      </header>

      <div class="chapter-body" v-loading="chapter.loading">
        <el-table :data="chapter.videos" size="small" class="video-table">
          <el-table-column label="视频内容" min-width="250">
            <template #default="{ row }">
              <div class="video-title-cell">
                <span class="source-icon" :class="row.sourceType === 1 ? 'is-external' : 'is-local'"><el-icon><Link v-if="row.sourceType === 1" /><VideoPlay v-else /></el-icon></span>
                <div>
                  <strong>{{ row.title }}</strong>
                  <small>{{ row.sourceType === 1 ? '外链视频' : '站内视频' }}<template v-if="row.durationText"> · {{ row.durationText }}</template></small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="必学" width="90" align="center">
            <template #default="{ row }"><el-switch :model-value="row.required" :disabled="!canEdit" @change="(value) => updateVideoConfig(row, { required: value })" /></template>
          </el-table-column>
          <el-table-column label="完成比例" width="150" align="center">
            <template #default="{ row }">
              <el-input-number :model-value="row.completionRatio" :min="1" :max="100" :step="5" size="small" :disabled="!canEdit || row.sourceType === 1" @change="(value) => updateVideoConfig(row, { completionRatio: value })" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="right">
            <template #default="{ row, $index }">
              <el-button text circle :disabled="!canEdit || $index === 0" @click="moveVideo(chapter, row, 'UP')"><el-icon><ArrowUp /></el-icon></el-button>
              <el-button text circle :disabled="!canEdit || $index === chapter.videos.length - 1" @click="moveVideo(chapter, row, 'DOWN')"><el-icon><ArrowDown /></el-icon></el-button>
              <el-button text circle type="danger" :disabled="!canEdit" @click="removeVideo(chapter, row)"><el-icon><Delete /></el-icon></el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="本章节还没有视频" :image-size="64" /></template>
        </el-table>
        <div v-if="canEdit" class="chapter-footer">
          <span>站内视频按播放时长自动判定完成；外链视频固定要求停留 60 秒。</span>
          <el-button link type="primary" @click="openVideoDialog(chapter)"><el-icon><Plus /></el-icon> 添加视频</el-button>
        </div>
      </div>
    </section>

    <el-dialog v-model="chapterDialogVisible" :title="editingChapter ? '编辑章节' : '新增章节'" width="520px" destroy-on-close>
      <el-form ref="chapterFormRef" :model="chapterForm" :rules="chapterRules" label-width="76px">
        <el-form-item label="章节名称" prop="title"><el-input v-model="chapterForm.title" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="章节介绍"><el-input v-model="chapterForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingChapter" @click="saveChapter">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="videoDialogVisible" title="添加章节视频" width="640px" destroy-on-close>
      <el-tabs v-model="videoSource">
        <el-tab-pane label="选择视频中心内容" name="EXIST">
          <div class="video-picker-tools">
            <el-input v-model="videoKeyword" clearable placeholder="搜索已发布视频" @keyup.enter="loadVideoChoices" />
            <el-button @click="loadVideoChoices">搜索</el-button>
          </div>
          <el-table :data="videoChoices" v-loading="videosLoading" highlight-current-row @current-change="selectedExistingVideo = $event">
            <el-table-column label="选择" width="58" align="center">
              <template #default="{ row }"><el-radio v-model="selectedExistingId" :value="row.id" @change="selectedExistingVideo = row" /></template>
            </el-table-column>
            <el-table-column prop="title" label="视频标题" min-width="230" />
            <el-table-column label="来源" width="100"><template #default="{ row }">{{ row.sourceType === 1 ? '外链' : '站内' }}</template></el-table-column>
            <el-table-column label="时长" width="90"><template #default="{ row }">{{ row.durationText || (row.sourceType === 1 ? '不适用' : '缺少时长') }}</template></el-table-column>
            <template #empty><el-empty description="没有可选视频" :image-size="64" /></template>
          </el-table>
          <p class="form-hint">新上传站内视频请先到视频管理上传，再回到此处选择。</p>
        </el-tab-pane>
        <el-tab-pane label="添加外链视频" name="EXTERNAL">
          <el-form ref="externalFormRef" :model="externalForm" :rules="externalRules" label-width="100px" class="external-form">
            <el-form-item label="视频标题" prop="title"><el-input v-model="externalForm.title" maxlength="100" show-word-limit placeholder="输入学员可见的标题" /></el-form-item>
            <el-form-item label="视频链接" prop="externalUrl"><el-input v-model="externalForm.externalUrl" placeholder="https://www.bilibili.com/video/..." /></el-form-item>
            <el-alert type="info" :closable="false" show-icon title="学员将在新窗口打开外链。需要可见停留满 60 秒后确认完成。" />
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="videoDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addingVideo" @click="addVideo">添加到章节</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="courseInfoVisible" title="编辑课程信息" width="560px">
      <el-form :model="courseInfoForm" label-width="88px">
        <el-form-item label="课程名称"><el-input v-model="courseInfoForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="课程简介"><el-input v-model="courseInfoForm.description" type="textarea" :rows="4" maxlength="500" /></el-form-item>
        <el-form-item label="封面地址"><el-input v-model="courseInfoForm.coverUrl" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="courseInfoVisible = false">取消</el-button><el-button type="primary" :loading="savingCourseInfo" @click="saveCourseInfo">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowUp, CircleCheck, Delete, Edit, Link, Plus, VideoPlay } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getVideos } from '@/api/video'
import {
  addChapterVideo, createCourseChapter, deleteChapterVideo, deleteCourseChapter, getCourseChapters,
  getChapterVideos, moveChapterVideo, moveCourseChapter, updateChapterVideo,
  updateCourse, updateCourseChapter,
} from '@/api/course'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const courseId = computed(() => String(route.params.id || ''))
const loading = ref(false)
const course = ref(null)
const chapters = ref([])
const canEdit = computed(() => !!course.value && (userStore.role === 'ADMIN' || String(course.value.createdBy) === String(userStore.userId)))

const chapterDialogVisible = ref(false)
const chapterFormRef = ref()
const editingChapter = ref(null)
const savingChapter = ref(false)
const chapterForm = reactive({ title: '', description: '' })
const chapterRules = { title: [{ required: true, whitespace: true, message: '请输入章节名称', trigger: 'blur' }] }

const videoDialogVisible = ref(false)
const videoSource = ref('EXIST')
const selectedChapter = ref(null)
const videoKeyword = ref('')
const videoChoices = ref([])
const videosLoading = ref(false)
const selectedExistingId = ref(null)
const selectedExistingVideo = ref(null)
const addingVideo = ref(false)
const externalFormRef = ref()
const externalForm = reactive({ title: '', externalUrl: '' })
const externalRules = {
  title: [{ required: true, whitespace: true, message: '请输入视频标题', trigger: 'blur' }],
  externalUrl: [
    { required: true, message: '请输入视频链接', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      try {
        const url = new URL(value)
        if (!['http:', 'https:'].includes(url.protocol)) callback(new Error('只支持 HTTP 或 HTTPS 链接'))
        else callback()
      } catch { callback(new Error('请输入有效的视频链接')) }
    }, trigger: 'blur' },
  ],
}

const courseInfoVisible = ref(false)
const courseInfoForm = reactive({ name: '', description: '', coverUrl: '' })
const savingCourseInfo = ref(false)

function unwrapList(result, key) {
  if (Array.isArray(result)) return result
  return result?.[key] || []
}

async function loadCourse() {
  const activeId = courseId.value
  if (!/^[1-9]\d*$/.test(activeId)) return
  loading.value = true
  try {
    const chapterResult = await getCourseChapters(activeId)
    if (activeId !== courseId.value) return
    course.value = chapterResult?.course || null
    chapters.value = unwrapList(chapterResult, 'chapters').map((chapter) => ({ ...chapter, videos: chapter.videos || [], loading: false }))
  } catch {
    if (activeId !== courseId.value) return
    course.value = null
    chapters.value = []
  } finally {
    if (activeId === courseId.value) loading.value = false
  }
}

async function loadChapterVideos(chapter) {
  if (!chapter) return
  chapter.loading = true
  try {
    const result = await getChapterVideos(chapter.id)
    chapter.videos = unwrapList(result, 'chapterVideos')
    chapter.videoCount = chapter.videos.length
    chapter.requiredVideoCount = chapter.videos.filter(video => video.required).length
  } catch {
  } finally {
    chapter.loading = false
  }
}

function openChapterDialog(chapter = null) {
  editingChapter.value = chapter
  chapterForm.title = chapter?.title || ''
  chapterForm.description = chapter?.description || ''
  chapterDialogVisible.value = true
}

async function saveChapter() {
  const valid = await chapterFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingChapter.value = true
  const payload = { title: chapterForm.title.trim(), description: chapterForm.description.trim() }
  try {
    if (editingChapter.value) await updateCourseChapter(editingChapter.value.id, payload)
    else await createCourseChapter(courseId.value, payload)
    chapterDialogVisible.value = false
    ElMessage.success(editingChapter.value ? '章节已更新' : '章节已添加')
    await loadCourse()
  } catch {
    // 请求拦截器负责展示接口错误。
  } finally {
    savingChapter.value = false
  }
}

async function moveChapter(chapter, direction) {
  try {
    await moveCourseChapter(chapter.id, direction)
    await loadCourse()
  } catch {
    // 请求拦截器负责展示接口错误。
  }
}

async function removeChapter(chapter) {
  try {
    await ElMessageBox.confirm(`确认删除章节“${chapter.title}”及其中的视频配置？学员学习历史会保留。`, '删除章节', { type: 'warning' })
    await deleteCourseChapter(chapter.id)
    ElMessage.success('章节已删除')
    await loadCourse()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') return
  }
}

async function openVideoDialog(chapter) {
  selectedChapter.value = chapter
  videoSource.value = 'EXIST'
  videoKeyword.value = ''
  selectedExistingId.value = null
  selectedExistingVideo.value = null
  externalForm.title = ''
  externalForm.externalUrl = ''
  videoDialogVisible.value = true
  await loadVideoChoices()
}

async function loadVideoChoices() {
  videosLoading.value = true
  try {
    const result = await getVideos({ page: 1, size: 50, keyword: videoKeyword.value.trim() || undefined })
    videoChoices.value = result?.records || []
  } catch {
    videoChoices.value = []
  } finally {
    videosLoading.value = false
  }
}

async function addVideo() {
  if (!selectedChapter.value) return
  let payload
  if (videoSource.value === 'EXIST') {
    if (!selectedExistingId.value) {
      ElMessage.warning('请先选择一个视频')
      return
    }
    payload = { source: 'EXIST', videoId: selectedExistingId.value, required: true, completionRatio: 70 }
  } else {
    const valid = await externalFormRef.value?.validate().catch(() => false)
    if (!valid) return
    payload = {
      source: 'EXTERNAL', title: externalForm.title.trim(), externalUrl: externalForm.externalUrl.trim(),
      required: true, completionRatio: 70,
    }
  }
  addingVideo.value = true
  try {
    await addChapterVideo(selectedChapter.value.id, payload)
    ElMessage.success('视频已添加到章节')
    videoDialogVisible.value = false
    await loadCourse()
  } catch {
    // 请求拦截器负责展示接口错误。
  } finally {
    addingVideo.value = false
  }
}

async function updateVideoConfig(video, changes) {
  const chapter = chapters.value.find(item => item.id === video.chapterId)
  if (!chapter || chapter.loading) return
  const next = { ...video, ...changes }
  chapter.loading = true
  try {
    const updated = await updateChapterVideo(video.id, {
      required: next.required,
      completionRatio: Number(next.completionRatio) || 70,
    })
    Object.assign(video, updated)
    chapter.requiredVideoCount = chapter.videos.filter(item => item.required).length
    ElMessage.success('视频学习配置已更新')
  } catch {
    await loadChapterVideos(chapter)
  } finally {
    chapter.loading = false
  }
}

async function moveVideo(chapter, video, direction) {
  try {
    await moveChapterVideo(video.id, direction)
    await loadChapterVideos(chapter)
  } catch {
    // 请求拦截器负责展示接口错误。
  }
}

async function removeVideo(chapter, video) {
  try {
    await ElMessageBox.confirm(`确认从“${chapter.title}”移除视频“${video.title}”？`, '移除视频', { type: 'warning' })
    await deleteChapterVideo(video.id)
    ElMessage.success('视频已移除')
    await loadChapterVideos(chapter)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') return
  }
}

function openCourseInfo() {
  Object.assign(courseInfoForm, {
    name: course.value?.name || '', description: course.value?.description || '', coverUrl: course.value?.coverUrl || '',
  })
  courseInfoVisible.value = true
}

async function saveCourseInfo() {
  if (!courseInfoForm.name.trim()) {
    ElMessage.warning('课程名称不能为空')
    return
  }
  savingCourseInfo.value = true
  try {
    await updateCourse(courseId.value, {
      name: courseInfoForm.name.trim(), description: courseInfoForm.description.trim(), coverUrl: courseInfoForm.coverUrl.trim() || null,
    })
    courseInfoVisible.value = false
    ElMessage.success('课程信息已更新')
    await loadCourse()
  } catch {
    // 请求拦截器负责展示接口错误。
  } finally {
    savingCourseInfo.value = false
  }
}

watch(courseId, () => {
  course.value = null
  chapters.value = []
  loading.value = false
  loadCourse()
}, { immediate: true })
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.course-chapters {
  .page-header { align-items: flex-start; }
  .back-link { margin-bottom: 10px; }
  .subtitle { margin-top: 6px; color: $text-muted; font-size: 13px; }
  .header-actions { display: flex; gap: 8px; flex-wrap: wrap; }
  .permission-alert { margin-bottom: 18px; }
  .chapter-card { @include glass; border-radius: $radius-lg; overflow: hidden; margin-bottom: 18px; }
  .chapter-header { display: flex; align-items: center; gap: 16px; padding: 18px 20px; }
  .chapter-order { display: grid; place-items: center; width: 42px; height: 42px; border-radius: $radius-md; background: $primary-soft; color: $primary; font-weight: 700; font-variant-numeric: tabular-nums; }
  .chapter-heading { min-width: 0; flex: 1; }
  .chapter-heading h3 { font-size: 16px; font-weight: 650; }
  .chapter-heading p { margin-top: 5px; color: $text-muted; font-size: 13px; }
  .chapter-stats { display: flex; gap: 16px; margin-top: 9px; color: $text-secondary; font-size: 12px; }
  .chapter-stats span { display: inline-flex; align-items: center; gap: 5px; }
  .chapter-actions { display: flex; align-items: center; }
  .chapter-body { border-top: 1px solid $border; padding: 0 20px 14px; }
  .video-table { width: 100%; }
  .video-title-cell { display: flex; align-items: center; gap: 10px; }
  .video-title-cell > div { display: grid; gap: 4px; }
  .video-title-cell small { color: $text-muted; font-size: 12px; }
  .source-icon { display: grid; place-items: center; width: 32px; height: 32px; border-radius: $radius-sm; background: $primary-soft; color: $primary; }
  .source-icon.is-external { background: #fff7ed; color: $warning; }
  .chapter-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 12px; color: $text-muted; font-size: 12px; }
  .video-picker-tools { display: flex; gap: 10px; margin-bottom: 12px; }
  .form-hint { color: $text-muted; font-size: 12px; margin-top: 8px; }
  .external-form { padding: 12px 0; }
}

@media (max-width: 700px) {
  .course-chapters .chapter-header { align-items: flex-start; padding: 14px; gap: 10px; }
  .course-chapters .chapter-actions { flex-wrap: wrap; }
  .course-chapters .chapter-body { padding: 0 10px 12px; }
  .course-chapters .chapter-footer { align-items: flex-start; flex-direction: column; }
}
</style>
