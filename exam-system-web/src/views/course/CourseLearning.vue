<template>
  <div class="page-container course-learning" v-loading="initialLoading">
    <div v-if="course" class="learning-page">
      <div class="learning-heading">
        <el-button link type="primary" class="back-link" @click="router.push('/my/courses')"><el-icon><ArrowLeft /></el-icon> 我的课程</el-button>
        <div class="course-heading-row">
          <div>
            <span class="eyebrow">COURSE LEARNING</span>
            <h1>{{ course.name }}</h1>
            <p>{{ course.description || '按照章节目录完成课程学习。' }}</p>
          </div>
          <div class="course-progress-summary">
            <el-progress type="circle" :percentage="course.progressPercent || 0" :width="74" :stroke-width="7" />
            <span>课程进度</span>
          </div>
        </div>
      </div>

      <el-alert v-if="loadError" type="error" :closable="false" show-icon title="课程内容加载失败" description="请检查网络后重新加载。">
        <template #default><el-button size="small" @click="loadCourse">重新加载</el-button></template>
      </el-alert>

      <div v-else class="learning-layout">
        <aside class="chapter-sidebar">
          <header class="sidebar-heading"><div><span>LEARNING PATH</span><h2>课程目录</h2></div><el-tag size="small" effect="plain">{{ chapters.length }} 章</el-tag></header>
          <el-empty v-if="!chapters.length" description="课程还没有章节" :image-size="80" />
          <div v-else class="chapter-list">
            <section v-for="(chapter, index) in chapters" :key="chapter.chapterId" class="chapter-group">
              <button class="chapter-selector" :class="{ active: selectedChapterId === chapter.chapterId }" @click="selectChapter(chapter)">
                <span class="chapter-state" :class="chapter.completed ? 'is-complete' : chapter.completedVideoCount ? 'is-progress' : ''">
                  <el-icon v-if="chapter.completed"><CircleCheckFilled /></el-icon>
                  <span v-else>{{ String(index + 1).padStart(2, '0') }}</span>
                </span>
                <span class="chapter-label"><strong>{{ chapter.title }}</strong><small>{{ chapter.completedVideoCount || 0 }} / {{ chapter.videoCount || 0 }} 视频完成</small></span>
                <el-icon class="chapter-chevron"><ArrowRight /></el-icon>
              </button>
              <div v-if="selectedChapterId === chapter.chapterId && chapterVideos.length" class="chapter-video-list">
                <button v-for="video in chapterVideos" :key="video.chapterVideoId" class="video-selector" :class="{ active: selectedVideoId === video.chapterVideoId }" @click="selectVideo(video)">
                  <el-icon :class="video.completed ? 'completed-icon' : 'pending-icon'"><CircleCheckFilled v-if="video.completed" /><VideoPlay v-else /></el-icon>
                  <span>{{ video.title }}</span>
                  <el-tag v-if="video.required" size="small" type="info" effect="plain">必学</el-tag>
                </button>
              </div>
            </section>
          </div>
          <div v-if="chapters.length" class="sidebar-progress">
            <div><span>课程完成</span><strong>{{ course.chapterCompleted || 0 }} / {{ course.chapterTotal || chapters.length }}</strong></div>
            <el-progress :percentage="course.progressPercent || 0" :stroke-width="6" :show-text="false" />
          </div>
        </aside>

        <main class="learning-content" v-loading="chapterLoading">
          <template v-if="activeChapter">
            <div class="content-breadcrumb"><span>{{ activeChapter.title }}</span><el-icon><ArrowRight /></el-icon><strong>{{ activeVideo?.title || '章节介绍' }}</strong></div>
            <div v-if="activeVideo" class="lesson-card">
              <div class="player-shell">
                <div v-if="!activeVideo.fileUrl" class="player-error"><el-icon><VideoPlay /></el-icon><strong>视频暂不可用</strong><span>请联系课程教师。</span></div>
                <template v-else-if="activeVideo.sourceType === 0">
                  <video ref="playerRef" :key="activeVideo.chapterVideoId" :src="activeVideo.fileUrl" controls playsinline preload="metadata" class="lesson-player"
                    @timeupdate="handleTimeUpdate" @playing="handlePlay" @pause="handlePause" @ended="handlePause" @waiting="handleSeeking" @seeking="handleSeeking" @seeked="handleSeeked" @error="playerFailed = true">
                    你的浏览器不支持视频播放。
                  </video>
                  <div v-if="playerFailed" class="player-error"><el-icon><VideoPlay /></el-icon><strong>视频暂时无法播放</strong><span>请稍后重试或联系课程教师。</span></div>
                  <div class="player-overlay-tag"><el-icon><VideoPlay /></el-icon> 站内课程视频</div>
                </template>
                <div v-else class="external-lesson">
                  <div class="external-art"><el-icon><Link /></el-icon><span>EXTERNAL LESSON</span></div>
                  <h2>{{ activeVideo.title }}</h2>
                  <p>外部视频在新窗口打开。打开后，本页可见时累计停留；系统无法读取外站的播放进度。</p>
                  <el-button type="primary" size="large" @click="openExternal(activeVideo)"><el-icon><TopRight /></el-icon> 打开视频</el-button>
                  <div v-if="!activeVideo.completed" class="external-countdown">
                    <el-icon><Clock /></el-icon>
                    <template v-if="externalRemaining > 0">还需停留约 <strong>{{ formatDuration(externalRemaining) }}</strong>（按同步结果更新）</template>
                    <template v-else>已达到 60 秒停留要求，可以确认完成</template>
                  </div>
                </div>
              </div>
              <div class="lesson-details">
                <div>
                  <div class="lesson-title-row"><h2>{{ activeVideo.title }}</h2><el-tag v-if="activeVideo.required" type="warning" effect="light">必学视频</el-tag></div>
                  <p class="lesson-meta"><el-icon><Reading /></el-icon>{{ activeChapter.title }}<span class="meta-separator">·</span><el-icon><Clock /></el-icon>{{ watchedLabel }}</p>
                </div>
                <div class="lesson-actions">
                  <el-tag v-if="activeVideo.completed" type="success" effect="light"><el-icon><CircleCheckFilled /></el-icon> 已完成</el-tag>
                  <el-button v-else-if="activeVideo.sourceType === 1" type="primary" :disabled="externalRemaining > 0" :loading="completionSaving" @click="completeExternal">
                    <el-icon><CircleCheck /></el-icon> 完成学习
                  </el-button>
                  <el-tag v-else-if="activeVideo.fileUrl" type="info" effect="light">播放满 {{ requiredLabel }} 自动完成</el-tag>
                </div>
              </div>
              <div v-if="activeVideo.sourceType === 0" class="lesson-progress-note">
                <span>完成要求：{{ activeVideo.completionRatio || 70 }}% 视频时长</span>
                <span>累计观看 {{ formatDuration(activeVideo.watchedSeconds || 0) }} / {{ requiredLabel }}</span>
              </div>
            </div>
            <el-empty v-else description="选择一个视频开始学习" />
            <div class="lesson-navigation">
              <el-button :disabled="!previousVideo" @click="previousVideo && selectVideo(previousVideo)"><el-icon><ArrowLeft /></el-icon> 上一个</el-button>
              <span>{{ selectedVideoIndex + 1 }} / {{ chapterVideos.length }}</span>
              <el-button type="primary" plain :disabled="!nextVideo" @click="nextVideo && selectVideo(nextVideo)">下一个 <el-icon><ArrowRight /></el-icon></el-button>
            </div>
          </template>
          <el-empty v-else description="从左侧课程目录选择章节" />
        </main>
      </div>
    </div>
    <el-empty v-else-if="!initialLoading" description="课程不存在、尚未开放或暂不可访问" :image-size="120">
      <el-button type="primary" @click="router.push('/my/courses')">返回我的课程</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ArrowRight, CircleCheck, CircleCheckFilled, Clock, Link, Reading, TopRight, VideoPlay,
} from '@element-plus/icons-vue'
import {
  completeExternalLearning, getMyCourse, getMyCourseChapter, sendLearningHeartbeat,
} from '@/api/course'

const route = useRoute()
const router = useRouter()
const courseId = computed(() => String(route.params.id || ''))
const initialLoading = ref(true)
const chapterLoading = ref(false)
const loadError = ref(false)
const course = ref(null)
const chapters = ref([])
const selectedChapterId = ref(null)
const chapterVideos = ref([])
const selectedVideoId = ref(null)
const playerRef = ref(null)
const playerFailed = ref(false)
const completionSaving = ref(false)
const externalSessionSeconds = ref(0)

let heartbeatTimer = null
let activeSession = null
let lastPlaybackPosition = null
let lastPlaybackSample = null
let externalVisibleSince = null
let chapterRequest = 0

const activeChapter = computed(() => chapters.value.find((chapter) => chapter.chapterId === selectedChapterId.value) || null)
const activeVideo = computed(() => chapterVideos.value.find((video) => video.chapterVideoId === selectedVideoId.value) || null)
const selectedVideoIndex = computed(() => chapterVideos.value.findIndex((video) => video.chapterVideoId === selectedVideoId.value))
const previousVideo = computed(() => chapterVideos.value[selectedVideoIndex.value - 1] || null)
const nextVideo = computed(() => chapterVideos.value[selectedVideoIndex.value + 1] || null)
const externalRemaining = computed(() => Math.max(0, 60 - externalSessionSeconds.value))
const watchedLabel = computed(() => {
  if (!activeVideo.value) return ''
  return activeVideo.value.sourceType === 1
    ? `本次停留 ${formatDuration(externalSessionSeconds.value)}`
    : `已观看 ${formatDuration(activeVideo.value.watchedSeconds || 0)}`
})
const requiredLabel = computed(() => formatDuration(activeVideo.value?.requiredSeconds || 0))

function formatDuration(seconds) {
  const safe = Math.max(0, Math.floor(Number(seconds) || 0))
  const minutes = Math.floor(safe / 60)
  const remainder = safe % 60
  return `${minutes}:${String(remainder).padStart(2, '0')}`
}

function newSessionId() {
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function visible() {
  return document.visibilityState === 'visible'
}

async function loadCourse() {
  const id = courseId.value
  const previousChapter = course.value?.courseId?.toString() === id ? selectedChapterId.value : null
  stopCurrentHeartbeat()
  chapterRequest += 1
  initialLoading.value = true
  loadError.value = false
  course.value = null
  chapters.value = []
  chapterVideos.value = []
  selectedChapterId.value = null
  selectedVideoId.value = null
  try {
    if (!/^[1-9]\d*$/.test(id)) return
    const result = await getMyCourse(id)
    if (id !== courseId.value) return
    course.value = result
    chapters.value = result?.chapters || []
    if (!chapters.value.length) {
      selectedChapterId.value = null
      chapterVideos.value = []
      selectedVideoId.value = null
      return
    }
    const chosen = chapters.value.find(chapter => chapter.chapterId === previousChapter) || chapters.value[0]
    await selectChapter(chosen, false)
  } catch {
    if (id !== courseId.value) return
    loadError.value = true
    course.value = null
  } finally {
    if (id === courseId.value) initialLoading.value = false
  }
}

async function selectChapter(chapter, resetSelection = true) {
  if (!chapter) return
  const request = ++chapterRequest
  const previousVideoId = selectedVideoId.value
  stopCurrentHeartbeat()
  selectedChapterId.value = chapter.chapterId
  chapterVideos.value = []
  selectedVideoId.value = null
  playerFailed.value = false
  chapterLoading.value = true
  try {
    const result = await getMyCourseChapter(courseId.value, chapter.chapterId)
    if (chapterRequest !== request) return
    chapterVideos.value = result?.videos || []
    const next = resetSelection
      ? chapterVideos.value[0]
      : chapterVideos.value.find(item => item.chapterVideoId === previousVideoId) || chapterVideos.value[0]
    selectedVideoId.value = next?.chapterVideoId || null
  } catch {
    if (chapterRequest !== request) return
    chapterVideos.value = []
    selectedVideoId.value = null
  } finally {
    if (chapterRequest === request) chapterLoading.value = false
  }
}

function selectVideo(video) {
  if (!video || video.chapterVideoId === selectedVideoId.value) return
  stopCurrentHeartbeat()
  selectedVideoId.value = video.chapterVideoId
  playerFailed.value = false
}

function beginVideoSession(video) {
  if (activeSession || !video?.fileUrl) return
  activeSession = {
    id: newSessionId(),
    video,
    chapterId: selectedChapterId.value,
    sequence: 0,
    pending: 0,
    retry: null,
    inFlight: null,
    failures: 0,
  }
  flushHeartbeat(activeSession)
}

function handlePlay() {
  if (!visible() || activeVideo.value?.sourceType !== 0) return
  beginVideoSession(activeVideo.value)
  lastPlaybackPosition = playerRef.value?.currentTime ?? null
  lastPlaybackSample = performance.now()
}

function handlePause() {
  if (playerRef.value?.ended) handleTimeUpdate()
  handleSeeking()
  flushHeartbeat(activeSession)
}

function handleSeeking() {
  lastPlaybackPosition = null
  lastPlaybackSample = null
}

function handleSeeked() {
  if (playerRef.value && !playerRef.value.paused) handlePlay()
}

function handleTimeUpdate() {
  if (!visible() || !activeSession || lastPlaybackSample === null ||
    (playerRef.value?.paused && !playerRef.value?.ended) || playerRef.value?.seeking ||
    activeSession.video.sourceType !== 0) return
  const current = Number(playerRef.value?.currentTime)
  if (!Number.isFinite(current)) return
  const now = performance.now()
  if (lastPlaybackPosition !== null && lastPlaybackSample !== null && current >= lastPlaybackPosition) {
    activeSession.pending += Math.min(current - lastPlaybackPosition, (now - lastPlaybackSample) / 1000, 1)
  }
  lastPlaybackPosition = current
  lastPlaybackSample = now
}

function sampleExternal() {
  if (externalVisibleSince === null || activeSession?.video.sourceType !== 1) return
  const now = performance.now()
  activeSession.pending += Math.min(60, (now - externalVisibleSince) / 1000)
  externalVisibleSince = now
}

function flushHeartbeat(session) {
  if (!session || !visible()) return Promise.resolve()
  if (session.inFlight) return session.inFlight
  if (!session.retry) {
    const seconds = session.sequence === 0 ? 0 : Math.min(60, Math.floor(session.pending))
    if (!seconds && session.sequence !== 0) return Promise.resolve()
    session.pending -= seconds
    session.retry = { sessionId: session.id, sequence: session.sequence, viewDuration: seconds }
  }
  session.inFlight = sendLearningHeartbeat(session.video.chapterVideoId, session.retry).then(result => {
    session.sequence += 1
    session.retry = null
    session.failures = 0
    if (activeSession === session && result) applyHeartbeatResult(session, result)
  }).catch(() => {
    session.failures += 1
    if (activeSession === session && session.failures === 3) ElMessage.warning('学习进度同步暂时不稳定，正在重试')
  }).finally(() => {
    session.inFlight = null
  })
  return session.inFlight
}

function applyHeartbeatResult(session, result) {
  const target = chapterVideos.value.find(item => item.chapterVideoId === session.video.chapterVideoId)
  if (target) {
    target.watchedSeconds = result.watchedSeconds ?? target.watchedSeconds
    target.completed = result.completed ?? target.completed
  }
  if (target?.sourceType === 1) externalSessionSeconds.value = result.sessionWatchedSeconds ?? externalSessionSeconds.value
  const chapter = chapters.value.find(item => item.chapterId === session.chapterId)
  if (chapter) {
    chapter.completed = result.chapterCompleted ?? chapter.completed
    chapter.completedVideoCount = chapterVideos.value.filter(item => item.completed).length
  }
  if (!course.value) return
  course.value.chapterCompleted = chapters.value.filter((item) => item.completed).length
  course.value.progressPercent = course.value.chapterTotal
    ? Math.floor(course.value.chapterCompleted / course.value.chapterTotal * 100)
    : 0
}

function onVisibilityChange() {
  if (visible()) {
    if (activeSession?.video.sourceType === 1) externalVisibleSince = performance.now()
    else if (playerRef.value && !playerRef.value.paused && !playerRef.value.seeking) handlePlay()
    return
  }
  sampleExternal()
  externalVisibleSince = null
  handleSeeking()
}

function openExternal(video) {
  if (!video?.fileUrl) return
  beginVideoSession(video)
  if (visible() && externalVisibleSince === null) externalVisibleSince = performance.now()
  window.open(video.fileUrl, '_blank', 'noopener,noreferrer')
}

async function completeExternal() {
  const session = activeSession
  if (!session || session.video.sourceType !== 1 || externalRemaining.value > 0 || completionSaving.value) return
  completionSaving.value = true
  try {
    const result = await completeExternalLearning(session.video.chapterVideoId, session.id)
    if (session === activeSession) {
      applyHeartbeatResult(session, result)
      ElMessage.success('已完成本视频学习')
    }
  } catch {
    // 接口返回的后端 message 原样由 request.js 提示，例如当前会话剩余时间。
  } finally {
    completionSaving.value = false
  }
}

function stopCurrentHeartbeat() {
  handleSeeking()
  externalVisibleSince = null
  externalSessionSeconds.value = 0
  activeSession = null
}

watch(courseId, loadCourse)
onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  heartbeatTimer = setInterval(() => {
    if (!visible()) return
    sampleExternal()
    flushHeartbeat(activeSession)
  }, 10000)
  loadCourse()
})
onBeforeUnmount(() => {
  chapterRequest += 1
  document.removeEventListener('visibilitychange', onVisibilityChange)
  if (heartbeatTimer) clearInterval(heartbeatTimer)
  stopCurrentHeartbeat()
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.course-learning {
  max-width: 1360px;
  .learning-heading { margin-bottom: 22px; }
  .back-link { margin-bottom: 12px; }
  .course-heading-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
  .eyebrow { color: $primary; font-size: 10px; font-weight: 700; letter-spacing: 1.6px; }
  h1 { margin-top: 6px; font-size: 25px; letter-spacing: -.4px; }
  .course-heading-row p { margin-top: 6px; color: $text-muted; font-size: 13px; }
  .course-progress-summary { display: flex; align-items: center; gap: 12px; color: $text-muted; font-size: 12px; }
  .learning-layout { display: grid; grid-template-columns: 310px minmax(0, 1fr); gap: 20px; align-items: start; }
  .chapter-sidebar { @include glass; position: sticky; top: 82px; border-radius: $radius-lg; overflow: hidden; }
  .sidebar-heading { display: flex; align-items: center; justify-content: space-between; padding: 18px 18px 14px; border-bottom: 1px solid $border; }
  .sidebar-heading span { color: $text-muted; font-size: 9px; font-weight: 700; letter-spacing: 1px; }
  .sidebar-heading h2 { margin-top: 4px; font-size: 16px; }
  .chapter-list { max-height: calc(100vh - 320px); overflow: auto; padding: 8px; }
  .chapter-group + .chapter-group { margin-top: 4px; }
  .chapter-selector { display: flex; align-items: center; gap: 11px; width: 100%; padding: 11px 9px; border: 0; border-radius: $radius-md; background: transparent; text-align: left; cursor: pointer; transition: background $transition-fast; }
  .chapter-selector:hover, .chapter-selector.active { background: $bg-soft; }
  .chapter-selector.active .chapter-label strong { color: $primary; }
  .chapter-state { display: grid; place-items: center; flex: 0 0 30px; width: 30px; height: 30px; border: 1px solid $border-strong; border-radius: 50%; color: $text-muted; font-size: 10px; font-weight: 650; font-variant-numeric: tabular-nums; }
  .chapter-state.is-complete { border-color: transparent; color: $success; font-size: 17px; }
  .chapter-state.is-progress { border-color: $primary-lighter; color: $primary; }
  .chapter-label { display: grid; gap: 4px; min-width: 0; flex: 1; }
  .chapter-label strong { overflow: hidden; color: $text-primary; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
  .chapter-label small { color: $text-muted; font-size: 10px; }
  .chapter-chevron { color: $text-muted; font-size: 12px; }
  .chapter-video-list { margin: 2px 0 8px 39px; border-left: 1px solid $border; padding-left: 8px; }
  .video-selector { display: flex; align-items: center; gap: 7px; width: 100%; padding: 8px 6px; border: 0; border-radius: $radius-sm; background: transparent; text-align: left; color: $text-secondary; font-size: 11px; cursor: pointer; }
  .video-selector > span { overflow: hidden; flex: 1; text-overflow: ellipsis; white-space: nowrap; }
  .video-selector:hover, .video-selector.active { background: $primary-soft; color: $primary; }
  .video-selector .completed-icon { color: $success; }
  .video-selector .pending-icon { color: $text-muted; }
  .video-selector :deep(.el-tag) { height: 18px; padding: 0 5px; font-size: 9px; }
  .sidebar-progress { padding: 14px 18px 17px; border-top: 1px solid $border; }
  .sidebar-progress > div { display: flex; justify-content: space-between; margin-bottom: 9px; color: $text-muted; font-size: 11px; }
  .sidebar-progress strong { color: $text-primary; }
  .learning-content { min-width: 0; }
  .content-breadcrumb { display: flex; align-items: center; gap: 8px; margin: 2px 0 12px; color: $text-muted; font-size: 12px; }
  .content-breadcrumb strong { overflow: hidden; color: $text-primary; text-overflow: ellipsis; white-space: nowrap; }
  .lesson-card { @include glass; border-radius: $radius-lg; overflow: hidden; }
  .player-shell { position: relative; min-height: 390px; display: grid; place-items: center; overflow: hidden; background: #111827; }
  .lesson-player { display: block; width: 100%; max-height: min(68vh, 680px); background: #0b1220; }
  .player-overlay-tag { position: absolute; top: 14px; left: 14px; display: flex; align-items: center; gap: 6px; padding: 6px 9px; border: 1px solid rgba(255,255,255,.18); border-radius: 20px; background: rgba(15,23,42,.62); color: #fff; font-size: 10px; pointer-events: none; }
  .player-error { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 10px; background: rgba(15,23,42,.92); color: #fff; }
  .player-error .el-icon { color: $primary-lighter; font-size: 34px; }
  .player-error span { color: rgba(255,255,255,.6); font-size: 12px; }
  .external-lesson { width: 100%; min-height: 390px; display: flex; align-items: center; justify-content: center; flex-direction: column; padding: 32px; color: #fff; text-align: center; background: radial-gradient(circle at 50% 25%, #26324a, #111827 65%); }
  .external-art { display: grid; place-items: center; gap: 10px; color: $primary-lighter; }
  .external-art .el-icon { font-size: 48px; }
  .external-art span { color: rgba(255,255,255,.45); font-size: 9px; font-weight: 700; letter-spacing: 1.6px; }
  .external-lesson h2 { margin-top: 20px; font-size: 19px; }
  .external-lesson p { max-width: 440px; margin: 10px auto 18px; color: rgba(255,255,255,.62); font-size: 12px; line-height: 1.7; }
  .external-countdown { display: flex; align-items: center; gap: 6px; margin-top: 18px; color: rgba(255,255,255,.65); font-size: 11px; }
  .external-countdown strong { color: #fff; font-variant-numeric: tabular-nums; }
  .lesson-details { display: flex; justify-content: space-between; align-items: center; gap: 18px; padding: 18px 20px; }
  .lesson-title-row { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }
  .lesson-title-row h2 { font-size: 17px; font-weight: 650; }
  .lesson-meta { display: flex; align-items: center; gap: 6px; margin-top: 9px; color: $text-muted; font-size: 11px; }
  .meta-separator { margin: 0 2px; }
  .lesson-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
  .lesson-actions :deep(.el-tag .el-icon) { vertical-align: middle; margin-right: 3px; }
  .lesson-progress-note { display: flex; justify-content: space-between; gap: 12px; padding: 10px 20px 14px; border-top: 1px solid $border; color: $text-muted; font-size: 10px; }
  .lesson-navigation { display: flex; align-items: center; justify-content: space-between; margin-top: 14px; color: $text-muted; font-size: 11px; }
  :deep(.chapter-sidebar .el-empty) { padding: 20px 0; }
}

@media (max-width: 900px) {
  .course-learning .learning-layout { grid-template-columns: 260px minmax(0, 1fr); gap: 14px; }
  .course-learning .player-shell, .course-learning .external-lesson { min-height: 300px; }
}
@media (max-width: 700px) {
  .course-learning .learning-layout { grid-template-columns: 1fr; }
  .course-learning .chapter-sidebar { position: static; }
  .course-learning .chapter-list { max-height: 300px; }
  .course-learning .player-shell, .course-learning .external-lesson { min-height: 250px; }
  .course-learning .lesson-details { align-items: flex-start; flex-direction: column; }
}
</style>
