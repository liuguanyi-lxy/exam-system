<template>
  <div class="practice-panel">
    <!-- 轮次信息 -->
    <div v-if="paper" class="paper-head">
      <div class="round-info">
        <span class="round-label">第 {{ paper.roundNo }} 轮</span>
        <span class="q-count">共 {{ questions.length }} 题</span>
        <el-tag v-if="step === 'retest'" size="small" type="danger" effect="light">复测成绩计入任务判定</el-tag>
      </div>
      <el-button v-if="result" size="small" :loading="loadingPaper" @click="load">
        <el-icon style="margin-right:4px"><RefreshRight /></el-icon>再练一轮
      </el-button>
    </div>

    <!-- 变式题池不足回退提示 -->
    <el-alert
      v-if="paper?.insufficient"
      title="题库变式题不足，本轮已回退全量练习"
      type="warning"
      show-icon
      :closable="false"
      class="insufficient-alert"
    />

    <div v-loading="loadingPaper" class="paper-body">
      <el-empty v-if="!loadingPaper && paper && !questions.length" description="本轮暂无可练习的题目">
        <el-button @click="load">重新取题</el-button>
      </el-empty>

      <template v-if="paper && questions.length">
        <!-- 本轮统计（提交后展示） -->
        <div v-if="result" class="round-result slide-up">
          <div class="rr-stats">
            <div class="stat">
              <span class="value">{{ result.round?.correctCount ?? 0 }}</span>
              <span class="label">答对题数</span>
            </div>
            <div class="stat">
              <span class="value">{{ result.round?.totalCount ?? questions.length }}</span>
              <span class="label">总题数</span>
            </div>
            <div class="stat">
              <span class="value">{{ result.round?.accuracy ?? 0 }}%</span>
              <span class="label">正确率</span>
            </div>
          </div>
          <p v-if="result.taskCompleted" class="rr-done">
            <el-icon><CircleCheckFilled /></el-icon>复测达标，补课任务已完成
          </p>
        </div>

        <!-- 题目卡片 -->
        <div v-for="(q, i) in questions" :key="q.id" class="question-card">
          <div class="q-header">
            <span class="q-num">{{ i + 1 }}</span>
            <el-tag size="small" effect="light">{{ typeLabel(q.type) }}</el-tag>
            <el-tag v-if="q.type === 'CHOICE' && q.multi" size="small" type="info" effect="plain">多选</el-tag>
            <el-tag
              v-if="verdictOf(q)"
              size="small"
              class="q-verdict"
              :type="verdictType(verdictOf(q))"
              effect="light"
            >{{ verdictLabel(verdictOf(q)) }}</el-tag>
          </div>
          <div class="q-title">{{ q.title }}</div>

          <!-- 选择题：单选/多选，口径与考试作答一致（提交选项内容） -->
          <template v-if="q.type === 'CHOICE'">
            <el-radio-group v-model="answers[q.id]" class="choices-group" :disabled="submitted">
              <div
                v-for="(ch, ci) in orderedChoices(q)"
                :key="ch.sort ?? ci"
                class="choice-item"
                :class="{ selected: isChoiceSelected(q, ch.content) }"
              >
                <el-checkbox
                  v-if="q.multi"
                  :model-value="isChoiceSelected(q, ch.content)"
                  :disabled="submitted"
                  @change="(v) => toggleMultiChoice(q, ch.content, v)"
                  :label="`${letterOf(ci)}. ${ch.content}`"
                />
                <el-radio v-else :value="ch.content">{{ letterOf(ci) }}. {{ ch.content }}</el-radio>
              </div>
            </el-radio-group>
          </template>

          <!-- 判断题 -->
          <template v-if="q.type === 'JUDGE'">
            <el-radio-group v-model="answers[q.id]" class="judge-group" :disabled="submitted">
              <el-radio value="正确" class="judge-radio"><span class="judge-true">✓ 正确</span></el-radio>
              <el-radio value="错误" class="judge-radio"><span class="judge-false">✗ 错误</span></el-radio>
            </el-radio-group>
          </template>

          <!-- 简答题 -->
          <template v-if="q.type === 'TEXT'">
            <el-input v-model="answers[q.id]" type="textarea" :rows="4" placeholder="请输入你的答案" :disabled="submitted" />
          </template>

          <!-- 手写代码题 -->
          <template v-if="q.type === 'CODE'">
            <div class="code-hint">可用伪代码作答，注意体现数据结构、关键步骤与边界处理</div>
            <el-input
              v-model="answers[q.id]"
              type="textarea"
              :rows="10"
              spellcheck="false"
              placeholder="// 在这里写代码"
              class="code-answer"
              :disabled="submitted"
              @keydown.tab.prevent="insertIndent($event, q.id)"
            />
          </template>

          <!-- AI 反馈 -->
          <div v-if="verdictOf(q) && verdictOf(q).feedback" class="q-feedback">
            <el-icon :size="14"><ChatLineRound /></el-icon>
            <span>{{ verdictOf(q).feedback }}</span>
          </div>
        </div>

        <!-- 提交栏 -->
        <div v-if="!result" class="submit-bar">
          <el-button type="primary" class="btn-gradient" :loading="submitting" @click="handleSubmit">
            提交本轮作答
          </el-button>
          <span class="answered-hint">已答 {{ answeredCount }} / {{ questions.length }} 题</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRemediationPaper, submitRemediationStep } from '@/api/remediation'
import { questionTypeMap } from '@/utils/format'

const props = defineProps({
  taskId: { type: Number, required: true },
  step: { type: String, required: true }, // practice | variation | retest
})
const emit = defineEmits(['task-completed'])

const paper = ref(null)
const loadingPaper = ref(false)
const submitting = ref(false)
const result = ref(null)
const answers = reactive({})

const questions = computed(() => paper.value?.questions || [])
const submitted = computed(() => !!result.value)
const answeredCount = computed(
  () => questions.value.filter((q) => answers[q.id] && String(answers[q.id]).trim()).length,
)

function typeLabel(type) {
  return questionTypeMap[type] || '题目'
}

function orderedChoices(q) {
  return (q.choices || []).slice().sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
}

function letterOf(index) {
  return String.fromCharCode(65 + index)
}

function isChoiceSelected(q, content) {
  const ans = answers[q.id]
  if (!ans) return false
  if (q.multi) return String(ans).split(',').includes(content)
  return ans === content
}

function toggleMultiChoice(q, content, checked) {
  const current = String(answers[q.id] || '').split(',').filter(Boolean)
  if (checked) {
    current.push(content)
  } else {
    const idx = current.indexOf(content)
    if (idx > -1) current.splice(idx, 1)
  }
  answers[q.id] = current.join(',')
}

// 代码题里按 Tab 插入两个空格，而不是把焦点移走
function insertIndent(e, qid) {
  const el = e.target
  const val = answers[qid] || ''
  const start = el.selectionStart ?? val.length
  const end = el.selectionEnd ?? start
  answers[qid] = val.slice(0, start) + '  ' + val.slice(end)
  setTimeout(() => {
    el.selectionStart = el.selectionEnd = start + 2
  }, 0)
}

async function load() {
  loadingPaper.value = true
  try {
    paper.value = await getRemediationPaper(props.taskId, props.step)
    // 新一轮：清空作答与上一轮结果
    Object.keys(answers).forEach((key) => delete answers[key])
    result.value = null
  } catch {
    // request.js 已统一提示错误（含视频门禁拦截），保持空态可重试
  } finally {
    loadingPaper.value = false
  }
}

async function handleSubmit() {
  const unanswered = questions.value.length - answeredCount.value
  if (unanswered > 0) {
    const confirmed = await ElMessageBox.confirm(
      `还有 ${unanswered} 题未作答，未作答将按错误处理，确认提交？`,
      '提交确认',
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '继续作答' },
    )
      .then(() => true)
      .catch(() => false)
    if (!confirmed) return
  }
  submitting.value = true
  try {
    const payload = questions.value.map((q) => ({ questionId: q.id, answer: answers[q.id] || '' }))
    result.value = await submitRemediationStep(props.taskId, props.step, payload)
    if (result.value?.taskCompleted) emit('task-completed', result.value)
  } catch {
    // request.js 已统一提示错误
  } finally {
    submitting.value = false
  }
}

function verdictOf(q) {
  return (result.value?.results || []).find((r) => Number(r.questionId) === Number(q.id)) || null
}

function verdictType(verdict) {
  if (verdict.correct === true) return 'success'
  if (verdict.correct === false) return 'danger'
  return 'warning'
}

function verdictLabel(verdict) {
  if (verdict.correct === true) return '答对'
  if (verdict.correct === false) return '答错'
  return '待 AI 判分'
}

onMounted(load)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.paper-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-3;
  flex-wrap: wrap;
  margin-bottom: $space-3;

  .round-info {
    display: flex;
    align-items: center;
    gap: $space-2;
    flex-wrap: wrap;
    .round-label {
      font-size: 14px;
      font-weight: 600;
      color: $text-primary;
    }
    .q-count {
      font-size: 12.5px;
      color: $text-muted;
    }
  }
}

.insufficient-alert {
  margin-bottom: $space-3;
}

.paper-body {
  min-height: 160px;
}

.round-result {
  @include glass;
  border-radius: $radius-lg;
  padding: $space-3 $space-5;
  margin-bottom: $space-4;

  .rr-stats {
    display: flex;
    gap: $space-8;
    .stat {
      display: flex;
      flex-direction: column;
      gap: 2px;
      .value {
        font-size: 20px;
        font-weight: 600;
        color: $text-primary;
        font-variant-numeric: tabular-nums;
      }
      .label {
        font-size: 12px;
        color: $text-muted;
      }
    }
  }
  .rr-done {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: $space-2;
    padding-top: $space-2;
    border-top: 1px solid $border;
    color: #067647;
    font-size: 13.5px;
    font-weight: 500;
  }
}

.question-card {
  @include glass;
  border-radius: $radius-lg;
  padding: $space-4 $space-5;
  margin-bottom: $space-3;

  .q-header {
    display: flex;
    align-items: center;
    gap: $space-2;
    margin-bottom: $space-2;
    flex-wrap: wrap;

    .q-num {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 26px;
      height: 26px;
      border-radius: 8px;
      background: $gradient-primary;
      color: #fff;
      font-weight: 700;
      font-size: 13px;
      flex-shrink: 0;
    }
    .q-verdict {
      margin-left: auto;
    }
  }
  .q-title {
    font-size: 15px;
    line-height: 1.7;
    margin-bottom: $space-3;
    font-weight: 500;
    color: $text-primary;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.choices-group {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: $space-2;

  .choice-item {
    width: 100%;
    padding: 11px 14px;
    border-radius: $radius-md;
    border: 1.5px solid $border;
    transition: all $transition-fast;
    cursor: pointer;
    background: #fff;
    text-align: left;
    :deep(.el-radio),
    :deep(.el-checkbox) {
      margin-right: 0;
      height: auto;
      line-height: 1.6;
      white-space: normal;
      display: inline-flex;
      align-items: flex-start;
      .el-radio__label,
      .el-checkbox__label {
        white-space: normal;
        text-align: left;
        line-height: 1.6;
        padding-left: 8px;
      }
    }
    &:hover {
      border-color: $primary-lighter;
      background: rgba(99, 102, 241, 0.04);
    }
    &.selected {
      border-color: $primary;
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(124, 58, 237, 0.06));
      box-shadow: 0 0 0 1px $primary-lighter inset;
    }
  }
}

.judge-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: $space-2;

  .judge-radio {
    width: 100%;
    padding: 11px 14px;
    border-radius: $radius-md;
    border: 1.5px solid $border;
    background: #fff;
    transition: all $transition-fast;
    &:has(.el-radio.is-checked) {
      border-color: $primary;
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(124, 58, 237, 0.06));
    }
    :deep(.el-radio) {
      margin-right: 0;
      height: auto;
      line-height: 1.6;
      .el-radio__label {
        text-align: left;
        padding-left: 8px;
      }
    }
    .judge-true {
      color: $success;
      font-weight: 600;
    }
    .judge-false {
      color: $danger;
      font-weight: 600;
    }
  }
}

.code-hint {
  font-size: 12.5px;
  color: $text-muted;
  margin-bottom: $space-2;
}

.code-answer {
  :deep(.el-textarea__inner) {
    font-family: $font-mono;
    font-size: 13px;
    line-height: 1.7;
    background: #fbfbfd;
  }
}

.q-feedback {
  display: flex;
  gap: 7px;
  margin-top: $space-3;
  padding: 9px 12px;
  background: $primary-soft;
  border-radius: $radius-sm;
  color: $text-secondary;
  font-size: 13px;
  line-height: 1.65;

  .el-icon {
    color: $primary;
    flex-shrink: 0;
    margin-top: 3px;
  }
}

.submit-bar {
  display: flex;
  align-items: center;
  gap: $space-3;
  margin-top: $space-4;

  .answered-hint {
    font-size: 12.5px;
    color: $text-muted;
  }
}
</style>
