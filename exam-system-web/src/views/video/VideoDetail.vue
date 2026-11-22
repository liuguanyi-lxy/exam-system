<template>
  <div class="page-container video-detail" v-loading="loading">
    <div v-if="video">
      <!-- 播放器区域 -->
      <div class="player-wrap">
        <!-- YouTube 支持内嵌；B 站等国内平台直接跳转原视频页 -->
        <iframe v-if="external?.embed" :src="external.embed" class="video-iframe" scrolling="no"
          frameborder="no" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"
          :title="`${video.title} 外链播放器`"></iframe>
        <div v-if="external?.embed" class="embed-fallback">
          <span>如果播放器提示错误，可前往原视频页面观看</span>
          <el-button link type="primary" @click="openExternal">打开原视频</el-button>
        </div>
        <!-- 外链视频：无法内嵌的平台，提供跳转 -->
        <div v-else-if="external" class="player-fallback external-fallback">
          <el-icon :size="56" class="fallback-icon"><Link /></el-icon>
          <p class="fallback-title">该视频来自{{ external.platform }}</p>
          <el-button type="primary" round class="external-btn" @click="openExternal">
            前往{{ external.platform }}观看
          </el-button>
          <p class="fallback-sub">{{ video.fileUrl }}</p>
        </div>
        <!-- 站内视频 -->
        <template v-else>
          <video v-if="!videoFailed" :src="video.fileUrl" controls preload="metadata"
            class="video-player" @error="videoFailed = true">
            您的浏览器不支持视频播放
          </video>
          <div v-else class="player-fallback">
            <el-icon :size="64" class="fallback-icon"><VideoPlay /></el-icon>
            <p class="fallback-title">视频即将上线</p>
            <p class="fallback-sub">该视频正在处理中，敬请期待</p>
          </div>
        </template>
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getVideoById, toggleVideoLike, recordVideoView } from '@/api/video'

const route = useRoute()
const loading = ref(false)
const video = ref(null)
const liked = ref(false)
const videoFailed = ref(false)
let viewTimer = null
let reportTimer = null
let viewSessionId = null
let pendingDelta = 0

// 外链来源解析：B 站 / YouTube 可提取出内嵌播放地址，其余平台跳转观看
const external = computed(() => {
  if (!video.value || video.value.sourceType !== 1) return null
  const url = video.value.fileUrl || ''
  const bv = url.match(/BV[0-9A-Za-z]{10}/)?.[0]
  if (bv && /bilibili\.com|b23\.tv/.test(url)) {
    return { kind: 'bilibili', platform: 'B站', embed: null }
  }
  const yt = url.match(/(?:youtube\.com\/(?:watch\?v=|embed\/)|youtu\.be\/)([0-9A-Za-z_-]{11})/)?.[1]
  if (yt) {
    return { kind: 'youtube', platform: 'YouTube', embed: `https://www.youtube.com/embed/${yt}` }
  }
  return { kind: 'other', platform: '外部平台', embed: null }
})

function openExternal() {
  if (video.value?.fileUrl) window.open(video.value.fileUrl, '_blank', 'noopener')
}

async function handleLike() {
  try {
    const res = await toggleVideoLike(video.value.id)
    liked.value = res?.isLiked ?? !liked.value
    ElMessage.success(res?.message || (liked.value ? '点赞成功' : '已取消'))
  } catch {}
}

// ===== 观看时长会话化上报 =====
// 进入详情页生成会话ID并复用：首次上报计一次观看，之后心跳只累加时长；
// 页面可见期间每 5 秒累计增量，每 30 秒批量上报，切后台/离开立即上报

function newViewSessionId() {
  return crypto?.randomUUID
    ? crypto.randomUUID()
    : `v-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function flushWatchTime() {
  const delta = pendingDelta
  pendingDelta = 0
  if (delta <= 0 || !video.value || !viewSessionId) return
  recordVideoView(video.value.id, viewSessionId, delta).catch(() => {})
}

// 离开页面兜底：sendBeacon 不携带认证头（该接口后端已放行），
// 避免 axios 请求在页面卸载时被中断导致时长丢失
function reportOnLeave() {
  const delta = pendingDelta
  if (delta <= 0 || !video.value || !viewSessionId) return
  pendingDelta = 0
  const params = new URLSearchParams({ sessionId: viewSessionId, viewDuration: String(delta) })
  if (navigator.sendBeacon) {
    navigator.sendBeacon(`/api/videos/${video.value.id}/view?${params.toString()}`)
  } else {
    recordVideoView(video.value.id, viewSessionId, delta).catch(() => {})
  }
}

function onVisibilityChange() {
  // 切到后台立即上报，避免后台期间误累计；回前台后心跳继续累计
  if (document.visibilityState === 'hidden') flushWatchTime()
}

onMounted(async () => {
  loading.value = true
  try {
    video.value = await getVideoById(Number(route.params.id))
    liked.value = video.value?.isLiked || false
    viewSessionId = newViewSessionId()
    // 建立观看会话（观次 +1）
    if (video.value) recordVideoView(video.value.id, viewSessionId, 0).catch(() => {})
    viewTimer = setInterval(() => {
      if (document.visibilityState === 'visible') pendingDelta += 5
    }, 5000)
    reportTimer = setInterval(flushWatchTime, 30000)
    document.addEventListener('visibilitychange', onVisibilityChange)
    window.addEventListener('pagehide', reportOnLeave)
    window.addEventListener('beforeunload', reportOnLeave)
  } finally { loading.value = false }
})

onBeforeUnmount(() => {
  if (viewTimer) clearInterval(viewTimer)
  if (reportTimer) clearInterval(reportTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('pagehide', reportOnLeave)
  window.removeEventListener('beforeunload', reportOnLeave)
  flushWatchTime()
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
  .video-iframe {
    width: 100%;
    aspect-ratio: 16 / 9;
    max-height: 480px;
    display: block;
    border: none;
  }
  .embed-fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 8px 12px;
    color: rgba(255, 255, 255, 0.7);
    font-size: 12px;
    background: rgba(0, 0, 0, 0.35);
  }
  .external-fallback {
    gap: 12px;
    .external-btn { margin: 4px 0; }
    .fallback-sub {
      font-size: 12px;
      opacity: 0.5;
      max-width: 80%;
      word-break: break-all;
      text-align: center;
    }
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
