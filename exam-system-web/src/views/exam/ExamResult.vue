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
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useExamStore } from '@/stores/exam'
import { getExamRecordById } from '@/api/examRecord'

const route = useRoute()
const examStore = useExamStore()
const loading = ref(false)
const grading = ref(false)
const result = ref(null)

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
  try { result.value = await examStore.gradeExam(); examStore.reset() } finally { grading.value = false }
}

onMounted(async () => {
  loading.value = true
  try {
    const record = await getExamRecordById(Number(route.params.recordId))
    if (record?.status === '已批阅' || record?.score !== null) { result.value = record }
  } finally { loading.value = false }
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
