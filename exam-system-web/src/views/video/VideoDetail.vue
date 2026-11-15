<template>
  <div class="page-container video-detail" v-loading="loading">
    <div v-if="video">
      <!-- 播放器区域 -->
      <div class="player-wrap">
        <video v-if="!videoFailed" :src="video.fileUrl" controls preload="metadata"
          class="video-player" @error="videoFailed = true">
          您的浏览器不支持视频播放
        </video>
        <div v-else class="player-fallback">
          <el-icon :size="64" class="fallback-icon"><VideoPlay /></el-icon>
          <p class="fallback-title">视频即将上线</p>
          <p class="fallback-sub">该视频正在处理中，敬请期待</p>
        </div>
        <div v-if="video.duration" class="player-duration">
          {{ Math.floor(video.duration/60) }}:{{ (video.duration%60).toString().padStart(2,'0') }}
        </div>
      </div>

      <!-- 标题和元信息 -->
      <div class="video-info">
        <h2 class="video-title">{{ video.title }}</h2>
        <div class="video-meta">
          <span class="meta-item"><el-icon><View /></el-icon>{{ video.viewCount || 0 }} 次观看</span>
          <span class="meta-item"><el-icon><Clock /></el-icon>{{ video.createdAt }}</span>
          <el-button :type="liked ? 'danger' : 'default'" size="small" round class="like-btn"
            :class="{ liked }" @click="handleLike">
            <el-icon><StarFilled v-if="liked" /><Star v-else /></el-icon>
            {{ video.likeCount || 0 }} 赞
          </el-button>
        </div>
      </div>

      <!-- 描述 -->
      <div class="video-desc">
        <div class="desc-head">
          <el-tag v-if="video.categoryName" size="small" effect="light">{{ video.categoryName }}</el-tag>
          <el-tag v-if="video.uploaderName" size="small" effect="plain">{{ video.uploaderName }}</el-tag>
        </div>
        <p>{{ video.description || '暂无描述' }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getVideoById, toggleVideoLike, recordVideoView } from '@/api/video'

const route = useRoute()
const loading = ref(false)
const video = ref(null)
const liked = ref(false)
const videoFailed = ref(false)
let viewTimer = null

async function handleLike() {
  try {
    const res = await toggleVideoLike(video.value.id)
    liked.value = res?.isLiked ?? !liked.value
    ElMessage.success(res?.message || (liked.value ? '点赞成功' : '已取消'))
  } catch {}
}

onMounted(async () => {
  loading.value = true
  try {
    video.value = await getVideoById(Number(route.params.id))
    liked.value = video.value?.isLiked || false
    let watchTime = 0
    viewTimer = setInterval(() => { watchTime++ }, 1000)
    window.addEventListener('beforeunload', () => {
      if (watchTime > 0) recordVideoView(video.value.id, watchTime)
    })
  } finally { loading.value = false }
})

onBeforeUnmount(() => {
  if (viewTimer) clearInterval(viewTimer)
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.player-wrap {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
  box-shadow: $shadow-lg;

  .video-player {
    width: 100%;
    max-height: 480px;
    display: block;
  }
  .player-fallback {
    aspect-ratio: 16/7;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: rgba(255, 255, 255, 0.8);
    .fallback-icon { color: #818cf8; margin-bottom: 16px; animation: pulse 2s infinite; }
    .fallback-title { font-size: 18px; font-weight: 600; margin-bottom: 6px; }
    .fallback-sub { font-size: 13px; opacity: 0.6; }
  }
  .player-duration {
    position: absolute;
    right: 12px;
    top: 12px;
    background: rgba(0, 0, 0, 0.6);
    color: #fff;
    font-size: 12px;
    padding: 4px 10px;
    border-radius: 8px;
    backdrop-filter: blur(4px);
  }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.video-info {
  padding: 20px 4px 8px;
  .video-title {
    font-size: 22px;
    font-weight: 700;
    margin-bottom: 12px;
  }
  .video-meta {
    display: flex;
    align-items: center;
    gap: 20px;
    color: $text-muted;
    font-size: 13px;
    flex-wrap: wrap;
    .meta-item { display: flex; align-items: center; gap: 5px; }
    .like-btn.liked {
      border-color: $danger;
      color: $danger;
    }
  }
}

.video-desc {
  @include glass;
  border-radius: 10px;
  padding: 18px 20px;
  margin-top: 16px;
  .desc-head {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }
  p {
    color: $text-secondary;
    line-height: 1.8;
    white-space: pre-wrap;
    font-size: 14px;
  }
}
</style>
