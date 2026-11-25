<template>
  <div class="page-container remediation-detail" v-loading="loading">
    <template v-if="detail">
      <!-- 返回 + 标题区 -->
      <div class="detail-header">
        <el-button link @click="$router.push('/remediation')">
          <el-icon style="margin-right:4px"><ArrowLeft /></el-icon>返回补课中心
        </el-button>
        <div class="header-main">
          <h2 class="point-name">{{ detail.knowledgePointName || '未知知识点' }}</h2>
          <div class="header-tags">
            <el-tag size="small" :type="isTaskCompleted ? 'success' : 'warning'" effect="light">
              {{ isTaskCompleted ? '已完成' : '进行中' }}
            </el-tag>
            <el-tag v-if="detail.overdue" size="small" type="danger" effect="light">已逾期</el-tag>
            <el-tag v-if="detail.pendingRetest" size="small" type="warning" effect="light">待复测</el-tag>
          </div>
        </div>
        <div class="header-meta">
          <span>
            <el-icon><Clock /></el-icon>
            {{ detail.dueTime ? `截止 ${detail.dueTime}` : '不限完成时间' }}
          </span>
          <span>练习 {{ policy.practiceCount ?? '-' }} 题 · 复测 {{ policy.retestCount ?? '-' }} 题 · 达标线 {{ policy.minMastery ?? '-' }}%</span>
        </div>
        <div class="mastery-line">
          <span class="mastery-label">掌握度</span>
          <template v-if="mastery !== null">
            <el-progress class="mastery-bar" :percentage="mastery" :stroke-width="8" :color="masteryColor" />
            <span class="mastery-num">{{ mastery }}%</span>
          </template>
          <span v-else class="mastery-pending">待评估</span>
        </div>
      </div>

      <!-- 四步流程 -->
      <el-steps class="flow-steps" align-center>
        <el-step title="推荐视频" :status="stepStatus('video')" :description="videoStepDesc" />
        <el-step title="基础练习" :status="stepStatus('practice')" :description="gateOpen ? '' : '视频完成后解锁'" />
        <el-step title="变式题" :status="stepStatus('variation')" />
        <el-step title="阶段复测" :status="stepStatus('retest')" />
      </el-steps>

      <!-- 完成庆祝态（提交复测达标后触发） -->
      <div v-if="celebrated" class="celebrate-card slide-up">
        <el-result icon="success" title="补课任务已完成" :sub-title="celebrateSubTitle">
          <template #extra>
            <el-button type="primary" class="btn-gradient" @click="$router.push('/remediation')">返回补课中心</el-button>
          </template>
        </el-result>
      </div>
      <el-alert
        v-else-if="isTaskCompleted"
        title="该补课任务已完成，可继续练习巩固"
        type="success"
        show-icon
        :closable="false"
        class="completed-alert"
      />

      <!-- 视频门禁提示 -->
      <el-alert
        v-if="!gateOpen"
        title="请先完成视频学习，完成后才能进入练习、变式与复测"
        type="warning"
        show-icon
        :closable="false"
        class="gate-alert"
      />

      <!-- 四步 Tab -->
      <el-tabs v-model="activeTab" class="step-tabs">
        <el-tab-pane label="推荐视频" name="video">
          <!-- 无可推荐视频 -->
          <el-empty
            v-if="detail.steps.video === 'NO_VIDEO'"
            description="本知识点暂无可推荐视频，可直接进入基础练习"
            :image-size="80"
          />
          <template v-else>
            <div class="video-list">
              <div v-for="v in videos" :key="v.videoId" class="video-item">
                <img v-if="v.coverUrl" :src="v.coverUrl" class="video-cover" alt="" />
                <div v-else class="video-cover cover-fallback">
                  <el-icon :size="26"><VideoPlay /></el-icon>
                </div>
                <div class="video-info">
                  <span class="video-title">{{ v.title }}</span>
                  <el-tag size="small" :type="v.completed ? 'success' : 'info'" effect="light">
                    {{ v.completed ? '已完成' : '未完成' }}
                  </el-tag>
                </div>
                <el-button size="small" type="primary" plain @click="$router.push(`/videos/${v.videoId}`)">
                  去学习
                </el-button>
              </div>
            </div>
            <div class="video-complete-bar">
              <template v-if="detail.steps.video === 'DONE'">
                <el-tag type="success" effect="light"><el-icon style="margin-right:3px"><CircleCheck /></el-icon>视频学习已完成</el-tag>
              </template>
              <template v-else>
                <el-button
                  type="primary"
                  class="btn-gradient"
                  :disabled="!allVideosDone"
                  :loading="completing"
                  @click="markVideoComplete"
                >标记视频完成</el-button>
                <span v-if="!allVideosDone && videos.length" class="complete-hint">完成全部推荐视频后可标记进入练习</span>
              </template>
            </div>
          </template>
        </el-tab-pane>

        <el-tab-pane label="基础练习" name="practice" :disabled="!gateOpen" lazy>
          <RemediationPractice :task-id="taskId" step="practice" @task-completed="onTaskCompleted" />
        </el-tab-pane>

        <el-tab-pane label="变式题" name="variation" :disabled="!gateOpen" lazy>
          <RemediationPractice :task-id="taskId" step="variation" @task-completed="onTaskCompleted" />
        </el-tab-pane>

        <el-tab-pane label="阶段复测" name="retest" :disabled="!gateOpen" lazy>
          <RemediationPractice :task-id="taskId" step="retest" @task-completed="onTaskCompleted" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 加载失败兜底 -->
    <el-empty v-else-if="!loading" description="任务不存在或无权访问">
      <el-button type="primary" class="btn-gradient" @click="$router.push('/remediation')">返回补课中心</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { completeRemediationVideo, getRemediationTask } from '@/api/remediation'
import RemediationPractice from './RemediationPractice.vue'

const route = useRoute()
const loading = ref(false)
const detail = ref(null)
const activeTab = ref('video')
const completing = ref(false)
const celebrated = ref(false)
// 首次加载后不再自动切换 Tab，避免刷新掌握度时打断用户操作
let tabInitialized = false

const taskId = computed(() => Number(route.params.id))
const policy = computed(() => detail.value?.policy || {})
const gateOpen = computed(() => detail.value?.steps?.gateOpen === true)
const isTaskCompleted = computed(() => detail.value?.status === 'COMPLETED')
const videos = computed(() => detail.value?.videos || [])
const allVideosDone = computed(() => videos.value.every((v) => v.completed))

// masteryTotal 为 null 表示尚未产生掌握度记录
const mastery = computed(() => {
  const raw = detail.value?.masteryTotal
  if (raw === null || raw === undefined) return null
  return Math.round(Number(raw))
})
const masteryColor = computed(() => (mastery.value !== null && mastery.value >= (policy.value.minMastery ?? 60) ? '#12b76a' : '#f79009'))

const videoStepDesc = computed(() => {
  const v = detail.value?.steps?.video
  if (v === 'NO_VIDEO') return '无推荐视频'
  if (v === 'DONE') return '已完成'
  return '待学习'
})

const celebrateSubTitle = computed(() => {
  const min = policy.value.minMastery ?? 60
  if (mastery.value !== null) return `最新掌握度 ${mastery.value}%，已达到达标线（${min}%）`
  return '阶段复测成绩已达标'
})

function stepStatus(name) {
  if (isTaskCompleted.value) return 'success'
  if (name === 'video') {
    const v = detail.value?.steps?.video
    if (v === 'DONE' || v === 'NO_VIDEO' || gateOpen.value) return 'success'
    return 'process'
  }
  return activeTab.value === name ? 'process' : 'wait'
}

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getRemediationTask(taskId.value)
    if (!tabInitialized) {
      tabInitialized = true
      // 默认落在当前应做的步骤：视频门禁未过先看视频，过了直接进入练习
      activeTab.value = gateOpen.value ? 'practice' : 'video'
    }
  } catch {
    // request.js 已统一提示错误
  } finally {
    loading.value = false
  }
}

async function markVideoComplete() {
  completing.value = true
  try {
    await completeRemediationVideo(taskId.value)
    ElMessage.success('视频学习已完成')
    await loadDetail()
  } catch {
    // 有映射视频未全部完成时后端返回 400，提示由 request.js 统一弹出
  } finally {
    completing.value = false
  }
}

async function onTaskCompleted() {
  celebrated.value = true
  await loadDetail()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(loadDetail)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.detail-header {
  padding-bottom: $space-4;
  margin-bottom: $space-4;
  border-bottom: 1px solid $border;

  .header-main {
    display: flex;
    align-items: center;
    gap: $space-3;
    flex-wrap: wrap;
    margin: $space-2 0 $space-3;

    .point-name {
      font-size: 20px;
      font-weight: 600;
      color: $text-primary;
      margin: 0;
    }
    .header-tags {
      display: flex;
      align-items: center;
      gap: $space-2;
    }
  }

  .header-meta {
    display: flex;
    align-items: center;
    gap: $space-5;
    flex-wrap: wrap;
    font-size: 12.5px;
    color: $text-muted;
    margin-bottom: $space-3;

    span {
      display: inline-flex;
      align-items: center;
      gap: 5px;
    }
  }

  .mastery-line {
    display: flex;
    align-items: center;
    gap: $space-3;

    .mastery-label {
      flex-shrink: 0;
      font-size: 12.5px;
      color: $text-muted;
    }
    .mastery-bar {
      flex: 1;
      max-width: 420px;
    }
    .mastery-num {
      flex-shrink: 0;
      font-size: 14px;
      font-weight: 600;
      color: $text-primary;
      font-variant-numeric: tabular-nums;
    }
    .mastery-pending {
      font-size: 12.5px;
      color: $text-muted;
    }
  }
}

.flow-steps {
  margin-bottom: $space-5;

  :deep(.el-step__title) {
    font-size: 13.5px;
  }
  :deep(.el-step__description) {
    font-size: 12px;
  }
}

.celebrate-card {
  @include glass;
  border-radius: $radius-lg;
  margin-bottom: $space-4;
}

.completed-alert,
.gate-alert {
  margin-bottom: $space-4;
}

.step-tabs {
  .video-list {
    display: flex;
    flex-direction: column;
    gap: $space-3;
    margin-bottom: $space-4;
  }

  .video-item {
    @include glass;
    display: flex;
    align-items: center;
    gap: $space-3;
    border-radius: $radius-md;
    padding: $space-3 $space-4;

    .video-cover {
      flex-shrink: 0;
      width: 96px;
      height: 60px;
      border-radius: $radius-sm;
      object-fit: cover;
      background: $bg-soft;

      &.cover-fallback {
        display: flex;
        align-items: center;
        justify-content: center;
        color: $text-muted;
        border: 1px solid $border;
      }
    }

    .video-info {
      flex: 1;
      min-width: 0;
      display: flex;
      align-items: center;
      gap: $space-2;
      flex-wrap: wrap;

      .video-title {
        font-size: 14px;
        font-weight: 500;
        color: $text-primary;
        line-height: 1.5;
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
      }
    }
  }

  .video-complete-bar {
    display: flex;
    align-items: center;
    gap: $space-3;
    padding-top: $space-3;
    border-top: 1px solid $border;

    .complete-hint {
      font-size: 12.5px;
      color: $text-muted;
    }
  }
}
</style>
