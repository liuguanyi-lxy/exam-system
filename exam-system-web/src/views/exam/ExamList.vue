<template>
  <div class="exam-list" v-loading="loading">
    <div class="page-head">
      <h2 class="page-title">在线考试</h2>
      <p class="sub">选择已发布的试卷，点击「开始考试」进入答题</p>
    </div>

    <el-empty v-if="!loading && !papers.length" description="暂无可用考试，请稍后再来">
      <el-button class="btn-gradient" @click="$router.push('/exam/history')">查看我的考试</el-button>
    </el-empty>

    <el-row :gutter="16" v-else>
      <el-col :xs="24" :sm="12" :md="8" v-for="p in papers" :key="p.id" style="margin-bottom:16px">
        <div class="paper-card card-hover" @click="startExam(p)">
          <div class="paper-head">
            <span class="paper-icon"><el-icon :size="16"><Document /></el-icon></span>
            <el-tag size="small" type="success" effect="light">已发布</el-tag>
          </div>
          <span class="paper-name">{{ p.name }}</span>
          <p class="paper-desc">{{ p.description || '暂无描述' }}</p>
          <div class="paper-meta">
            <div class="meta-item">
              <span class="meta-val">{{ p.duration }}</span>
              <span class="meta-unit">分钟</span>
            </div>
            <div class="meta-item">
              <span class="meta-val">{{ p.questionCount }}</span>
              <span class="meta-unit">题</span>
            </div>
            <div class="meta-item">
              <span class="meta-val">{{ p.totalScore }}</span>
              <span class="meta-unit">分</span>
            </div>
          </div>
          <div class="paper-actions">
            <el-button type="primary" plain @click.stop="startExam(p)">
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
  margin-bottom: 20px;
  .page-title {
    font-size: 22px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 4px;
  }
  .sub { color: $text-muted; font-size: 13px; margin: 0; }
}

.paper-card {
  position: relative;
  background: $surface;
  border-radius: $radius-xl;
  padding: 18px;
  border: 1px solid $border;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  height: 100%;
  @include card-hover(-2px, false);

  .paper-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 12px;
    .paper-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 30px;
      height: 30px;
      border-radius: $radius-sm;
      background: $bg-soft;
      color: $text-secondary;
    }
  }
  .paper-name {
    display: block;
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 6px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .paper-desc {
    color: $text-muted;
    font-size: 12.5px;
    min-height: 38px;
    margin: 0 0 14px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    line-height: 1.6;
  }
  .paper-meta {
    display: flex;
    padding: 12px 0;
    margin-bottom: 14px;
    border-top: 1px solid $border;
    border-bottom: 1px solid $border;
    .meta-item {
      flex: 1;
      display: flex;
      align-items: baseline;
      justify-content: center;
      gap: 2px;
      & + .meta-item { border-left: 1px solid $border; }
      .meta-val {
        font-size: 16px;
        font-weight: 600;
        color: $text-primary;
        font-variant-numeric: tabular-nums;
      }
      .meta-unit { font-size: 11.5px; color: $text-muted; }
    }
  }
  .paper-actions {
    margin-top: auto;
    .el-button { width: 100%; }
  }
}
</style>
