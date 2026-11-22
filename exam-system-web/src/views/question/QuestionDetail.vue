<template>
  <div class="page-container question-detail-page" v-loading="loading">
    <div class="page-header">
      <h2>题目详情</h2>
      <div class="header-actions">
        <el-button size="small" @click="$router.back()">
          <el-icon style="margin-right:4px"><ArrowLeft /></el-icon>返回
        </el-button>
        <el-button
          v-if="question && canFavorite"
          size="small"
          :type="favorited ? 'danger' : 'default'"
          plain
          :loading="toggling"
          @click="toggleFavorite"
        >
          <el-icon style="margin-right:4px">
            <StarFilled v-if="favorited" />
            <Star v-else />
          </el-icon>
          {{ favorited ? '已收藏' : '收藏题目' }}
        </el-button>
      </div>
    </div>

    <template v-if="question">
      <div class="question-meta">
        <el-tag size="small" effect="light">{{ typeLabel(question.type) }}</el-tag>
        <el-tag v-if="question.type === 'CHOICE' && question.multi" size="small" type="info" effect="plain">
          多选
        </el-tag>
        <el-tag size="small" :type="difficultyColor(question.difficulty)" effect="light">
          {{ difficultyLabel(question.difficulty) }}
        </el-tag>
        <el-tag v-if="categoryName" size="small" type="info" effect="plain">{{ categoryName }}</el-tag>
      </div>

      <h3 class="question-title" :class="{ 'is-code': question.type === 'CODE' }">
        {{ question.title }}
      </h3>

      <div v-if="question.type === 'CHOICE' && choices.length" class="choice-list">
        <div v-for="(choice, index) in choices" :key="choice.id || index" class="choice-item">
          <span class="choice-label">{{ choiceLabel(index) }}</span>
          <span class="choice-content">{{ choice.content }}</span>
        </div>
      </div>

      <div class="detail-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>学生端题目详情不展示答案与解析；若该题曾答错，可到错题本查看完整复习内容。</span>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="题目不存在或已删除">
      <el-button v-if="canFavorite" @click="$router.push('/favorites')">返回我的收藏</el-button>
      <el-button v-else @click="$router.back()">返回</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getQuestionById } from '@/api/question'
import { addFavorite, getFavoriteStatus, removeFavorite } from '@/api/favorite'
import { difficultyColors, difficultyMap, questionTypeMap } from '@/utils/format'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const toggling = ref(false)
const question = ref(null)
const favorited = ref(false)

const questionId = computed(() => Number(route.params.id))
// 收藏是学生个人数据（后端 @RequireRole STUDENT），非学生不展示入口、不请求收藏状态
const canFavorite = computed(() => userStore.isStudent)
// 后端详情补了 categoryName 字段；为空时不展示分类标签
const categoryName = computed(() => question.value?.categoryName || '')
const choices = computed(() =>
  (question.value?.choices || []).slice().sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0)),
)

function typeLabel(type) {
  return questionTypeMap[type] || '简答题'
}

function difficultyLabel(difficulty) {
  return difficultyMap[difficulty] || '中等'
}

function difficultyColor(difficulty) {
  return difficultyColors[difficulty] || 'info'
}

function choiceLabel(index) {
  return String.fromCharCode(65 + index)
}

async function toggleFavorite() {
  if (!canFavorite.value || !question.value || !Number.isFinite(questionId.value)) return
  toggling.value = true
  try {
    if (favorited.value) {
      await removeFavorite(questionId.value)
      favorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(questionId.value)
      favorited.value = true
      ElMessage.success('收藏成功')
    }
  } catch {
    // request.js 已统一提示错误
  } finally {
    toggling.value = false
  }
}

onMounted(async () => {
  if (!Number.isFinite(questionId.value)) return
  loading.value = true
  try {
    // 非学生不请求收藏状态，避免每次加载都触发 403
    const requests = [getQuestionById(questionId.value)]
    if (canFavorite.value) {
      requests.push(getFavoriteStatus(questionId.value).catch(() => null))
    }
    const [detail, status] = await Promise.all(requests)
    question.value = detail || null
    favorited.value = Boolean(status?.favorited)
  } catch {
    question.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.question-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.question-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.75;
  color: $text-primary;
  margin-bottom: 16px;
  white-space: pre-wrap;
  word-break: break-word;

  &.is-code {
    font-family: $font-mono;
    font-size: 13px;
    background: #fcfcfd;
    border: 1px solid $border;
    border-radius: $radius-sm;
    padding: 12px 14px;
  }
}

.choice-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.choice-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 10px 12px;
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
    width: 22px;
    height: 22px;
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

.detail-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  border-radius: $radius-sm;
  background: $bg-soft;
  color: $text-muted;
  font-size: 12.5px;
  line-height: 1.6;

  .el-icon {
    flex-shrink: 0;
    margin-top: 2px;
  }
}
</style>
