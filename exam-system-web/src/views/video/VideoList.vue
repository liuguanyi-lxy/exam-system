<template>
  <div class="page-container">
    <div class="page-header">
      <h2>视频中心</h2>
      <el-button type="primary" class="btn-gradient" round @click="$router.push('/videos/submit')">
        <el-icon style="margin-right:4px"><VideoCamera /></el-icon> 投稿视频
      </el-button>
    </div>

    <div class="search-bar">
      <el-select v-model="filterCategory" placeholder="分类" clearable @change="search" style="width:180px">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-input v-model="filterKeyword" placeholder="搜索视频" clearable @keyup.enter="search" style="width:240px"
        :prefix-icon="Search" />
      <el-button type="primary" round @click="search">搜索</el-button>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col :xs="12" :sm="8" :md="6" v-for="v in videoList" :key="v.id" style="margin-bottom:16px">
        <div class="video-card card-hover" @click="$router.push(`/videos/${v.id}`)">
          <div class="vc-cover">
            <img v-if="v.coverUrl" :src="v.coverUrl" loading="lazy" />
            <span v-else class="vc-placeholder"><el-icon :size="36"><VideoPlay /></el-icon></span>
            <span class="vc-play"><el-icon :size="28"><VideoPlay /></el-icon></span>
            <span class="vc-source" v-if="v.sourceType === 1">外链</span>
            <span class="vc-duration" v-if="v.duration">{{ Math.floor(v.duration/60) }}:{{ (v.duration%60).toString().padStart(2,'0') }}</span>
          </div>
          <div class="vc-body">
            <div class="vc-title">{{ v.title }}</div>
            <div class="vc-meta">
              <span><el-icon><View /></el-icon>{{ v.viewCount || 0 }}</span>
              <span><el-icon><Star /></el-icon>{{ v.likeCount || 0 }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-empty v-if="!videoList.length && !loading" description="暂无视频" />

    <div style="margin-top:16px;text-align:right" v-if="total > size">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
        layout="prev,pager,next" @change="loadData" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search, VideoPlay } from '@element-plus/icons-vue'
import { getVideos } from '@/api/video'
import { getVideoCategories } from '@/api/videoCategory'

const loading = ref(false)
const videoList = ref([])
const categories = ref([])
const filterCategory = ref(null)
const filterKeyword = ref('')
const page = ref(1)
const size = ref(12)
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getVideos({
      page: page.value, size: size.value,
      categoryId: filterCategory.value || undefined,
      keyword: filterKeyword.value || undefined,
    })
    videoList.value = res?.records || []
    total.value = res?.total || 0
  } finally { loading.value = false }
}

function search() { page.value = 1; loadData() }

onMounted(async () => {
  try { categories.value = await getVideoCategories() || []; } catch {}
  loadData()
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.video-card {
  cursor: pointer;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.7);
  box-shadow: $shadow-sm;

  .vc-cover {
    aspect-ratio: 16/9;
    background: $gradient-sidebar;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    overflow: hidden;
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform $transition-normal;
    }
    .vc-placeholder { color: #a5b4fc; }
    .vc-play {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      background: rgba(0, 0, 0, 0.35);
      opacity: 0;
      transition: opacity $transition-fast;
    }
    .vc-source {
      position: absolute;
      top: 8px;
      left: 8px;
      background: rgba(245, 158, 11, 0.9);
      color: #fff;
      font-size: 11px;
      padding: 2px 7px;
      border-radius: 6px;
      font-weight: 600;
    }
    .vc-duration {
      position: absolute;
      bottom: 8px;
      right: 8px;
      background: rgba(0, 0, 0, 0.75);
      color: #fff;
      font-size: 11px;
      padding: 3px 8px;
      border-radius: 6px;
      backdrop-filter: blur(4px);
      font-variant-numeric: tabular-nums;
    }
    &:hover {
      img { transform: scale(1.1); }
      .vc-play { opacity: 1; }
    }
  }

  .vc-body {
    padding: 10px 12px 12px;
    .vc-title {
      font-size: 14px;
      font-weight: 600;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      margin-bottom: 6px;
    }
    .vc-meta {
      display: flex;
      gap: 14px;
      font-size: 12px;
      color: $text-muted;
      span { display: flex; align-items: center; gap: 4px; }
    }
  }
}
</style>
