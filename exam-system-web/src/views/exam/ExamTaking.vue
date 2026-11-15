<template>
  <div class="exam-taking" v-if="exam">
    <!-- 顶部栏 -->
    <div class="exam-header">
      <div class="header-left">
        <span class="exam-title">{{ exam.paper?.name || '考试中' }}</span>
        <span class="exam-student">考生：{{ examStore.currentExam?.studentName || userStore.realName || userStore.username || '-' }}</span>
      </div>
      <div class="header-right">
        <div class="exam-timer" :class="{ warning: exam.remainingSeconds < 300 }">
          <el-icon><Timer /></el-icon>
          {{ formatTime(exam.remainingSeconds) }}
        </div>
        <el-button type="danger" round @click="showSubmitDialog = true" :disabled="exam.isSubmitted">交卷</el-button>
      </div>
    </div>

    <!-- 主体 -->
    <div class="exam-body">
      <div class="exam-question-area">
        <div v-for="(q, i) in exam.paper?.questions" :key="q.id" :id="`q-${i}`" class="question-card">
          <div class="q-header">
            <span class="q-num">{{ i + 1 }}</span>
            <el-tag size="small" effect="light">{{ typeLabel(q.type) }}</el-tag>
            <el-tag size="small" effect="plain" :type="q.difficulty === 'EASY' ? 'success' : q.difficulty === 'MEDIUM' ? 'warning' : 'danger'">
              {{ q.difficulty === 'EASY' ? '简单' : q.difficulty === 'MEDIUM' ? '中等' : '困难' }}
            </el-tag>
            <span class="q-score">{{ q.paperScore || q.score }}分</span>
          </div>
          <div class="q-title">{{ q.title }}</div>

          <!-- 选择题 -->
          <template v-if="q.type === 'CHOICE'">
            <el-radio-group v-model="answers[q.id]" class="choices-group" :class="{ multi: q.multi }">
              <div v-for="(ch, ci) in q.choices" :key="ch.sort" class="choice-item" :class="{ selected: isChoiceSelected(q, ch.content) }">
                <el-checkbox v-if="q.multi" :model-value="isChoiceSelected(q, ch.content)"
                  @change="(v) => toggleMultiChoice(q, ch.content, v)" :label="`${letterOf(ci)}. ${ch.content}`" />
                <el-radio v-else :value="ch.content">{{ letterOf(ci) }}. {{ ch.content }}</el-radio>
              </div>
            </el-radio-group>
          </template>

          <!-- 判断题 -->
          <template v-if="q.type === 'JUDGE'">
            <el-radio-group v-model="answers[q.id]" class="choices-group judge-group">
              <el-radio value="正确" class="judge-radio"><span class="judge-true">✓ 正确</span></el-radio>
              <el-radio value="错误" class="judge-radio"><span class="judge-false">✗ 错误</span></el-radio>
            </el-radio-group>
          </template>

          <!-- 简答题 -->
          <template v-if="q.type === 'TEXT'">
            <el-input v-model="answers[q.id]" type="textarea" :rows="4" placeholder="请输入你的答案" class="text-answer" />
          </template>

          <!-- 手写代码题：等宽字体 + 可用 Tab 缩进，允许写伪代码 -->
          <template v-if="q.type === 'CODE'">
            <div class="code-hint">可用伪代码作答，注意体现数据结构、关键步骤与边界处理</div>
            <el-input
              v-model="answers[q.id]"
              type="textarea"
              :rows="12"
              spellcheck="false"
              placeholder="// 在这里写代码，例如：&#10;// class LRUCache { ... }"
              class="code-answer"
              @keydown.tab.prevent="insertIndent($event, q.id)"
            />
          </template>
        </div>
      </div>

      <!-- 答题卡 -->
      <div class="exam-nav">
        <h4>答题卡</h4>
        <div class="nav-grid">
          <div v-for="(q, i) in exam.paper?.questions" :key="q.id" class="nav-item"
            :class="{ answered: answers[q.id], active: currentIndex === i }"
            @click="scrollTo(i)">
            {{ i + 1 }}
          </div>
        </div>
        <div class="nav-summary">
          <div class="summary-answered">
            <span class="dot dot-answered"></span> 已答 {{ answeredCount }}
          </div>
          <div class="summary-unanswered">
            <span class="dot dot-unanswered"></span> 未答 {{ unansweredCount }}
          </div>
        </div>
      </div>
    </div>

    <!-- 交卷确认 -->
    <el-dialog v-model="showSubmitDialog" title="确认交卷" width="420px">
      <div class="submit-dialog-body">
        <el-icon :size="48" class="dialog-icon"><WarningFilled /></el-icon>
        <p>已答 <b>{{ answeredCount }}</b> 题，还有 <b class="text-danger">{{ unansweredCount }}</b> 题未答。</p>
        <p class="dialog-tip">交卷后不可修改，确认交卷？</p>
      </div>
      <template #footer>
        <el-button @click="showSubmitDialog = false">继续答题</el-button>
        <el-button type="danger" round @click="handleSubmit" :loading="submitting">确认交卷</el-button>
      </template>
    </el-dialog>
  </div>

  <!-- 刷新恢复中的过渡态 -->
  <div class="exam-restoring" v-else-if="restoring">
    <el-icon class="is-loading" :size="30"><Loading /></el-icon>
    <p>正在恢复考试进度...</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useExamStore } from '@/stores/exam'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const examStore = useExamStore()
const userStore = useUserStore()
const exam = computed(() => examStore.currentExam)
// 用 ref 持有答题对象：刷新恢复发生在 onMounted，恢复成功后重新指向 store 里的对象
const answers = ref(examStore.currentExam?.answers || {})
const restoring = ref(false)
const showSubmitDialog = ref(false)
const submitting = ref(false)
const currentIndex = ref(0)

const answeredCount = computed(() => Object.values(answers.value).filter(v => v).length)
const unansweredCount = computed(() => (exam.value?.paper?.questions?.length || 0) - answeredCount.value)

// 选项编号按展示顺序生成（A/B/C…），不依赖后端 sort 是否从 0 开始
function letterOf(index) {
  return String.fromCharCode(65 + index)
}

const TYPE_LABELS = { CHOICE: '选择题', JUDGE: '判断题', CODE: '代码题', TEXT: '简答题' }
function typeLabel(type) {
  return TYPE_LABELS[type] || '题目'
}

// 代码题里按 Tab 插入两个空格，而不是把焦点移走
function insertIndent(e, qid) {
  const el = e.target
  const val = answers.value[qid] || ''
  const start = el.selectionStart ?? val.length
  const end = el.selectionEnd ?? start
  answers.value[qid] = val.slice(0, start) + '  ' + val.slice(end)
  nextTick(() => {
    el.selectionStart = el.selectionEnd = start + 2
  })
}

function isChoiceSelected(q, content) {
  const ans = answers.value[q.id]
  if (!ans) return false
  if (q.multi) return ans.split(',').includes(content)
  return ans === content
}

function toggleMultiChoice(q, content, checked) {
  const current = (answers.value[q.id] || '').split(',').filter(Boolean)
  if (checked) { current.push(content) } else { const idx = current.indexOf(content); if (idx > -1) current.splice(idx, 1) }
  answers.value[q.id] = current.join(',')
  examStore.setAnswer(q.id, answers.value[q.id])
}

function formatTime(seconds) {
  const m = Math.floor(seconds / 60); const s = seconds % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

function scrollTo(index) {
  currentIndex.value = index
  document.getElementById(`q-${index}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function handleSubmit() {
  submitting.value = true
  try {
    await examStore.submitExam()
    ElMessage.success('交卷成功')
    router.push(`/exam/result/${exam.value.recordId}`)
  } finally { submitting.value = false }
}

// 切屏检测
function onVisibilityChange() { if (document.hidden) examStore.recordWindowSwitch() }
function onBlur() { examStore.recordWindowSwitch() }

onMounted(async () => {
  // 刷新恢复：store 里有进行中的考试则直接用，否则尝试从 sessionStorage 恢复
  if (!exam.value) {
    restoring.value = true
    const ok = await examStore.tryRestore()
    restoring.value = false
    if (!ok) { router.push('/home'); return }
    answers.value = examStore.currentExam.answers
  }
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('blur', onBlur)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('blur', onBlur)
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.exam-taking {
  position: fixed;
  inset: 0;
  background: $gradient-bg;
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

// 刷新恢复过渡态
.exam-restoring {
  position: fixed;
  inset: 0;
  background: $gradient-bg;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: $text-secondary;
  font-size: 14px;
  p { margin: 0; }
}

// 顶部栏
.exam-header {
  height: 56px;
  background: #101828;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  gap: 24px;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);

  .header-left {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;
    .exam-title { font-weight: 600; font-size: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .exam-student { font-size: 12.5px; color: rgba(255,255,255,.55); white-space: nowrap; }
  }
  .header-right {
    display: flex;
    align-items: center;
    gap: 14px;
    .exam-timer {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 20px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      font-family: $font-mono;
      padding: 5px 14px;
      border-radius: $radius-md;
      background: rgba(255, 255, 255, 0.08);
      &.warning {
        color: #fda29b;
        background: rgba(240, 68, 56, 0.16);
      }
    }
  }
}

@keyframes pulse-warning {
  0%, 100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5); }
  50% { transform: scale(1.05); box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
}

// 主体
.exam-body { flex: 1; display: flex; overflow: hidden; }

.exam-question-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  scroll-behavior: smooth;
}

// 题目卡片
.question-card {
  @include glass;
  border-radius: 12px;
  padding: 22px 24px;
  margin-bottom: 16px;
  scroll-margin-top: 16px;
  transition: box-shadow $transition-normal;

  .q-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 14px;
    .q-num {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: 9px;
      background: $gradient-primary;
      color: #fff;
      font-weight: 700;
      font-size: 14px;
      flex-shrink: 0;
    }
    .q-score { color: $text-muted; font-size: 12px; margin-left: auto; font-weight: 600; }
  }
  .q-title { font-size: 15px; line-height: 1.7; margin-bottom: 16px; font-weight: 500; }

  .choices-group {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .choice-item {
    width: 100%;
    padding: 12px 16px;
    border-radius: 10px;
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
      background: linear-gradient(135deg, rgba(99,102,241,.08), rgba(124,58,237,.06));
      box-shadow: 0 0 0 1px $primary-light inset;
    }
  }
  .judge-group {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    width: 100%;
    .judge-radio {
      width: 100%;
      padding: 12px 16px;
      border-radius: 10px;
      border: 1.5px solid $border;
      background: #fff;
      transition: all $transition-fast;
      &:has(.el-radio.is-checked) {
        border-color: $primary;
        background: linear-gradient(135deg, rgba(99,102,241,.08), rgba(124,58,237,.06));
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
      .judge-true { color: $success; font-weight: 600; }
      .judge-false { color: $danger; font-weight: 600; }
    }
  }
  .text-answer {
    :deep(.el-textarea__inner) {
      border-radius: 10px;
      font-size: 14px;
      line-height: 1.7;
    }
  }
}

// 答题卡
.exam-nav {
  width: 220px;
  @include glass;
  border-radius: 0;
  border-top: none;
  border-bottom: none;
  border-right: none;
  padding: 20px;
  overflow-y: auto;
  flex-shrink: 0;

  h4 {
    margin-bottom: 14px;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }
  .nav-grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 8px;
  }
  .nav-item {
    height: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: $radius-sm;
    background: $surface;
    border: 1px solid $border;
    color: $text-secondary;
    font-size: 12px;
    cursor: pointer;
    transition: all $transition-fast;
    font-weight: 500;
    &:hover { border-color: $primary-lighter; color: $primary; }
    &.answered {
      background: $primary;
      border-color: $primary;
      color: #fff;
    }
    &.active {
      outline: 2px solid $primary;
      outline-offset: 2px;
    }
  }
  .nav-summary {
    margin-top: 18px;
    font-size: 12.5px;
    color: $text-secondary;
    display: flex;
    flex-direction: column;
    gap: 7px;
    padding-top: 14px;
    border-top: 1px solid $border;
    .summary-answered, .summary-unanswered {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .dot {
      width: 10px;
      height: 10px;
      border-radius: 3px;
      &.dot-answered { background: $primary; }
      &.dot-unanswered { background: $surface; border: 1px solid $border-strong; }
    }
  }
}

// 交卷对话框
.submit-dialog-body {
  text-align: center;
  padding: 8px 0;
  .dialog-icon { color: $warning; margin-bottom: 12px; }
  p { font-size: 15px; margin-bottom: 8px; }
  .dialog-tip { color: $text-muted; font-size: 13px; }
}
</style>
