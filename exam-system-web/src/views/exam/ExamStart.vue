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
          <el-descriptions-item label="考试时长">
            <span class="desc-val"><el-icon><Clock /></el-icon> {{ paper.duration }} 分钟</span>
          </el-descriptions-item>
          <el-descriptions-item label="题目数量">
            <span class="desc-val"><el-icon><Document /></el-icon> {{ paper.questionCount }} 题</span>
          </el-descriptions-item>
          <el-descriptions-item label="总分">
            <span class="desc-val text-gradient desc-score">{{ paper.totalScore }} 分</span>
          </el-descriptions-item>
          <el-descriptions-item label="试卷描述">
            {{ paper.description || '暂无描述' }}
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
  margin-bottom: 20px;
  .page-title {
    font-size: 24px;
    font-weight: 700;
    margin-bottom: 6px;
    @include gradient-text;
  }
  .sub { color: $text-muted; font-size: 14px; }
}

.glass-card {
  @include glass;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 20px;
}

// 试卷信息
.paper-info {
  .paper-name-row {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 20px;
    .paper-type-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 52px;
      height: 52px;
      border-radius: 14px;
      background: $gradient-primary;
      color: #fff;
      flex-shrink: 0;
    }
    .paper-name { font-size: 18px; font-weight: 700; }
    .paper-status { margin-top: 4px; }
  }
  .paper-descs {
    .desc-val { display: flex; align-items: center; gap: 6px; font-weight: 500; }
    .desc-score { font-weight: 700; }
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
  width: 200px;
  height: 46px;
  font-size: 16px;
  letter-spacing: 3px;
}

// 成功卡片
.success-card {
  text-align: center;
  padding: 40px;
}
</style>
