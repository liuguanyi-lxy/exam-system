<template>
  <div class="page-container favorites-page">
    <div class="page-header">
      <h2>我的收藏</h2>
      <span class="header-count" v-if="!loading && total > 0">共 {{ total }} 题</span>
    </div>

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
      <span class="search-hint">按收藏时间倒序，收藏列表不展示答案与解析</span>
    </div>

    <div class="list-wrap" v-loading="loading">
      <el-empty v-if="!loading && !list.length" :description="emptyDescription">
        <el-button v-if="hasFilters" @click="resetFilters">清空筛选</el-button>
        <el-button v-else @click="$router.push('/wrong-questions')">去错题本复习</el-button>
      </el-empty>

      <div v-else class="favorite-list">
        <article v-for="(item, index) in list" :key="item.questionId" class="favorite-card">
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
            </div>
            <span class="favorite-time">
              <el-icon><Clock /></el-icon>{{ item.favoriteTime || '-' }}
            </span>
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
            >
              <span class="choice-label">{{ choiceLabel(ci) }}</span>
              <span class="choice-content">{{ choice.content }}</span>
            </div>
          </div>

          <div class="card-foot">
            <span class="foot-meta">
              <el-icon><InfoFilled /></el-icon>
              收藏题目仅展示题干与选项，答案与解析不在此处展示
            </span>
            <div class="foot-actions">
              <el-button size="small" @click="$router.push(`/questions/${item.questionId}`)">
                <el-icon style="margin-right:4px"><View /></el-icon>查看题目
              </el-button>
              <el-button
                size="small"
                type="danger"
                plain
                :loading="cancelingId === item.questionId"
                @click="handleRemove(item)"
              >
                <el-icon style="margin-right:4px"><StarFilled /></el-icon>取消收藏
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
      @change="loadData"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFavoriteList, removeFavorite } from '@/api/favorite'
import { getCategories } from '@/api/category'
import { difficultyColors, difficultyMap, questionTypeMap } from '@/utils/format'

const loading = ref(false)
const categories = ref([])
const list = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const cancelingId = ref(null)

const filters = reactive({ type: '', difficulty: '', categoryId: null })

const filterParams = computed(() => {
  const params = {}
  if (filters.type) params.type = filters.type
  if (filters.difficulty) params.difficulty = filters.difficulty
  if (filters.categoryId) params.categoryId = filters.categoryId
  return params
})

const hasFilters = computed(() => Object.keys(filterParams.value).length > 0)
const emptyDescription = computed(() =>
  hasFilters.value ? '没有符合条件的收藏题目' : '还没有收藏任何题目',
)

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

function orderedChoices(question) {
  return (question?.choices || []).slice().sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
}

function choiceLabel(index) {
  return String.fromCharCode(65 + index)
}

let loadSeq = 0
async function loadData() {
  const seq = ++loadSeq
  loading.value = true
  try {
    const params = { ...filterParams.value, page: page.value, size: size.value }
    let res = await getFavoriteList(params)
    let records = res?.records || []
    let totalCount = res?.total || 0

    // 取消收藏后当前页可能为空，回到第一页，避免停留在空白页
    if (!records.length && page.value > 1 && totalCount > 0) {
      page.value = 1
      res = await getFavoriteList({ ...params, page: 1 })
      records = res?.records || []
      totalCount = res?.total || 0
    }

    if (seq === loadSeq) {
      list.value = records.map(withQuestion)
      total.value = totalCount
    }
  } catch {
    // request.js 已统一提示错误，这里保持原列表
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function search() {
  page.value = 1
  loadData()
}

function resetFilters() {
  filters.type = ''
  filters.difficulty = ''
  filters.categoryId = null
  search()
}

async function confirmRemove(item) {
  return ElMessageBox.confirm(`确定取消收藏《${item.question.title || '该题'}》吗？`, '取消收藏', {
    type: 'warning',
    confirmButtonText: '取消收藏',
    cancelButtonText: '再想想',
  })
    .then(() => true)
    .catch(() => false)
}

async function handleRemove(item) {
  const confirmed = await confirmRemove(item)
  if (!confirmed) return false

  cancelingId.value = item.questionId
  try {
    await removeFavorite(item.questionId)
    ElMessage.success('已取消收藏')
    if (list.value.length === 1 && page.value > 1) page.value -= 1
    await loadData()
    return true
  } catch {
    return false
  } finally {
    cancelingId.value = null
  }
}

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

.search-hint {
  font-size: 12.5px;
  color: $text-muted;
  margin-left: auto;
}

.list-wrap {
  min-height: 180px;
}

.favorite-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.favorite-card {
  @include glass;
  border-radius: $radius-lg;
  padding: 18px 20px;
  transition: border-color $transition-normal, box-shadow $transition-normal;

  &:hover {
    border-color: $border-strong;
    box-shadow: $shadow-md;
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

.favorite-time {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: $text-muted;
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

  .choice-content {
    flex: 1;
    color: $text-primary;
    word-break: break-word;
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
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: $text-muted;
}

.foot-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 640px) {
  .search-hint {
    margin-left: 0;
    width: 100%;
  }
  .card-head {
    flex-direction: column;
  }
}
</style>
