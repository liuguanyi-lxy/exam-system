<template>
  <div class="exam-list" v-loading="loading">
    <div class="page-head">
      <h2 class="page-title">📝 在线考试</h2>
      <p class="sub">选择已发布的试卷，点击「开始考试」进入答题</p>
    </div>

    <el-empty v-if="!loading && !papers.length" description="暂无可用考试，请稍后再来">
      <el-button class="btn-gradient" @click="$router.push('/exam/history')">查看我的考试</el-button>
    </el-empty>

    <el-row :gutter="20" v-else>
      <el-col :xs="24" :sm="12" :md="8" v-for="p in papers" :key="p.id" style="margin-bottom:20px">
        <div class="paper-card card-hover" @click="startExam(p)">
          <div class="paper-head">
            <span class="paper-name">{{ p.name }}</span>
            <el-tag size="small" type="success" effect="light">已发布</el-tag>
          </div>
          <p class="paper-desc">{{ p.description || '暂无描述' }}</p>
          <div class="paper-meta">
            <div class="meta-item">
              <el-icon><Clock /></el-icon>
              <div><span class="meta-val">{{ p.duration }}</span><span class="meta-unit">分钟</span></div>
            </div>
            <div class="meta-item">
              <el-icon><Document /></el-icon>
              <div><span class="meta-val">{{ p.questionCount }}</span><span class="meta-unit">题</span></div>
            </div>
            <div class="meta-item">
              <el-icon><Medal /></el-icon>
              <div><span class="meta-val">{{ p.totalScore }}</span><span class="meta-unit">分</span></div>
            </div>
          </div>
          <div class="paper-actions">
            <el-button type="primary" class="btn-gradient" round @click="startExam(p)">
              开始考试
              <el-icon style="margin-left:4px"><Right /></el-icon>
            </el-button>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getPaperList } from '@/api/paper'

const router = useRouter()
const loading = ref(false)
const papers = ref([])

onMounted(async () => {
  loading.value = true
  try {
    papers.value = await getPaperList({ status: 'PUBLISHED' }) || []
  } finally {
    loading.value = false
  }
})

function startExam(paper) {
  router.push(`/exam/start/${paper.id}`)
}
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.page-head {
  margin-bottom: 24px;
  .page-title {
    font-size: 24px;
    font-weight: 700;
    margin: 0 0 6px;
    @include gradient-text;
  }
  .sub { color: $text-muted; font-size: 14px; margin: 0; }
}

.paper-card {
  position: relative;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 16px;
  padding: 20px;
  border: 1px solid rgba(226, 232, 240, 0.7);
  cursor: pointer;
  overflow: hidden;
  @include top-accent;

  .paper-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 10px;
    .paper-name {
      font-size: 16px;
      font-weight: 700;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  .paper-desc {
    color: $text-secondary;
    font-size: 13px;
    min-height: 40px;
    margin: 0 0 14px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    line-height: 1.6;
  }
  .paper-meta {
    display: flex;
    justify-content: space-around;
    padding: 12px 0;
    margin-bottom: 14px;
    background: rgba(238, 242, 255, 0.5);
    border-radius: 10px;
    .meta-item {
      display: flex;
      align-items: center;
      gap: 6px;
      .el-icon { color: $primary; }
      .meta-val { font-size: 18px; font-weight: 700; color: $text-primary; }
      .meta-unit { font-size: 12px; color: $text-muted; margin-left: 2px; }
    }
  }
  .paper-actions { text-align: center; }
}
</style>
