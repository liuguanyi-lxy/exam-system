<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div class="head-left">
        <el-button link @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2>答卷详情</h2>
        <el-tag v-if="record" size="small" :type="record.status === '已批阅' ? 'success' : 'info'" effect="light">
          {{ record.status }}
        </el-tag>
      </div>
      <div class="head-score" v-if="record && record.score != null">
        <span class="score">{{ record.score }}</span>
        <span class="score-total">/ {{ record.paper?.totalScore || 100 }}</span>
      </div>
    </div>

    <template v-if="record">
      <el-descriptions :column="3" border class="meta-desc">
        <el-descriptions-item label="考生">{{ record.studentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="试卷">{{ record.paper?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用时">{{ usedMinutes }}</el-descriptions-item>
        <el-descriptions-item label="开始">{{ record.startTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交">{{ record.endTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="切屏次数">
          <span :class="{ warn: (record.windowSwitches || 0) > 0 }">{{ record.windowSwitches ?? 0 }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="record.summary" class="summary-card">
        <div class="summary-head">
          <el-icon><ChatLineRound /></el-icon><span>AI 整体评价</span>
        </div>
        <p class="summary-text">{{ record.summary }}</p>
      </div>

      <!-- 逐题答卷 -->
      <div v-if="items.length" class="answers">
        <h3 class="answers-title">
          逐题答卷
          <span class="answers-sub">{{ correctCount }} / {{ items.length }} 题正确</span>
        </h3>

        <div v-for="(it, i) in items" :key="i" class="answer-card">
          <div class="a-head">
            <span class="a-index">{{ i + 1 }}</span>
            <el-tag size="small" effect="light">{{ typeLabel(it.type) }}</el-tag>
            <el-tag size="small" effect="light"
              :type="it.isCorrect === 1 ? 'success' : it.isCorrect === 2 ? 'warning' : 'danger'">
              {{ it.isCorrect === 1 ? '正确' : it.isCorrect === 2 ? '部分正确' : '错误' }}
            </el-tag>
            <span class="a-score">{{ it.score ?? 0 }} 分</span>
          </div>

          <div class="a-title">{{ it.title }}</div>

          <div class="a-row">
            <span class="a-label">考生答案</span>
            <pre v-if="it.isCode" class="a-code">{{ it.userAnswer || '（未作答）' }}</pre>
            <span v-else class="a-value" :class="{ 'is-empty': !it.userAnswer }">{{ it.userAnswer || '（未作答）' }}</span>
          </div>

          <div class="a-row" v-if="it.correctAnswer">
            <span class="a-label">正确答案</span>
            <pre v-if="it.isCode" class="a-code">{{ it.correctAnswer }}</pre>
            <span v-else class="a-value is-correct">{{ it.correctAnswer }}</span>
          </div>

          <div class="a-row" v-if="it.analysis">
            <span class="a-label">解析</span>
            <span class="a-value is-muted">{{ it.analysis }}</span>
          </div>

          <div class="a-feedback" v-if="it.aiCorrection && !it.isJudgeInfo">
            <el-icon :size="14"><InfoFilled /></el-icon>
            <span>{{ it.aiCorrection }}</span>
          </div>
        </div>
      </div>
      <el-empty v-else description="该考生还没有提交答卷" :image-size="70" />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getExamRecordById } from '@/api/examRecord'

const route = useRoute()
const loading = ref(false)
const record = ref(null)

const TYPE_LABELS = { CHOICE: '选择题', JUDGE: '判断题', TEXT: '简答题', CODE: '代码题' }
function typeLabel(t) {
  return TYPE_LABELS[t] || '题目'
}

// 把答题记录和试卷里的题目合并，老师看的时候不用再去对 questionId
const items = computed(() => {
  const r = record.value
  if (!r?.answerRecords?.length) return []
  const qMap = new Map((r.paper?.questions || []).map((q) => [Number(q.id), q]))
  return r.answerRecords.map((a) => {
    const q = qMap.get(Number(a.questionId)) || {}
    const choices = q.choices || []
    let correctAnswer = ''
    if (q.type === 'CHOICE') {
      correctAnswer = choices
        .map((c, i) => (c.isCorrect ? `${String.fromCharCode(65 + i)}. ${c.content}` : null))
        .filter(Boolean)
        .join('　')
    } else if (q.type === 'JUDGE') {
      const ans = q.answer?.answer
      correctAnswer = ans === 'TRUE' ? '正确' : ans === 'FALSE' ? '错误' : ans || ''
    } else {
      correctAnswer = q.answer?.answer || ''
    }
    const isCode = q.type === 'CODE'
    return {
      type: q.type || 'CHOICE',
      title: q.title || `题目 ${a.questionId}`,
      userAnswer: formatUser(a, q),
      correctAnswer,
      analysis: q.analysis || '',
      aiCorrection: a.aiCorrection || '',
      score: a.score,
      isCorrect: a.isCorrect,
      isCode,
      isJudgeInfo: q.type === 'JUDGE' || q.type === 'CHOICE',
    }
  })
})

// 选择题把内容补成 "A. xxx"，和正确答案格式一致
function formatUser(a, q) {
  const raw = a.userAnswer
  if (!raw || q.type !== 'CHOICE') return raw || ''
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

const correctCount = computed(() => items.value.filter((i) => i.isCorrect === 1).length)

const usedMinutes = computed(() => {
  const r = record.value
  if (!r?.startTime || !r?.endTime) return '-'
  const s = new Date(String(r.startTime).replace(/-/g, '/')).getTime()
  const e = new Date(String(r.endTime).replace(/-/g, '/')).getTime()
  if (!s || !e) return '-'
  if (e <= s) return '<1 分钟'
  return `${Math.max(1, Math.round((e - s) / 60000))} 分钟`
})

onMounted(async () => {
  loading.value = true
  try { record.value = await getExamRecordById(Number(route.params.id)); } finally { loading.value = false }
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';
@import '@/assets/styles/mixins';

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .head-left {
    display: flex;
    align-items: center;
    gap: 12px;
    h2 { margin: 0; }
  }
  .head-score {
    .score { font-size: 26px; font-weight: 600; color: $text-primary; }
    .score-total { font-size: 13px; color: $text-muted; margin-left: 2px; }
  }
}

.meta-desc { margin-bottom: 16px; }

.summary-card {
  @include glass;
  border-radius: $radius-lg;
  padding: 16px 18px;
  margin-bottom: 16px;
  .summary-head {
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 8px;
    .el-icon { color: $primary; }
  }
  .summary-text { font-size: 13.5px; line-height: 1.8; color: $text-secondary; white-space: pre-line; margin: 0; }
}

.answers {
  .answers-title {
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 12px;
    display: flex;
    align-items: baseline;
    gap: 10px;
    .answers-sub { font-size: 12.5px; font-weight: 400; color: $text-muted; }
  }
}

.answer-card {
  @include glass;
  border-radius: $radius-lg;
  padding: 14px 18px;
  margin-bottom: 10px;

  .a-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    .a-index {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: $bg-soft;
      color: $text-secondary;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 11.5px;
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

  .a-title { font-size: 14px; color: $text-primary; line-height: 1.7; margin-bottom: 10px; }

  .a-row {
    display: flex;
    gap: 10px;
    font-size: 13px;
    line-height: 1.7;
    padding: 2px 0;
    .a-label { flex-shrink: 0; width: 64px; color: $text-muted; font-size: 12.5px; }
    .a-value { color: $text-primary; word-break: break-word; }
    .a-value.is-empty { color: $text-muted; }
    .a-value.is-correct { color: #067647; }
    .a-value.is-muted { color: $text-secondary; }
  }

  .a-code {
    flex: 1;
    margin: 0;
    padding: 10px 12px;
    background: #fbfbfd;
    border: 1px solid $border;
    border-radius: $radius-sm;
    font-family: $font-mono;
    font-size: 12.5px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 320px;
    overflow: auto;
  }

  .a-feedback {
    display: flex;
    gap: 7px;
    margin-top: 10px;
    padding: 8px 12px;
    background: $bg-soft;
    border-radius: $radius-sm;
    font-size: 12.5px;
    color: $text-secondary;
    line-height: 1.65;
    .el-icon { color: $text-muted; flex-shrink: 0; margin-top: 2px; }
  }
}

.warn { color: #b54708; font-weight: 600; }
</style>
