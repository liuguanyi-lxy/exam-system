<template>
  <div class="exam-list" v-loading="loading">
    <div class="page-head">
      <h2 class="page-title">在线考试</h2>
      <p class="sub">选择已发布的试卷，开始或继续考试</p>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" />

    <el-row v-if="papers.length" :gutter="16">
      <el-col :xs="24" :sm="12" :md="8" v-for="p in papers" :key="p.id" style="margin-bottom:16px">
        <div class="paper-card card-hover" :class="{ blocked: !!startBlockReason(p) }" @click="handleExamAction(p)">
          <div class="paper-head">
            <span class="paper-icon"><el-icon :size="16"><Document /></el-icon></span>
            <span class="head-tags">
              <template v-if="p.assigned">
                <el-tag size="small" effect="light">已分配</el-tag>
                <el-tag v-if="isWindowStatus(p.windowStatus)" size="small" :type="windowTagType(p.windowStatus)" effect="light">
                  {{ p.windowStatus }}
                </el-tag>
              </template>
              <el-tag v-else size="small" type="success" effect="light">已发布</el-tag>
            </span>
          </div>
          <span class="paper-name">{{ p.name }}</span>
          <p class="paper-desc">{{ p.description || '暂无描述' }}</p>
          <div v-if="p.assigned" class="assign-line">
            <el-icon :size="12"><Clock /></el-icon>
            <span>考试时间：{{ fmtWindow(p) }}</span>
            <span v-if="p.attemptLeft != null" class="attempt-left" :class="{ 'attempt-zero': p.attemptLeft <= 0 }">
              剩余 {{ p.attemptLeft }} 次
            </span>
          </div>
          <div class="paper-meta">
            <div class="meta-item">
              <span class="meta-val">{{ p.duration }}</span>
              <span class="meta-unit">分钟</span>
            </div>
            <div class="meta-item">
              <span class="meta-val">{{ p.questionCount }}</span>
              <span class="meta-unit">题</span>
            </div>
            <div class="meta-item">
              <span class="meta-val">{{ p.totalScore }}</span>
              <span class="meta-unit">分</span>
            </div>
          </div>
          <div class="paper-actions">
            <p v-if="startBlockReason(p)" class="block-reason">{{ startBlockReason(p) }}</p>
            <el-button type="primary" plain :disabled="!canStart(p)" @click.stop="handleExamAction(p)">
              {{ actionLabel(p) }}
              <el-icon style="margin-left:4px"><Right /></el-icon>
            </el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-empty v-else-if="!loading && !loadError" description="暂无可用考试，请稍后再来">
      <el-button class="btn-gradient" @click="$router.push('/exam/history')">查看我的考试</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPaperList } from '@/api/paper'
import { useExamStore } from '@/stores/exam'

const router = useRouter()
const examStore = useExamStore()
const loading = ref(false)
const papers = ref([])
const loadError = ref('')
let loadRequestId = 0

onMounted(loadData)

async function loadData() {
  const requestId = ++loadRequestId
  loading.value = true
  loadError.value = ''
  try {
    const paperResult = await getPaperList({ status: 'PUBLISHED' })
    if (requestId !== loadRequestId) return
    papers.value = Array.isArray(paperResult) ? paperResult : []
  } catch {
    if (requestId === loadRequestId) loadError.value = '考试列表加载失败，请稍后重试'
  } finally {
    if (requestId === loadRequestId) loading.value = false
  }
}

function hasInProgress(paper) {
  const session = examStore.currentExam
  return Boolean(paper.resumeRecordId)
    || Boolean(session && Number(session.paperId) === Number(paper.id) && Number(session.recordId) === Number(paper.resumeRecordId) && !session.isSubmitted)
}

function actionLabel(paper) {
  return hasInProgress(paper) ? '继续考试' : '开始考试'
}

async function handleExamAction(paper) {
  const session = examStore.currentExam
  if (session && Number(session.paperId) === Number(paper.id) && Number(session.recordId) === Number(paper.resumeRecordId) && !session.isSubmitted) {
    router.push(`/exam/taking/${session.recordId}`)
    return
  }

  const recordId = paper.resumeRecordId
  if (recordId) {
    let savedSession = null
    try { savedSession = JSON.parse(sessionStorage.getItem('exam-taking-state') || 'null') } catch { /* ignore corrupt local state */ }
    if (savedSession && Number(savedSession.recordId) === Number(recordId)) {
      try {
        if (await examStore.tryRestore()) {
          router.push(`/exam/taking/${recordId}`)
          return
        }
      } catch {
        ElMessage.error('本地考试进度恢复失败，未重新创建考试记录')
        return
      }
    }
    const confirmed = await ElMessageBox.confirm(
      '此标签没有保存的作答。继续后会复用进行中的考试记录，剩余时间仍按原开考时间计算。',
      '继续考试',
      { type: 'warning', confirmButtonText: '继续考试', cancelButtonText: '返回' },
    ).then(() => true).catch(() => false)
    if (confirmed) router.push(`/exam/start/${paper.id}?resumeRecordId=${recordId}`)
    return
  }

  if (canStart(paper)) router.push(`/exam/start/${paper.id}`)
}

// ---- M3 班级分配：窗口三态与剩余次数（无班级学员行为不变，后端 startExam 强校验兜底） ----
function canStart(p) {
  if (hasInProgress(p)) return true
  if (p.status !== 'PUBLISHED') return false
  if (!p.assigned) return true
  if (p.windowStatus && p.windowStatus !== '进行中') return false
  if (p.attemptLeft != null && p.attemptLeft <= 0) return false
  return true
}

function startBlockReason(p) {
  if (hasInProgress(p)) return ''
  if (p.status !== 'PUBLISHED') return '当前试卷不可参加'
  if (!p.assigned) return ''
  if (p.windowStatus === '未开始') return `考试时间：${fmtWindow(p)}，尚未开始`
  if (p.windowStatus === '已结束') return `考试时间：${fmtWindow(p)}，已结束`
  if (p.attemptLeft != null && p.attemptLeft <= 0) return '考试次数已用完'
  return ''
}

// 窗口时间取后端返回的精确时间（NULL 侧显示"不限"），与开考报错文案口径一致
function fmtWindow(p) {
  const s = p.examStartTime ? String(p.examStartTime).replace('T', ' ').slice(0, 16) : '不限'
  const e = p.examEndTime ? String(p.examEndTime).replace('T', ' ').slice(0, 16) : '不限'
  return `${s} ~ ${e}`
}

function windowTagType(status) {
  if (status === '进行中') return 'success'
  if (status === '已结束') return 'info'
  return 'warning' // 未开始
}

function isWindowStatus(status) {
  return ['未开始', '进行中', '已结束'].includes(status)
}

function handleCardClick(p) {
  if (canStart(p)) handleExamAction(p)
}
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.page-head {
  margin-bottom: 20px;
  .page-title {
    font-size: 22px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 4px;
  }
  .sub { color: $text-muted; font-size: 13px; margin: 0; }
}

.paper-card {
  position: relative;
  background: $surface;
  border-radius: $radius-xl;
  padding: 18px;
  border: 1px solid $border;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  height: 100%;
  @include card-hover(-2px, false);

  .paper-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 12px;
    .paper-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 30px;
      height: 30px;
      border-radius: $radius-sm;
      background: $bg-soft;
      color: $text-secondary;
    }
    .head-tags {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }
  .assign-line {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: -6px 0 12px;
    font-size: 12px;
    color: $text-muted;
    .attempt-left {
      margin-left: auto;
      color: $text-secondary;
      &.attempt-zero { color: $danger; font-weight: 500; }
    }
  }
  .block-reason {
    margin: 0 0 8px;
    font-size: 12px;
    line-height: 1.5;
    color: $warning;
    text-align: center;
  }
  &.blocked {
    cursor: not-allowed;
  }
  .paper-name {
    display: block;
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 6px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .paper-desc {
    color: $text-muted;
    font-size: 12.5px;
    min-height: 38px;
    margin: 0 0 14px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    line-height: 1.6;
  }
  .paper-meta {
    display: flex;
    padding: 12px 0;
    margin-bottom: 14px;
    border-top: 1px solid $border;
    border-bottom: 1px solid $border;
    .meta-item {
      flex: 1;
      display: flex;
      align-items: baseline;
      justify-content: center;
      gap: 2px;
      & + .meta-item { border-left: 1px solid $border; }
      .meta-val {
        font-size: 16px;
        font-weight: 600;
        color: $text-primary;
        font-variant-numeric: tabular-nums;
      }
      .meta-unit { font-size: 11.5px; color: $text-muted; }
    }
  }
  .paper-actions {
    margin-top: auto;
    .el-button { width: 100%; }
  }
}
</style>
