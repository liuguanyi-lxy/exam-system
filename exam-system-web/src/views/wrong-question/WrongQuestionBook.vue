<template>
  <div class="page-container wrong-book">
    <div class="page-header">
      <h2>错题本</h2>
      <span class="header-count" v-if="!loading && total > 0">共 {{ total }} 题</span>
    </div>

    <el-tabs v-model="activeTab" class="status-tabs">
      <el-tab-pane name="pending">
        <template #label>
          <span class="tab-label">
            待复习
            <span v-if="pendingCount !== null" class="tab-count">{{ pendingCount }}</span>
          </span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="mastered">
        <template #label>
          <span class="tab-label">
            已掌握
            <span v-if="masteredCount !== null" class="tab-count">{{ masteredCount }}</span>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <div class="search-bar">
      <el-select v-model="filters.type" placeholder="题型" clearable style="width:140px" @change="search">
        <el-option label="选择题" value="CHOICE" />
        <el-option label="判断题" value="JUDGE" />
        <el-option label="简答题" value="TEXT" />
        <el-option label="手写代码题" value="CODE" />
      </el-select>
      <el-select v-model="filters.difficulty" placeholder="难度" clearable style="width:120px" @change="search">
        <el-option label="简单" value="EASY" />
        <el-option label="中等" value="MEDIUM" />
        <el-option label="困难" value="HARD" />
      </el-select>
      <el-select v-model="filters.categoryId" placeholder="分类" clearable filterable style="width:180px" @change="search">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button @click="resetFilters">
        <el-icon style="margin-right:4px"><RefreshLeft /></el-icon>重置
      </el-button>
      <span class="search-hint">
        {{ activeTab === 'pending' ? '按最近一次答错时间倒序，仅统计完全答错的记录' : '标记为已掌握后可随时恢复；再次答错会自动回到待复习' }}
      </span>
    </div>

    <div class="list-wrap" v-loading="loading">
      <el-empty v-if="!loading && !list.length" :description="emptyDescription">
        <el-button v-if="hasFilters" @click="resetFilters">清空筛选</el-button>
        <el-button v-else-if="activeTab === 'pending'" class="btn-gradient" @click="$router.push('/exam/list')">
          去参加考试
        </el-button>
        <el-button v-else @click="activeTab = 'pending'">去复习错题</el-button>
      </el-empty>

      <div v-else class="wrong-list">
        <article
          v-for="(item, index) in list"
          :key="item.questionId"
          class="wrong-card"
          :class="{ 'is-mastered': item.mastered }"
        >
          <div class="card-head">
            <div class="head-main">
              <span class="serial">{{ (page - 1) * size + index + 1 }}</span>
              <el-tag size="small" effect="light">{{ typeLabel(item.question.type) }}</el-tag>
              <el-tag v-if="item.question.type === 'CHOICE' && item.question.multi" size="small" type="info" effect="plain">
                多选
              </el-tag>
              <el-tag size="small" :type="difficultyColor(item.question.difficulty)" effect="light">
                {{ difficultyLabel(item.question.difficulty) }}
              </el-tag>
              <el-tag v-if="item.question.categoryName" size="small" type="info" effect="plain">
                {{ item.question.categoryName }}
              </el-tag>
              <el-tag v-if="item.mastered" size="small" type="success" effect="light">
                <el-icon style="margin-right:3px"><CircleCheck /></el-icon>已掌握
              </el-tag>
            </div>
            <div class="head-meta">
              <span class="wrong-count">错 {{ item.wrongCount || 0 }} 次</span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon>{{ item.lastWrongTime || '-' }}
              </span>
            </div>
          </div>

          <h3 class="question-title" :class="{ 'is-code': item.question.type === 'CODE' }">
            {{ item.question.title || '题目内容已删除' }}
          </h3>

          <div
            v-if="item.question.type === 'CHOICE' && orderedChoices(item.question).length"
            class="choice-list"
          >
            <div
              v-for="(choice, ci) in orderedChoices(item.question)"
              :key="choice.id || ci"
              class="choice-item"
              :class="{
                'is-correct': choice.isCorrect,
                'is-picked': isPicked(item, ci),
              }"
            >
              <span class="choice-label">{{ choiceLabel(ci) }}</span>
              <span class="choice-content">{{ choice.content }}</span>
              <span v-if="choice.isCorrect" class="choice-flag correct">正确选项</span>
              <span
                v-if="isPicked(item, ci)"
                class="choice-flag"
                :class="choice.isCorrect ? 'picked-correct' : 'picked'"
              >
                你的选择
              </span>
            </div>
          </div>

          <div class="answer-grid">
            <div class="answer-block wrong">
              <span class="block-label">我的错误答案</span>
              <pre class="answer-text" :class="{ 'is-code': item.question.type === 'CODE' }">{{
                formatUserAnswer(item) || '未作答'
              }}</pre>
            </div>
            <div class="answer-block correct">
              <span class="block-label">正确答案</span>
              <pre class="answer-text" :class="{ 'is-code': item.question.type === 'CODE' }">{{
                correctAnswerText(item.question)
              }}</pre>
            </div>
          </div>

          <div v-if="item.question.analysis" class="analysis-block">
            <div class="block-title">
              <el-icon><Document /></el-icon>题目解析
            </div>
            <p>{{ item.question.analysis }}</p>
          </div>

          <div v-if="item.aiCorrection && showAiCorrection(item)" class="ai-block">
            <el-icon><ChatLineRound /></el-icon>
            <div>
              <span class="ai-label">AI 评语</span>{{ item.aiCorrection }}
            </div>
          </div>

          <div class="card-foot">
            <span class="foot-meta">
              <template v-if="item.lastPaperName">来自《{{ item.lastPaperName }}》</template>
              <template v-else>最近一次答错记录</template>
            </span>
            <div class="foot-actions">
              <el-button
                v-if="item.lastExamRecordId"
                size="small"
                @click="$router.push(`/exam/result/${item.lastExamRecordId}`)"
              >
                查看整卷解析
              </el-button>
              <el-button
                v-if="!item.mastered"
                size="small"
                type="success"
                plain
                :loading="actionLoading === item.questionId"
                @click="handleMarkMastered(item)"
              >
                <el-icon style="margin-right:4px"><CircleCheck /></el-icon>标记已掌握
              </el-button>
              <el-button
                v-else
                size="small"
                plain
                :loading="actionLoading === item.questionId"
                @click="handleRestore(item)"
              >
                <el-icon style="margin-right:4px"><RefreshLeft /></el-icon>恢复
              </el-button>
            </div>
          </div>
        </article>
      </div>
    </div>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[5, 10, 20, 50]"
      layout="total,sizes,prev,pager,next"
      @current-change="loadData"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWrongQuestionList, markMastered, unmarkMastered } from '@/api/wrongQuestion'
import { getCategories } from '@/api/category'
import { difficultyColors, difficultyMap, questionTypeMap } from '@/utils/format'

const loading = ref(false)
const activeTab = ref('pending')
const categories = ref([])
const actionLoading = ref(null)

const filters = reactive({ type: '', difficulty: '', categoryId: null })

// 两个 Tab 各自维护分页状态：页码/每页条数互不干扰，
// 已掌握 Tab 通过 masteredOnly=true 走服务端分页，不再本地过滤。
const pendingPage = ref(1)
const pendingSize = ref(10)
const masteredPage = ref(1)
const masteredSize = ref(10)

const pendingState = reactive({ list: [], total: 0 })
const masteredState = reactive({ list: [], total: 0 })
// 计数单独标记是否已加载：另一 Tab 的计数在标记/取消掌握或改筛选后失效，切换过去时重新拉取
const pendingLoaded = ref(false)
const masteredLoaded = ref(false)

// 当前 Tab 的分页器与结果（page/size 为可写 computed，供 el-pagination 双向绑定）
const currentTabState = computed(() =>
  activeTab.value === 'pending' ? pendingState : masteredState,
)
const page = computed({
  get: () => (activeTab.value === 'pending' ? pendingPage.value : masteredPage.value),
  set: (value) => {
    if (activeTab.value === 'pending') pendingPage.value = value
    else masteredPage.value = value
  },
})
const size = computed({
  get: () => (activeTab.value === 'pending' ? pendingSize.value : masteredSize.value),
  set: (value) => {
    if (activeTab.value === 'pending') pendingSize.value = value
    else masteredSize.value = value
  },
})
const list = computed(() => currentTabState.value.list)
const total = computed(() => currentTabState.value.total)
const pendingCount = computed(() => (pendingLoaded.value ? pendingState.total : null))
const masteredCount = computed(() => (masteredLoaded.value ? masteredState.total : null))

const filterParams = computed(() => {
  const params = {}
  if (filters.type) params.type = filters.type
  if (filters.difficulty) params.difficulty = filters.difficulty
  if (filters.categoryId) params.categoryId = filters.categoryId
  return params
})

const hasFilters = computed(() => Object.keys(filterParams.value).length > 0)
const emptyDescription = computed(() => {
  if (activeTab.value === 'mastered') {
    return hasFilters.value ? '没有符合条件的已掌握题目' : '还没有标记为已掌握的题目'
  }
  return hasFilters.value ? '没有符合条件的错题' : '太棒了，当前没有待复习的错题'
})

function withQuestion(record) {
  return { ...record, question: record.question || {} }
}

function typeLabel(type) {
  return questionTypeMap[type] || '简答题'
}

function difficultyLabel(difficulty) {
  return difficultyMap[difficulty] || '中等'
}

function difficultyColor(difficulty) {
  return difficultyColors[difficulty] || 'info'
}

// 客观题（选择题/判断题）的 aiCorrection 只是"回答错误"这类噪音，仅主观题展示 AI 评语
function showAiCorrection(item) {
  return item?.question?.type === 'TEXT' || item?.question?.type === 'CODE'
}

function orderedChoices(question) {
  return (question?.choices || []).slice().sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
}

function choiceLabel(index) {
  return String.fromCharCode(65 + index)
}

// 错题答案可能是选项字母（A/B）、选项内容，或逗号分隔的多选答案
function parseChoiceTokens(raw, choices) {
  if (raw === null || raw === undefined) return []
  const text = String(raw).trim()
  if (!text) return []

  const exact = choices.find((c) => String(c.content || '').trim() === text)
  if (exact) return [text]

  if (text.startsWith('[')) {
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) {
        return parsed.map((v) => String(v).trim()).filter(Boolean)
      }
    } catch {
      // 非 JSON，按普通答案处理
    }
  }

  return text
    .split(/[,，;；]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function choiceIndexOf(token, choices) {
  const text = String(token || '')
    .trim()
    .replace(/^([A-Za-z])[.、:：)）]\s*/, '$1')
  if (!text) return -1
  if (/^[A-Za-z]$/.test(text)) return text.toUpperCase().charCodeAt(0) - 65
  return choices.findIndex((c) => String(c.content || '').trim() === text)
}

function isPicked(item, index) {
  const choices = orderedChoices(item.question)
  return parseChoiceTokens(item.lastWrongAnswer, choices).some(
    (token) => choiceIndexOf(token, choices) === index,
  )
}

function formatChoiceTokens(raw, choices) {
  return parseChoiceTokens(raw, choices)
    .map((token) => {
      const index = choiceIndexOf(token, choices)
      if (index >= 0 && choices[index]) {
        return `${choiceLabel(index)}. ${choices[index].content}`
      }
      return token
    })
    .join('　')
}

function formatJudgeAnswer(raw) {
  const text = String(raw ?? '').trim()
  if (!text) return ''
  const upper = text.toUpperCase()
  if (['TRUE', 'T', '1', 'YES', 'Y', '正确', '对'].includes(upper)) return '正确'
  if (['FALSE', 'F', '0', 'NO', 'N', '错误', '错'].includes(upper)) return '错误'
  return text
}

function formatUserAnswer(item) {
  const question = item.question
  const raw = item.lastWrongAnswer
  if (raw === null || raw === undefined || String(raw).trim() === '') return ''
  if (question.type === 'CHOICE') {
    return formatChoiceTokens(raw, orderedChoices(question))
  }
  if (question.type === 'JUDGE') {
    return formatJudgeAnswer(raw)
  }
  return String(raw)
}

function correctAnswerText(question) {
  if (question?.type === 'CHOICE') {
    const choices = orderedChoices(question)
    const correct = choices
      .map((choice, index) => (choice.isCorrect ? `${choiceLabel(index)}. ${choice.content}` : null))
      .filter(Boolean)
    if (correct.length) return correct.join('　')
    return formatChoiceTokens(question.answer?.answer, choices) || '暂无正确答案'
  }
  if (question?.type === 'JUDGE') {
    return formatJudgeAnswer(question.answer?.answer) || '暂无正确答案'
  }
  return question?.answer?.answer || '暂无参考答案'
}

function pagerFor(tab) {
  return tab === 'pending'
    ? { page: pendingPage, size: pendingSize }
    : { page: masteredPage, size: masteredSize }
}

// 单页拉取：已掌握 Tab 带 masteredOnly=true，由后端只返回已掌握的题
async function fetchTabPage(tab) {
  const pager = pagerFor(tab)
  const params = { ...filterParams.value, page: pager.page.value, size: pager.size.value }
  if (tab === 'mastered') params.masteredOnly = true

  let res = await getWrongQuestionList(params)
  let records = res?.records || []
  let totalCount = res?.total || 0

  // 当前页因标记/取消掌握变空时，回到第一页避免停留在空白页
  if (!records.length && pager.page.value > 1 && totalCount > 0) {
    pager.page.value = 1
    res = await getWrongQuestionList({ ...params, page: 1 })
    records = res?.records || []
    totalCount = res?.total || 0
  }
  return { records: records.map(withQuestion), total: totalCount }
}

let loadSeq = 0
async function loadData() {
  const tab = activeTab.value
  const seq = ++loadSeq
  loading.value = true
  try {
    const data = await fetchTabPage(tab)
    if (seq !== loadSeq) return
    const state = tab === 'pending' ? pendingState : masteredState
    state.list = data.records
    state.total = data.total
    if (tab === 'pending') pendingLoaded.value = true
    else masteredLoaded.value = true
  } catch {
    // request.js 已统一提示错误，这里保持原列表，避免刷新失败清空页面
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function search() {
  // 筛选变化后当前 Tab 回到第一页；另一 Tab 的列表/计数已失效，切换过去时重新拉取
  page.value = 1
  if (activeTab.value === 'pending') {
    masteredLoaded.value = false
    masteredState.list = []
    masteredState.total = 0
  } else {
    pendingLoaded.value = false
    pendingState.list = []
    pendingState.total = 0
  }
  loadData()
}

function resetFilters() {
  filters.type = ''
  filters.difficulty = ''
  filters.categoryId = null
  search()
}

function handleSizeChange() {
  page.value = 1
  loadData()
}

async function handleMarkMastered(item) {
  const confirmed = await ElMessageBox.confirm(
    `确定已掌握《${item.question.title || '该题'}》吗？标记后将移出错题本，再次答错会自动恢复。`,
    '标记已掌握',
    { type: 'success', confirmButtonText: '标记已掌握', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  actionLoading.value = item.questionId
  try {
    await markMastered(item.questionId)
    ElMessage.success('已标记为已掌握')
    // 已掌握 Tab 的列表/计数已变化，切换过去时重新拉取
    masteredLoaded.value = false
    masteredState.list = []
    masteredState.total = 0
    await loadData()
  } catch {
    // request.js 已统一提示
  } finally {
    actionLoading.value = null
  }
}

async function handleRestore(item) {
  const confirmed = await ElMessageBox.confirm(
    `将《${item.question.title || '该题'}》恢复到错题本？`,
    '恢复错题',
    { type: 'warning', confirmButtonText: '恢复', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  actionLoading.value = item.questionId
  try {
    await unmarkMastered(item.questionId)
    ElMessage.success('已恢复到错题本')
    // 待复习 Tab 的列表/计数已变化，切换过去时重新拉取
    pendingLoaded.value = false
    pendingState.list = []
    pendingState.total = 0
    await loadData()
  } catch {
    // request.js 已统一提示
  } finally {
    actionLoading.value = null
  }
}

watch(activeTab, () => {
  // activeTab 已完成切换，这里的赋值落到目标 Tab 的页码上（各 Tab 独立维护分页状态）
  page.value = 1
  loadData()
})

onMounted(async () => {
  try {
    categories.value = (await getCategories()) || []
  } catch {
    categories.value = []
  }
  loadData()
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.header-count {
  font-size: 13px;
  color: $text-muted;
}

.status-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: $space-4;
  }
  .tab-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .tab-count {
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: 9px;
    background: $bg-soft;
    color: $text-muted;
    font-size: 11px;
    line-height: 18px;
    text-align: center;
    font-weight: 600;
  }
}

.search-hint {
  font-size: 12.5px;
  color: $text-muted;
  margin-left: auto;
}

.list-wrap {
  min-height: 180px;
}

.wrong-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.wrong-card {
  @include glass;
  border-radius: $radius-lg;
  padding: 18px 20px;
  border-left: 3px solid $danger;
  transition: border-color $transition-normal, box-shadow $transition-normal;

  &:hover {
    box-shadow: $shadow-md;
  }
  &.is-mastered {
    border-left-color: $success;
  }
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.head-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.serial {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: $bg-soft;
  color: $text-secondary;
  font-size: 12px;
  font-weight: 600;
}

.head-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: $text-muted;

  .meta-item {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }
  .wrong-count {
    color: $danger;
    font-weight: 600;
  }
}

.question-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.7;
  color: $text-primary;
  margin-bottom: 12px;
  white-space: pre-wrap;
  word-break: break-word;

  &.is-code {
    font-family: $font-mono;
    font-size: 13px;
    background: #fcfcfd;
    border: 1px solid $border;
    border-radius: $radius-sm;
    padding: 10px 12px;
  }
}

.choice-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-bottom: 14px;
}

.choice-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 8px 12px;
  border: 1px solid $border;
  border-radius: $radius-sm;
  background: #fcfcfd;
  font-size: 13.5px;
  line-height: 1.6;
  transition: background $transition-fast, border-color $transition-fast;

  &.is-correct {
    background: #ecfdf3;
    border-color: #a6f4c5;
  }
  &.is-picked:not(.is-correct) {
    background: #fef3f2;
    border-color: #fecdca;
  }

  .choice-label {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    margin-top: 1px;
    border-radius: 50%;
    background: #fff;
    border: 1px solid $border-strong;
    color: $text-secondary;
    font-size: 11.5px;
    font-weight: 600;
  }
  &.is-correct .choice-label {
    background: $success;
    border-color: $success;
    color: #fff;
  }
  &.is-picked:not(.is-correct) .choice-label {
    background: $danger;
    border-color: $danger;
    color: #fff;
  }

  .choice-content {
    flex: 1;
    color: $text-primary;
    word-break: break-word;
  }
  .choice-flag {
    flex-shrink: 0;
    font-size: 11.5px;
    line-height: 20px;
    &.correct {
      color: #067647;
    }
    &.picked {
      color: $danger;
    }
    &.picked-correct {
      color: $primary;
    }
  }
}

.answer-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 12px;
}

.answer-block {
  border: 1px solid $border;
  border-radius: $radius-sm;
  padding: 10px 12px;
  background: $bg-soft;

  &.wrong {
    background: #fef3f2;
    border-color: #fecdca;
  }
  &.correct {
    background: #ecfdf3;
    border-color: #a6f4c5;
  }

  .block-label {
    display: block;
    font-size: 12px;
    margin-bottom: 6px;
    color: $text-muted;
  }
  &.wrong .block-label {
    color: #b42318;
  }
  &.correct .block-label {
    color: #067647;
  }
}

.answer-text {
  margin: 0;
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.7;
  color: $text-primary;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow: auto;

  &.is-code {
    font-family: $font-mono;
    font-size: 12.5px;
    background: #fff;
    border: 1px solid $border;
    border-radius: $radius-sm;
    padding: 8px 10px;
  }
}

.analysis-block {
  padding: 10px 12px;
  margin-bottom: 10px;
  background: #fcfcfd;
  border: 1px solid $border;
  border-radius: $radius-sm;

  .block-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 6px;
    font-size: 12.5px;
    color: $text-muted;
  }
  p {
    font-size: 13.5px;
    line-height: 1.75;
    color: $text-secondary;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.ai-block {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: $primary-soft;
  border-radius: $radius-sm;
  font-size: 13px;
  line-height: 1.65;
  color: $text-secondary;

  .el-icon {
    flex-shrink: 0;
    margin-top: 3px;
    color: $primary;
  }
  .ai-label {
    margin-right: 6px;
    color: $primary;
    font-weight: 600;
  }
}

.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid $border;
}

.foot-meta {
  font-size: 12.5px;
  color: $text-muted;
}

.foot-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 640px) {
  .answer-grid {
    grid-template-columns: 1fr;
  }
  .search-hint {
    margin-left: 0;
    width: 100%;
  }
  .card-head {
    flex-direction: column;
  }
}
</style>
