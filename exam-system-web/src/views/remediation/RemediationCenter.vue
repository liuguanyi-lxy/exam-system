<template>
  <div class="page-container remediation-center">
    <div class="page-header">
      <h2>补课中心</h2>
      <span class="header-count" v-if="!loading && tasks.length">共 {{ tasks.length }} 个任务</span>
    </div>

    <!-- 状态筛选 -->
    <div class="filter-bar">
      <el-radio-group v-model="statusFilter" @change="loadData">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="IN_PROGRESS">进行中</el-radio-button>
        <el-radio-button value="COMPLETED">已完成</el-radio-button>
      </el-radio-group>
      <span class="filter-hint">按考试诊断自动生成，完成视频学习、练习与复测达标后任务关闭</span>
    </div>

    <div class="list-wrap" v-loading="loading">
      <el-empty v-if="!loading && !tasks.length" :description="emptyDescription">
        <el-button v-if="statusFilter" @click="statusFilter = ''">查看全部任务</el-button>
        <el-button v-else class="btn-gradient" @click="$router.push('/exam/list')">去参加考试</el-button>
      </el-empty>

      <div v-else class="task-grid">
        <article
          v-for="task in tasks"
          :key="task.id"
          class="task-card"
          :class="{ 'is-completed': task.status === 'COMPLETED' }"
          @click="$router.push(`/remediation/tasks/${task.id}`)"
        >
          <div class="card-top">
            <h3 class="point-name">{{ task.knowledgePointName || '未知知识点' }}</h3>
            <el-tag size="small" :type="task.status === 'COMPLETED' ? 'success' : 'warning'" effect="light">
              {{ task.status === 'COMPLETED' ? '已完成' : '进行中' }}
            </el-tag>
          </div>

          <div class="mastery-row">
            <span class="mastery-label">掌握度</span>
            <template v-if="masteryOf(task) !== null">
              <el-progress
                class="mastery-bar"
                :percentage="masteryOf(task)"
                :stroke-width="8"
                :color="masteryColor(task)"
              />
              <span class="mastery-num">{{ masteryOf(task) }}%</span>
            </template>
            <span v-else class="mastery-pending">待评估</span>
          </div>

          <div class="card-tags">
            <el-tag v-if="task.overdue" size="small" type="danger" effect="light">已逾期</el-tag>
            <el-tag v-if="task.pendingRetest" size="small" type="warning" effect="light">待复测</el-tag>
          </div>

          <div class="card-foot">
            <span class="due-time">
              <el-icon><Clock /></el-icon>
              {{ task.dueTime ? `截止 ${task.dueTime}` : '不限完成时间' }}
            </span>
            <span class="go-detail">
              开始补课
              <el-icon><ArrowRight /></el-icon>
            </span>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getRemediationTasks } from '@/api/remediation'

const loading = ref(false)
const statusFilter = ref('')
const tasks = ref([])

const emptyDescription = computed(() => {
  if (statusFilter.value === 'IN_PROGRESS') return '当前没有进行中的补课任务'
  if (statusFilter.value === 'COMPLETED') return '还没有已完成的补课任务'
  return '太棒了，暂时没有需要补课的任务'
})

// masteryTotal 为 null 表示尚未产生掌握度记录
function masteryOf(task) {
  if (task.masteryTotal === null || task.masteryTotal === undefined) return null
  return Math.round(Number(task.masteryTotal))
}

function masteryColor(task) {
  const mastery = masteryOf(task)
  if (mastery === null) return '#98a2b3'
  return mastery >= 60 ? '#12b76a' : '#f79009'
}

async function loadData() {
  loading.value = true
  try {
    tasks.value = (await getRemediationTasks(statusFilter.value)) || []
  } catch {
    // request.js 已统一提示错误，保持原列表
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.header-count {
  font-size: 13px;
  color: $text-muted;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: $space-4;
  flex-wrap: wrap;
  margin-bottom: $space-5;
  padding: $space-3 $space-4;
  background: $bg-soft;
  border: 1px solid $border;
  border-radius: $radius-md;

  .filter-hint {
    font-size: 12.5px;
    color: $text-muted;
  }
}

.list-wrap {
  min-height: 180px;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: $space-4;
}

.task-card {
  @include glass;
  border-radius: $radius-lg;
  padding: $space-4 $space-5;
  cursor: pointer;
  transition: transform $transition-normal, box-shadow $transition-normal, border-color $transition-normal;

  &:hover {
    transform: translateY(-2px);
    border-color: $border-strong;
    box-shadow: $shadow-md;
  }
  &.is-completed {
    border-left: 3px solid $success;
  }
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-2;
  margin-bottom: $space-3;

  .point-name {
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.5;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}

.mastery-row {
  display: flex;
  align-items: center;
  gap: $space-2;
  margin-bottom: $space-2;

  .mastery-label {
    flex-shrink: 0;
    font-size: 12.5px;
    color: $text-muted;
  }
  .mastery-bar {
    flex: 1;
  }
  .mastery-num {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    font-variant-numeric: tabular-nums;
  }
  .mastery-pending {
    font-size: 12.5px;
    color: $text-muted;
  }
}

.card-tags {
  display: flex;
  align-items: center;
  gap: $space-2;
  flex-wrap: wrap;
  min-height: 22px;
  margin-bottom: $space-3;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-2;
  padding-top: $space-3;
  border-top: 1px solid $border;

  .due-time {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12.5px;
    color: $text-muted;

    .el-icon {
      color: $danger;
    }
  }
  .go-detail {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    font-size: 13px;
    font-weight: 500;
    color: $primary;
    white-space: nowrap;
  }
}

@media (max-width: 640px) {
  .task-grid {
    grid-template-columns: 1fr;
  }
}
</style>
