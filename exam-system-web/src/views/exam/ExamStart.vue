<template>
  <div class="exam-start" style="max-width:700px;margin:0 auto" v-loading="loading">
    <!-- 开始成功 -->
    <div v-if="started" class="glass-card success-card slide-up">
      <el-result icon="success" title="考试已开始">
        <template #sub-title>
          <p>考试记录ID: {{ recordId }}</p>
          <p>请认真作答，不要切换页面</p>
        </template>
        <template #extra>
          <el-button type="primary" class="btn-gradient" round @click="$router.push(`/exam/taking/${recordId}`)">
            进入考试 <el-icon style="margin-left:4px"><Right /></el-icon>
          </el-button>
        </template>
      </el-result>
    </div>

    <!-- 考试确认 -->
    <template v-else>
      <div class="page-head">
        <h2 class="page-title">考试信息确认</h2>
        <p class="sub">请核对试卷信息，确认后开始考试</p>
      </div>

      <div class="glass-card paper-info" v-if="paper">
        <div class="paper-name-row">
          <span class="paper-type-icon"><el-icon :size="26"><Document /></el-icon></span>
          <div>
            <div class="paper-name">{{ paper.name }}</div>
            <div class="paper-status"><el-tag size="small" type="success" effect="light">已发布</el-tag></div>
          </div>
        </div>
        <el-descriptions :column="2" border class="paper-descs">
          <el-descriptions-item label="时长">
            <span class="desc-val">{{ paper.duration }} 分钟</span>
          </el-descriptions-item>
          <el-descriptions-item label="题量">
            <span class="desc-val">{{ paper.questionCount }} 题</span>
          </el-descriptions-item>
          <el-descriptions-item label="总分">
            <span class="desc-val desc-score">{{ paper.totalScore }} 分</span>
          </el-descriptions-item>
          <el-descriptions-item label="说明">
            <span class="desc-val desc-desc">{{ paper.description || '暂无描述' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="glass-card form-card">
        <el-form label-width="90px">
          <el-form-item label="考生姓名" required>
            <el-input v-model="studentName" placeholder="请输入你的姓名" style="width:280px" :prefix-icon="User" />
          </el-form-item>
        </el-form>
      </div>

      <div class="notice-alert slide-up">
        <el-icon class="notice-icon"><WarningFilled /></el-icon>
        <div>
          <p class="notice-title">注意事项</p>
          <ul>
            <li>考试开始后请勿切换页面，切换次数将被记录</li>
            <li>倒计时归零后将自动交卷</li>
            <li>交卷后由 AI 自动批阅</li>
          </ul>
        </div>
      </div>

      <div style="margin-top:28px;text-align:center">
        <el-button type="primary" size="large" class="btn-gradient start-btn" round @click="handleStart" :loading="starting">
          开始考试
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { getPaperById } from '@/api/paper'
import { useExamStore } from '@/stores/exam'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const examStore = useExamStore()
const userStore = useUserStore()
const loading = ref(false)
const starting = ref(false)
const started = ref(false)
const recordId = ref(null)
const paper = ref(null)
const studentName = ref('')

onMounted(async () => {
  loading.value = true
  // 预填当前登录用户姓名
  if (userStore.realName || userStore.username) {
    studentName.value = userStore.realName || userStore.username
  }
  try { paper.value = await getPaperById(Number(route.params.paperId)); }
  finally { loading.value = false }
})

async function handleStart() {
  if (!studentName.value.trim()) { ElMessage.warning('请输入姓名'); return }
  starting.value = true
  try {
    const record = await examStore.startExam(Number(route.params.paperId), studentName.value.trim())
    recordId.value = record.id
    started.value = true
  } finally { starting.value = false }
}
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.page-head {
  margin-bottom: 18px;
  .page-title {
    font-size: 22px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 4px;
  }
  .sub { color: $text-muted; font-size: 13px; }
}

.glass-card {
  @include glass;
  border-radius: $radius-xl;
  padding: 20px 24px;
  margin-bottom: 16px;
}

// 试卷信息
.paper-info {
  .paper-name-row {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 18px;
    .paper-type-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: $radius-md;
      background: $primary-soft;
      color: $primary;
      flex-shrink: 0;
    }
    .paper-name { font-size: 16px; font-weight: 600; color: $text-primary; }
    .paper-status { margin-top: 4px; }
  }
  .paper-descs {
    // 固定表格布局，否则「说明」那一格内容长会把「时长」列挤窄，出现"60 分/钟"折行
    :deep(.el-descriptions__body table) { table-layout: fixed; }
    :deep(.el-descriptions__label) {
      color: $text-muted;
      font-weight: 400;
      width: 64px;
      white-space: nowrap;
    }
    :deep(.el-descriptions__content) {
      color: $text-primary;
      white-space: normal;
      word-break: break-word;
    }
    .desc-val { display: inline; }
    .desc-score { font-weight: 600; }
    .desc-desc { color: $text-secondary; }
  }
}

// 姓名表单
.form-card {
  padding: 16px 24px;
}

// 注意事项
.notice-alert {
  display: flex;
  gap: 14px;
  padding: 16px 20px;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 12px;
  margin-bottom: 8px;
  .notice-icon { color: $warning; font-size: 20px; flex-shrink: 0; }
  .notice-title { font-weight: 600; margin-bottom: 6px; color: $text-primary; }
  ul {
    padding-left: 18px;
    color: $text-secondary;
    font-size: 13px;
    li { margin-bottom: 4px; }
  }
}

.start-btn {
  min-width: 180px;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
}

// 成功卡片
.success-card {
  text-align: center;
  padding: 40px;
}
</style>
