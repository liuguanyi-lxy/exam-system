<template>
  <div class="page-container result-page" style="max-width:800px;margin:0 auto" v-loading="loading">
    <!-- 结果头部 -->
    <div v-if="result" class="result-hero">
      <div class="result-icon" :class="resultIconClass">
        <el-icon :size="40"><component :is="resultIcon" /></el-icon>
      </div>
      <h2 class="result-title">考试完成</h2>
      <div class="score-display">
        <span class="score-num text-gradient">{{ result.score }}</span>
        <span class="score-total">/ {{ result.paper?.totalScore || 100 }}</span>
      </div>
      <p class="score-label">分</p>
    </div>
    <el-skeleton v-else-if="loading" :rows="5" animated style="padding:20px" />

    <!-- AI 评语 -->
    <div v-if="result?.summary" class="summary-card slide-up">
      <div class="summary-head">
        <el-icon class="summary-icon"><ChatLineRound /></el-icon>
        <span>AI 评语</span>
      </div>
      <p class="summary-text">{{ result.summary }}</p>
    </div>

    <!-- 答题详情 -->
    <div v-if="result?.answerRecords?.length" class="answers-section">
      <h3 class="answers-title">答题详情</h3>
      <div v-for="(a, i) in result.answerRecords" :key="i" class="answer-card">
        <div class="a-header">
          <span class="a-index">{{ i + 1 }}</span>
          <el-tag size="small" :type="a.isCorrect === 1 ? 'success' : a.isCorrect === 2 ? 'warning' : 'danger'" effect="light">
            {{ a.isCorrect === 1 ? '✓ 正确' : a.isCorrect === 2 ? '△ 部分正确' : '✗ 错误' }}
          </el-tag>
          <span class="a-score">{{ a.score || 0 }}分</span>
        </div>
        <div class="a-answer">你的答案: {{ a.userAnswer || '(未作答)' }}</div>
        <div class="a-correct" v-if="correctAnswer(a)">
          <span class="correct-label">正确答案:</span> {{ correctAnswer(a) }}
        </div>
        <div class="a-feedback" v-if="a.aiCorrection">{{ a.aiCorrection }}</div>
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
  if (pct >= 0.6) return 'SuccessFilled'
  return 'WarningFilled'
})
const resultIconClass = computed(() => {
  if (!result.value) return ''
  const total = result.value.paper?.totalScore || 100
  const pct = result.value.score / total
  if (pct >= 0.8) return 'icon-excellent'
  if (pct >= 0.6) return 'icon-good'
  return 'icon-fail'
})

// 根据答题记录找到对应题目，返回格式化后的正确答案
function correctAnswer(a) {
  const q = (result.value?.paper?.questions || []).find((q) => Number(q.id) === Number(a.questionId))
  if (!q) return ''
  if (q.type === 'CHOICE') {
    const c = (q.choices || []).find((c) => c.isCorrect)
    if (c) return `${String.fromCharCode(65 + c.sort)}. ${c.content}`
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

.result-page { padding: 32px; }

.result-hero {
  text-align: center;
  padding: 24px 0 32px;
  animation: fade-in-up 0.5s ease-out both;

  .result-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 84px;
    height: 84px;
    border-radius: 26px;
    margin-bottom: 16px;
    &.icon-excellent { background: linear-gradient(135deg, #ecfdf5, #d1fae5); color: $success; }
    &.icon-good { background: linear-gradient(135deg, #fffbeb, #fef3c7); color: $warning; }
    &.icon-fail { background: linear-gradient(135deg, #fef2f2, #fee2e2); color: $danger; }
  }
  .result-title { font-size: 20px; font-weight: 600; margin-bottom: 12px; color: $text-primary; }
  .score-display {
    display: flex;
    align-items: baseline;
    justify-content: center;
    gap: 8px;
    .score-num { font-size: 64px; font-weight: 800; line-height: 1; }
    .score-total { font-size: 22px; color: $text-muted; }
  }
  .score-label { color: $text-muted; font-size: 14px; margin-top: 4px; }
}

.summary-card {
  @include glass;
  border-radius: 16px;
  padding: 20px 24px;
  margin-bottom: 24px;
  .summary-head {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 700;
    margin-bottom: 12px;
    .summary-icon { color: $secondary; font-size: 18px; }
  }
  .summary-text {
    color: $text-secondary;
    line-height: 1.8;
    font-size: 14px;
    white-space: pre-line;
  }
}

.answers-section {
  margin-bottom: 24px;
  .answers-title { font-size: 17px; font-weight: 700; margin-bottom: 16px; }
}
.answer-card {
  @include glass;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 12px;
  .a-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
    .a-index {
      width: 26px;
      height: 26px;
      border-radius: 8px;
      background: rgba(99,102,241,.1);
      color: $primary;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 600;
    }
    .a-score { margin-left: auto; font-weight: 600; color: $text-primary; }
  }
  .a-answer { color: $text-secondary; margin-bottom: 8px; font-size: 14px; line-height: 1.6; }
  .a-correct {
    color: $success;
    font-size: 13px;
    margin-bottom: 8px;
    padding: 8px 14px;
    background: rgba(16, 185, 129, 0.08);
    border-radius: 8px;
    line-height: 1.6;
    .correct-label { font-weight: 600; }
  }
  .a-feedback {
    color: $primary;
    font-size: 13px;
    background: rgba(99, 102, 241, 0.07);
    padding: 8px 14px;
    border-radius: 8px;
    line-height: 1.6;
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
