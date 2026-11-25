<template>
  <div class="page-container result-page" style="max-width:800px;margin:0 auto" v-loading="loading">
    <!-- 结果头部 -->
    <div v-if="result" class="result-hero">
      <div class="result-icon" :class="resultIconClass">
        <el-icon :size="40"><component :is="resultIcon" /></el-icon>
      </div>
      <h2 class="result-title">考试完成</h2>
      <div class="score-display">
        <span class="score-num">{{ result.score }}</span>
        <span class="score-total">/ {{ result.paper?.totalScore || 100 }} 分</span>
      </div>
    </div>
    <el-skeleton v-else-if="loading" :rows="5" animated style="padding:20px" />

    <!-- 整体评价 -->
    <div v-if="result" class="summary-card slide-up">
      <div class="summary-head">
        <el-icon class="summary-icon"><ChatLineRound /></el-icon>
        <span>整体评价</span>
      </div>
      <div class="summary-stats">
        <div v-for="s in summaryStats" :key="s.label" class="stat">
          <span class="stat-value">{{ s.value }}</span>
          <span class="stat-label">{{ s.label }}</span>
        </div>
      </div>
      <p class="summary-text" v-if="result.summary">{{ result.summary }}</p>
      <p class="summary-empty" v-else>本次考试暂无 AI 评语</p>
    </div>

    <section v-if="result" class="diagnostic-card">
      <div class="diagnostic-heading">
        <div>
          <h3>学习诊断</h3>
          <p v-if="diagnostic?.status === 'WAITING_FOR_GRADE'">交卷后生成诊断报告</p>
          <p v-else-if="diagnostic?.status === 'PENDING'">报告正在生成，页面会自动更新</p>
          <p v-else-if="diagnostic?.status === 'FAILED'">报告生成遇到问题，可以重试</p>
          <p v-else-if="diagnostic?.status === 'DEGRADED'">报告已生成，部分建议使用了降级分析</p>
          <p v-else-if="diagnostic?.status === 'READY'">报告已生成</p>
          <p v-else>正在查询报告状态</p>
        </div>
        <el-button v-if="diagnostic?.status === 'FAILED'" :loading="diagnosticLoading" @click="retryDiagnostic">重试</el-button>
      </div>
      <template v-if="diagnostic?.report">
        <div class="diagnostic-metrics">
          <div><strong>{{ diagnostic.report.objectiveScore ?? '—' }}</strong><span>客观题</span></div>
          <div><strong>{{ diagnostic.report.subjectiveScore ?? '—' }}</strong><span>主观题</span></div>
          <div><strong>{{ confidenceLabel(diagnostic.report.confidence) }}</strong><span>置信度</span></div>
        </div>
        <p v-if="diagnostic.report.summary" class="diagnostic-summary">{{ diagnostic.report.summary }}</p>
        <div v-if="diagnostic.report.weakPoints?.length" class="weak-points">
          <h4>优先复习</h4>
          <el-tag v-for="point in diagnostic.report.weakPoints" :key="point.id || point.name" type="warning" effect="light">
            {{ point.name }} · {{ percentage(point.rate) }}
          </el-tag>
        </div>
        <div v-if="diagnostic.report.recommendations?.length" class="recommendations">
          <h4>下一步建议</h4>
          <div v-for="(item, index) in diagnostic.report.recommendations" :key="index" class="recommendation-item">
            <p>{{ recommendationText(item) }}</p>
            <!-- M5：建议条目可携带知识点推荐视频，无视频（旧报告/无映射）按纯文字渲染 -->
            <div v-if="recommendationVideos(item)?.length" class="recommendation-videos">
              <div
                v-for="v in recommendationVideos(item)"
                :key="v.videoId ?? v.fileUrl"
                class="rec-video-card"
                @click="openRecommendVideo(v)"
              >
                <img v-if="v.coverUrl" :src="v.coverUrl" class="rec-cover" alt="" />
                <div v-else class="rec-cover rec-cover-fallback">
                  <el-icon :size="18"><VideoPlay /></el-icon>
                </div>
                <span class="rec-title">{{ v.title }}</span>
                <el-icon class="rec-arrow"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
        </div>
        <div v-if="canReview" class="review-box">
          <div class="review-state">{{ diagnostic.reviewedAt ? `已由 ${diagnostic.reviewedBy || '教师'}复核` : '教师复核' }}</div>
          <el-input v-model="reviewNote" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="记录复核结论或需要人工跟进的内容" />
          <el-button type="primary" :loading="reviewLoading" @click="submitReview">保存复核</el-button>
        </div>
      </template>
      <div v-else class="diagnostic-empty">
        <el-button v-if="diagnostic?.status === 'PENDING'" link :loading="diagnosticLoading" @click="loadDiagnostic">刷新状态</el-button>
        <span v-else-if="diagnosticError">{{ diagnosticError }}</span>
      </div>
    </section>

    <!-- 答题详情 -->
    <div v-if="result?.answerRecords?.length" class="answers-section">
      <h3 class="answers-title">答题详情</h3>
      <div v-for="(a, i) in result.answerRecords" :key="i" class="answer-card">
        <div class="a-header">
          <span class="a-index">{{ i + 1 }}</span>
          <el-tag size="small" :type="a.isCorrect === 1 ? 'success' : a.isCorrect === 2 ? 'warning' : 'danger'" effect="light">
            {{ a.isCorrect === 1 ? '正确' : a.isCorrect === 2 ? '部分正确' : '错误' }}
          </el-tag>
          <span class="a-score">{{ a.score || 0 }} 分</span>
        </div>
        <!-- 手写代码题：答案按代码块展示，保留缩进 -->
        <template v-if="typeOf(a) === 'CODE'">
          <div class="a-code-block">
            <div class="a-code-title">你的代码</div>
            <pre class="a-code">{{ a.userAnswer || '（未作答）' }}</pre>
          </div>
          <div class="a-code-block" v-if="correctAnswer(a)">
            <div class="a-code-title">参考实现</div>
            <pre class="a-code">{{ correctAnswer(a) }}</pre>
          </div>
        </template>
        <template v-else>
          <div class="a-row">
            <span class="a-label">你的答案</span>
            <span class="a-value" :class="{ 'is-empty': !a.userAnswer }">{{ formatUserAnswer(a) || '未作答' }}</span>
          </div>
          <div class="a-row" v-if="correctAnswer(a)">
            <span class="a-label">正确答案</span>
            <span class="a-value is-correct">{{ correctAnswer(a) }}</span>
          </div>
        </template>
        <div class="a-analysis" v-if="analysisOf(a)">
          <span class="a-label">解析</span>
          <span class="a-analysis-text">{{ analysisOf(a) }}</span>
        </div>
        <div class="a-feedback" v-if="a.aiCorrection">
          <el-icon :size="14"><InfoFilled /></el-icon>
          <span>{{ a.aiCorrection }}</span>
        </div>
        <div class="chat-actions">
          <el-button v-if="userStore.role === 'STUDENT'" link type="primary" :loading="chatLoadingId === a.questionId" @click="toggleChat(a.questionId)">
            {{ openChatId === a.questionId ? '收起逐题追问' : '追问这道题' }}
          </el-button>
        </div>
        <div v-if="openChatId === a.questionId" class="chat-panel">
          <el-empty v-if="!chatTurns[a.questionId]?.length && chatLoadingId !== a.questionId" description="还没有追问记录" :image-size="48" />
          <div v-for="turn in chatTurns[a.questionId] || []" :key="turn.requestId || turn.roundNo" class="chat-turn">
            <p><strong>你</strong>{{ turn.message }}</p>
            <p><strong>答</strong>{{ turn.answer || '正在生成回答' }}</p>
          </div>
          <el-input v-model="chatDrafts[a.questionId]" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="针对你的作答继续提问" />
          <div class="chat-footer">
            <span>{{ (chatTurns[a.questionId] || []).length >= 20 ? '本题追问轮数已达上限' : '每道题最多追问 20 轮' }}</span>
            <el-button type="primary" :loading="chatLoadingId === a.questionId" :disabled="!(chatDrafts[a.questionId] || '').trim() || (chatTurns[a.questionId] || []).length >= 20" @click="sendQuestion(a.questionId)">发送</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="result-actions" v-if="result">
      <el-button round @click="$router.push('/exam/history')">
        <el-icon style="margin-right:4px"><Notebook /></el-icon> 查看考试记录
      </el-button>
      <el-button class="btn-gradient" round @click="$router.push('/home')">
        <el-icon style="margin-right:4px"><HomeFilled /></el-icon> 返回首页
      </el-button>
    </div>

    <!-- 手动评分兜底 -->
    <div v-if="!result && !loading" class="no-result">
      <el-empty description="考试记录还未批阅">
        <el-button type="primary" round @click="handleGrade" :loading="grading">请求 AI 评分</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useExamStore } from '@/stores/exam'
import { getExamRecordById } from '@/api/examRecord'
import { gradeExam as gradeExamApi } from '@/api/exam'
import { getDiagnosticReport, retryDiagnosticReport, reviewDiagnosticReport, getQuestionChats, askQuestionChat } from '@/api/diagnostics'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const examStore = useExamStore()
const loading = ref(false)
const grading = ref(false)
const result = ref(null)
const userStore = useUserStore()
const diagnostic = ref(null)
const diagnosticLoading = ref(false)
const diagnosticError = ref('')
const reviewNote = ref('')
const reviewLoading = ref(false)
const openChatId = ref(null)
const chatLoadingId = ref(null)
const chatTurns = reactive({})
const chatDrafts = reactive({})
let diagnosticPoll = null
let chatPoll = null
const recordId = computed(() => Number(route.params.recordId))
const canReview = computed(() => ['ADMIN', 'TEACHER'].includes(userStore.role) && !!diagnostic.value?.report)

const resultIcon = computed(() => {
  if (!result.value) return 'InfoFilled'
  const total = result.value.paper?.totalScore || 100
  const pct = result.value.score / total
  if (pct >= 0.8) return 'CircleCheckFilled'
  if (pct >= 0.6) return 'CircleCheck'
  return 'WarningFilled'
})
const resultIconClass = computed(() => {
  if (!result.value) return ''
  const total = result.value.paper?.totalScore || 100
  const pct = result.value.score / total
  if (pct >= 0.8) return 'icon-excellent'
  if (pct >= 0.6) return 'icon-pass'
  return 'icon-fail'
})

// 根据答题记录找到对应题目，返回格式化后的正确答案
function findQuestion(a) {
  return (result.value?.paper?.questions || []).find((q) => Number(q.id) === Number(a.questionId))
}

function typeOf(a) {
  return findQuestion(a)?.type || 'CHOICE'
}

function correctAnswer(a) {
  const q = findQuestion(a)
  if (!q) return ''
  if (q.type === 'CHOICE') {
    const choices = q.choices || []
    // 多选题要把所有正确选项都列出来，只取第一个会误导考生
    const parts = choices
      .map((c, i) => (c.isCorrect ? `${String.fromCharCode(65 + i)}. ${c.content}` : null))
      .filter(Boolean)
    if (parts.length) return parts.join('　')
    return q.answer?.answer || ''
  }
  if (q.type === 'JUDGE') {
    const ans = q.answer?.answer
    if (ans === 'TRUE' || ans === '正确') return '正确'
    if (ans === 'FALSE' || ans === '错误') return '错误'
    return ans || ''
  }
  return q.answer?.answer || ''
}

// 学生答案补上选项字母，和「正确答案」保持同一种表述
function formatUserAnswer(a) {
  const raw = a.userAnswer
  if (!raw) return ''
  const q = findQuestion(a)
  if (!q || q.type !== 'CHOICE') return raw
  const choices = q.choices || []
  return String(raw)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((content) => {
      const idx = choices.findIndex((c) => c.content === content)
      return idx >= 0 ? `${String.fromCharCode(65 + idx)}. ${content}` : content
    })
    .join('　')
}

// 每题的解析来自试卷题目（批阅后后端才下发）
function analysisOf(a) {
  return findQuestion(a)?.analysis || ''
}

// 整体评价用到的三个指标
const summaryStats = computed(() => {
  const r = result.value
  if (!r) return []
  const total = r.paper?.totalScore || 100
  const answers = r.answerRecords || []
  const correct = answers.filter((a) => a.isCorrect === 1).length
  const list = [
    { label: '得分率', value: `${total ? Math.round((r.score / total) * 100) : 0}%` },
    { label: '答对题数', value: `${correct} / ${answers.length}` },
  ]
  const minutes = usedMinutes(r.startTime, r.endTime)
  if (minutes !== null) list.push({ label: '用时', value: `${minutes} 分钟` })
  return list
})

function usedMinutes(start, end) {
  if (!start || !end) return null
  const s = new Date(start.replace(/-/g, '/')).getTime()
  const e = new Date(end.replace(/-/g, '/')).getTime()
  if (!s || !e || e <= s) return null
  return Math.max(1, Math.round((e - s) / 60000))
}

async function handleGrade() {
  grading.value = true
  try {
    // 直接按记录ID触发判卷，不依赖进行中的考试会话（从历史记录进入也能兜底重判）
    result.value = await gradeExamApi(recordId.value)
    examStore.reset()
    await loadDiagnostic()
  } finally { grading.value = false }
}

function confidenceLabel(value) {
  return ({ NORMAL: '高', LIMITED: '低' })[value] || value || '待评估'
}

// M5 建议条目：新数据为 {text, videos}，历史纯字符串按纯文字兼容
function recommendationText(item) {
  return typeof item === 'string' ? item : (item?.text || '')
}

function recommendationVideos(item) {
  return typeof item === 'string' ? null : item?.videos
}

// 有 videoId 走站内视频播放页，否则兜底新窗口打开外链
function openRecommendVideo(video) {
  if (video?.videoId) {
    router.push(`/videos/${video.videoId}`)
  } else if (video?.fileUrl) {
    window.open(video.fileUrl, '_blank')
  }
}

function percentage(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return `${Math.round(Number(value))}%`
}

async function loadDiagnostic() {
  if (!recordId.value || diagnosticLoading.value) return
  diagnosticLoading.value = true
  diagnosticError.value = ''
  try {
    diagnostic.value = await getDiagnosticReport(recordId.value)
    reviewNote.value = diagnostic.value?.reviewNote || ''
    if (diagnostic.value?.status === 'PENDING' && !diagnosticPoll) {
      diagnosticPoll = setInterval(async () => {
        if (!diagnostic.value || diagnostic.value.status !== 'PENDING') return stopDiagnosticPoll()
        await loadDiagnostic()
      }, 4000)
    } else if (diagnostic.value?.status !== 'PENDING') stopDiagnosticPoll()
  } catch (error) {
    diagnosticError.value = error?.message || '诊断报告暂时无法读取'
  } finally {
    diagnosticLoading.value = false
  }
}

function stopDiagnosticPoll() {
  if (diagnosticPoll) clearInterval(diagnosticPoll)
  diagnosticPoll = null
}

async function submitReview() {
  if (!recordId.value || reviewLoading.value) return
  reviewLoading.value = true
  try {
    diagnostic.value = await reviewDiagnosticReport(recordId.value, reviewNote.value.trim())
  } finally { reviewLoading.value = false }
}

async function retryDiagnostic() {
  if (!recordId.value || diagnosticLoading.value) return
  diagnosticLoading.value = true
  diagnosticError.value = ''
  try {
    diagnostic.value = await retryDiagnosticReport(recordId.value)
    if (diagnostic.value?.status === 'PENDING' && !diagnosticPoll) {
      diagnosticPoll = setInterval(() => loadDiagnostic(), 4000)
    }
  } catch (error) {
    diagnosticError.value = error?.message || '报告重试失败'
  } finally { diagnosticLoading.value = false }
}

async function toggleChat(questionId) {
  if (openChatId.value === questionId) {
    openChatId.value = null
    return
  }
  openChatId.value = questionId
  if (chatTurns[questionId]) return
  chatLoadingId.value = questionId
  try {
    chatTurns[questionId] = await getQuestionChats(recordId.value, questionId) || []
  } finally { chatLoadingId.value = null }
}

function createRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16)
    return (character === 'x' ? random : (random & 0x3) | 0x8).toString(16)
  })
}

function syncChatPolling() {
  const hasPending = Object.values(chatTurns).some((turns) => turns.some((turn) => turn.status === 'PENDING'))
  if (hasPending && !chatPoll) {
    chatPoll = setInterval(async () => {
      if (!openChatId.value) return
      try {
        chatTurns[openChatId.value] = await getQuestionChats(recordId.value, openChatId.value) || []
        if (!Object.values(chatTurns).some((turns) => turns.some((turn) => turn.status === 'PENDING'))) stopChatPoll()
      } catch {}
    }, 2500)
  } else if (!hasPending) stopChatPoll()
}

function stopChatPoll() {
  if (chatPoll) clearInterval(chatPoll)
  chatPoll = null
}

async function sendQuestion(questionId) {
  const message = (chatDrafts[questionId] || '').trim()
  if (!message || chatLoadingId.value) return
  chatLoadingId.value = questionId
  try {
    const turn = await askQuestionChat(recordId.value, questionId, { requestId: createRequestId(), message })
    chatTurns[questionId] = [...(chatTurns[questionId] || []), turn]
    chatDrafts[questionId] = ''
    syncChatPolling()
  } finally { chatLoadingId.value = null }
}

onMounted(async () => {
  loading.value = true
  try {
    const record = await getExamRecordById(Number(route.params.recordId))
    if (record?.status === '已批阅') {
      result.value = record
    } else if (record?.status === '已结束') {
      // 交卷后未判卷：结果页自动触发判卷（判卷接口幂等，已批阅的记录直接返回）。
      // 不能用 score 判断是否已出分：数据库 score 默认 0，未判卷记录也会被误当成已批阅，
      // 导致判卷请求永远不会发出、答题记录停留在 is_correct=0 被展示成全部错误
      result.value = await gradeExamApi(Number(route.params.recordId))
    }
    if (result.value) await loadDiagnostic()
  } finally { loading.value = false }
})

onBeforeUnmount(() => {
  stopDiagnosticPoll()
  stopChatPoll()
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.result-page { padding: 28px 32px; }

.result-hero {
  text-align: center;
  padding: 8px 0 28px;
  border-bottom: 1px solid $border;
  margin-bottom: 20px;

  .result-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 64px;
    height: 64px;
    border-radius: 16px;
    margin-bottom: 14px;
    &.icon-excellent { background: #ecfdf3; color: $success; }
    &.icon-pass { background: $primary-soft; color: $primary; }
    &.icon-fail { background: #fef3f2; color: $danger; }
  }
  .result-title {
    font-size: 15px;
    font-weight: 500;
    margin-bottom: 10px;
    color: $text-secondary;
  }
  .score-display {
    display: flex;
    align-items: baseline;
    justify-content: center;
    gap: 6px;
    .score-num {
      font-size: 52px;
      font-weight: 600;
      line-height: 1;
      color: $text-primary;
      font-variant-numeric: tabular-nums;
    }
    .score-total { font-size: 16px; color: $text-muted; }
  }
}

.summary-card {
  @include glass;
  border-radius: $radius-lg;
  padding: 16px 20px;
  margin-bottom: 20px;
  .summary-head {
    display: flex;
    align-items: center;
    gap: 7px;
    font-weight: 600;
    font-size: 14px;
    margin-bottom: 10px;
    .summary-icon { color: $primary; font-size: 16px; }
  }
  .summary-stats {
    display: flex;
    gap: 28px;
    padding: 12px 0 14px;
    margin-bottom: 12px;
    border-bottom: 1px solid $border;

    .stat {
      display: flex;
      flex-direction: column;
      gap: 2px;
      .stat-value {
        font-size: 18px;
        font-weight: 600;
        color: $text-primary;
        font-variant-numeric: tabular-nums;
      }
      .stat-label { font-size: 12px; color: $text-muted; }
    }
  }
  .summary-text {
    color: $text-secondary;
    line-height: 1.8;
    font-size: 13.5px;
    white-space: pre-line;
  }
  .summary-empty { font-size: 13px; color: $text-muted; }
}

.answers-section {
  margin-bottom: 24px;
  .answers-title {
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 12px;
  }
}

.diagnostic-card { @include glass; border-radius: $radius-lg; padding: 20px; margin-bottom: 22px; }
.diagnostic-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; h3 { margin: 0 0 5px; font-size: 16px; } p { margin: 0; color: $text-muted; font-size: 13px; } }
.diagnostic-metrics { display: flex; gap: 30px; padding: 18px 0; div { display: flex; flex-direction: column; gap: 3px; } strong { font-size: 19px; color: $text-primary; } span { color: $text-muted; font-size: 12px; } }
.diagnostic-summary { margin: 0; line-height: 1.75; white-space: pre-line; color: $text-secondary; }
.weak-points, .recommendations { margin-top: 16px; h4 { font-size: 13px; margin: 0 0 9px; } }
.weak-points { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; h4 { width: 100%; } }
.recommendations p { margin: 6px 0; color: $text-secondary; font-size: 13px; line-height: 1.6; }
.recommendation-item { margin-bottom: 10px; p { margin: 0 0 6px; } }
.recommendation-videos { display: flex; flex-wrap: wrap; gap: 8px; }
.rec-video-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 6px;
  border: 1px solid $border;
  border-radius: $radius-md;
  background: #fcfcfd;
  cursor: pointer;
  transition: border-color $transition-fast, background $transition-fast;
  max-width: 320px;

  &:hover {
    border-color: $primary-lighter;
    background: $primary-soft;
    .rec-arrow { color: $primary; }
  }
  .rec-cover {
    flex-shrink: 0;
    width: 52px;
    height: 34px;
    border-radius: $radius-sm;
    object-fit: cover;
    background: $bg-soft;
    &.rec-cover-fallback {
      display: flex;
      align-items: center;
      justify-content: center;
      color: $text-muted;
      border: 1px solid $border;
    }
  }
  .rec-title {
    flex: 1;
    min-width: 0;
    font-size: 12.5px;
    color: $text-primary;
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
  .rec-arrow {
    flex-shrink: 0;
    color: $text-muted;
    font-size: 13px;
  }
}
.review-box { display: grid; gap: 10px; margin-top: 18px; padding-top: 14px; border-top: 1px solid $border; }
.review-state { color: $text-muted; font-size: 12px; }
.diagnostic-empty { padding-top: 12px; color: $text-muted; font-size: 13px; }
.chat-actions { border-top: 1px solid $border; margin-top: 12px; padding-top: 8px; }
.chat-panel { display: grid; gap: 10px; margin-top: 8px; padding: 14px; border: 1px solid $border; border-radius: $radius-sm; background: #fcfcfd; }
.chat-turn { padding: 10px 12px; border-radius: $radius-sm; background: white; p { margin: 4px 0; color: $text-secondary; font-size: 13px; line-height: 1.6; white-space: pre-line; } strong { display: inline-block; width: 30px; color: $text-primary; } }
.chat-footer { display: flex; justify-content: space-between; align-items: center; color: $text-muted; font-size: 12px; }
.answer-card {
  @include glass;
  border-radius: $radius-lg;
  padding: 14px 18px;
  margin-bottom: 10px;

  .a-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
    .a-index {
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: $bg-soft;
      color: $text-secondary;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      font-weight: 600;
    }
    .a-score {
      margin-left: auto;
      font-size: 13px;
      font-weight: 600;
      color: $text-primary;
      font-variant-numeric: tabular-nums;
    }
  }

  .a-row {
    display: flex;
    gap: 10px;
    font-size: 13.5px;
    line-height: 1.7;
    padding: 2px 0;
    .a-label {
      flex-shrink: 0;
      width: 60px;
      color: $text-muted;
      font-size: 12.5px;
    }
    .a-value { color: $text-primary; word-break: break-word; }
    .a-value.is-empty { color: $text-muted; }
    .a-value.is-correct { color: #067647; }
  }

  .a-code-block {
    margin-top: 10px;
    .a-code-title {
      font-size: 12px;
      color: $text-muted;
      margin-bottom: 6px;
    }
    .a-code {
      margin: 0;
      padding: 12px 14px;
      background: #fbfbfd;
      border: 1px solid $border;
      border-radius: $radius-sm;
      font-family: $font-mono;
      font-size: 12.5px;
      line-height: 1.7;
      color: $text-primary;
      white-space: pre-wrap;
      word-break: break-word;
      max-height: 420px;
      overflow: auto;
    }
  }

  .a-analysis {
    display: flex;
    gap: 10px;
    margin-top: 10px;
    padding: 10px 12px;
    background: #fcfcfd;
    border: 1px solid $border;
    border-radius: $radius-sm;
    font-size: 13px;
    line-height: 1.7;
    .a-label {
      flex-shrink: 0;
      width: 60px;
      color: $text-muted;
      font-size: 12.5px;
    }
    .a-analysis-text { color: $text-secondary; word-break: break-word; }
  }

  .a-feedback {
    display: flex;
    gap: 7px;
    margin-top: 10px;
    padding: 9px 12px;
    background: $bg-soft;
    border-radius: $radius-sm;
    color: $text-secondary;
    font-size: 13px;
    line-height: 1.65;
    .el-icon { color: $text-muted; flex-shrink: 0; margin-top: 3px; }
  }
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.no-result {
  padding: 40px 0;
}
</style>
