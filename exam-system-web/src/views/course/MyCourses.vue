<template>
  <div class="page-container my-courses">
    <header class="page-intro">
      <div>
        <span class="eyebrow">LEARNING LIBRARY</span>
        <h1>我的课程</h1>
        <p>查看班级课程、章节和已保存的学习进度。</p>
      </div>
      <div class="course-total"><strong>{{ courses.length }}</strong><span>门课程</span></div>
    </header>

    <el-skeleton v-if="loading" :rows="3" animated />
    <el-empty v-else-if="!courses.length" description="你所在的班级暂时没有已开放课程" :image-size="140" />
    <div v-else class="course-grid">
      <article v-for="course in courses" :key="course.courseId" class="course-card">
        <div class="course-cover">
          <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.name" loading="lazy" />
          <div v-else class="cover-art"><span class="cover-mark">{{ initials(course.name) }}</span><span class="cover-label">COURSE</span></div>
          <span class="chapter-count"><el-icon><Reading /></el-icon>{{ course.chapterTotal || 0 }} 章节</span>
        </div>
        <div class="course-body">
          <div class="teacher-label"><el-icon><UserFilled /></el-icon>{{ course.teacherName || '课程教师' }}</div>
          <h2>{{ course.name }}</h2>
          <p class="course-description">{{ course.description || '跟随章节内容完成课程学习。' }}</p>
          <div class="progress-heading"><span>学习进度</span><strong>{{ course.progressPercent || 0 }}%</strong></div>
          <el-progress :percentage="course.progressPercent || 0" :stroke-width="8" :show-text="false" />
          <div class="progress-meta">完成 {{ course.chapterCompleted || 0 }} / {{ course.chapterTotal || 0 }} 个章节</div>
          <el-button type="primary" class="continue-button" @click="router.push(`/my/courses/${course.courseId}`)">
            {{ course.progressPercent ? '继续学习' : '开始学习' }}<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Reading, UserFilled } from '@element-plus/icons-vue'
import { getMyCourses } from '@/api/course'

const router = useRouter()
const loading = ref(true)
const courses = ref([])

function initials(name) {
  return String(name || '课程').trim().slice(0, 2)
}

async function loadCourses() {
  loading.value = true
  try {
    const result = await getMyCourses()
    courses.value = Array.isArray(result) ? result : result?.courses || []
  } catch {
    courses.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadCourses)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.my-courses {
  .page-intro { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 28px; }
  .eyebrow { color: $primary; font-size: 11px; font-weight: 700; letter-spacing: 1.5px; }
  h1 { margin-top: 8px; font-size: 30px; letter-spacing: -0.5px; }
  .page-intro p { margin-top: 7px; color: $text-muted; font-size: 14px; }
  .course-total { display: grid; gap: 2px; text-align: right; color: $text-muted; font-size: 12px; }
  .course-total strong { color: $text-primary; font-size: 24px; font-variant-numeric: tabular-nums; }
  .course-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
  .course-card { @include glass; @include card-hover; border-radius: $radius-xl; overflow: hidden; }
  .course-cover { height: 164px; position: relative; overflow: hidden; background: $primary-soft; }
  .course-cover img { width: 100%; height: 100%; object-fit: cover; }
  .cover-art { position: relative; display: flex; align-items: center; justify-content: center; height: 100%; overflow: hidden; background: linear-gradient(135deg, #e0e7ff, #f1f5f9 60%, #dbeafe); }
  .cover-art::before, .cover-art::after { position: absolute; content: ''; width: 170px; height: 170px; border: 1px solid rgba(79, 70, 229, .13); border-radius: 50%; }
  .cover-art::before { left: -42px; top: -88px; }
  .cover-art::after { right: -55px; bottom: -110px; width: 230px; height: 230px; }
  .cover-mark { position: relative; z-index: 1; color: $primary; font-size: 42px; font-weight: 750; letter-spacing: -2px; }
  .cover-label { position: absolute; right: 16px; top: 14px; color: rgba(79,70,229,.5); font-size: 10px; font-weight: 700; letter-spacing: 1.4px; }
  .chapter-count { position: absolute; left: 12px; bottom: 12px; display: flex; align-items: center; gap: 5px; padding: 5px 9px; border-radius: 20px; background: rgba(255,255,255,.92); color: $text-secondary; font-size: 11px; }
  .course-body { padding: 18px; }
  .teacher-label { display: flex; align-items: center; gap: 6px; color: $text-muted; font-size: 12px; }
  h2 { margin-top: 11px; font-size: 17px; font-weight: 650; }
  .course-description { min-height: 38px; margin-top: 7px; color: $text-muted; font-size: 12px; line-height: 1.6; }
  .progress-heading { display: flex; justify-content: space-between; margin-top: 18px; margin-bottom: 8px; font-size: 12px; color: $text-secondary; }
  .progress-heading strong { color: $primary; font-variant-numeric: tabular-nums; }
  .progress-meta { margin-top: 7px; color: $text-muted; font-size: 11px; }
  .continue-button { width: 100%; margin-top: 17px; }
  .continue-button :deep(.el-icon) { margin-left: auto; }
}

@media (max-width: 940px) { .my-courses .course-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 620px) {
  .my-courses .page-intro { align-items: flex-start; }
  .my-courses h1 { font-size: 25px; }
  .my-courses .course-grid { grid-template-columns: 1fr; }
}
</style>
