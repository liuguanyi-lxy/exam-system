<template>
  <div class="home">
    <!-- 轮播图 -->
    <el-carousel v-if="banners.length" height="340px" :interval="5000" class="home-banner" indicator-position="outside">
      <el-carousel-item v-for="b in banners" :key="b.id">
        <div class="banner-slide" :style="{ backgroundImage: `url(${b.imageUrl})` }">
          <div class="banner-overlay"></div>
          <div class="banner-text">
            <h2>{{ b.title }}</h2>
            <p>{{ b.description }}</p>
            <el-button v-if="b.linkUrl" class="btn-gradient banner-btn" @click="goLink(b.linkUrl)">
              了解更多
              <el-icon style="margin-left:4px"><Right /></el-icon>
            </el-button>
          </div>
        </div>
      </el-carousel-item>
    </el-carousel>

    <!-- 公告 -->
    <div class="notice-list" v-if="notices.length">
      <div v-for="n in notices" :key="n.id" class="notice-item slide-up"
        :class="`priority-${n.priority || 0}`">
        <el-icon class="notice-icon"><Bell /></el-icon>
        <div class="notice-content">
          <span class="notice-title">{{ n.title }}</span>
          <span class="notice-desc">{{ n.content }}</span>
        </div>
      </div>
    </div>

    <!-- 在线考试入口 -->
    <section class="section exam-entry">
      <div class="section-head">
        <h3 class="section-title"><span class="title-bar"></span>在线考试</h3>
        <el-button type="primary" link @click="$router.push('/exam/list')">
          全部考试 <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <el-empty v-if="!exams.length" description="暂无已发布考试" :image-size="80" />
      <div v-else class="exam-cards">
        <div v-for="p in exams" :key="p.id" class="exam-card card-hover" @click="$router.push(`/exam/start/${p.id}`)">
          <div class="exam-card-top">
            <span class="exam-type">📝</span>
            <el-tag size="small" type="success" effect="light">已发布</el-tag>
          </div>
          <div class="exam-name">{{ p.name }}</div>
          <div class="exam-meta">
            <span><el-icon><Clock /></el-icon>{{ p.duration }} 分钟</span>
            <span><el-icon><Document /></el-icon>{{ p.questionCount }} 题</span>
            <span><el-icon><Medal /></el-icon>{{ p.totalScore }} 分</span>
          </div>
        </div>
      </div>
    </section>

    <el-row :gutter="24" class="bottom-row">
      <!-- 热门题目 -->
      <el-col :xs="24" :md="14">
        <section class="section">
          <div class="section-head">
            <h3 class="section-title"><span class="title-bar title-bar-red"></span>热门题目</h3>
          </div>
          <div v-for="(q, i) in hotQuestions" :key="q.id" class="question-item">
            <span class="q-index" :class="`rank-${i + 1}`">{{ i + 1 }}</span>
            <el-tag size="small" effect="light"
              :type="q.difficulty === 'EASY' ? 'success' : q.difficulty === 'MEDIUM' ? 'warning' : 'danger'">
              {{ q.difficulty === 'EASY' ? '简单' : q.difficulty === 'MEDIUM' ? '中等' : '困难' }}
            </el-tag>
            <span class="q-title">{{ q.title }}</span>
            <span class="q-type">{{ q.type === 'CHOICE' ? '选择题' : q.type === 'JUDGE' ? '判断题' : '简答题' }}</span>
          </div>
          <el-empty v-if="!hotQuestions.length" description="暂无热门题目" />
        </section>
      </el-col>

      <!-- 热门视频 -->
      <el-col :xs="24" :md="10">
        <section class="section">
          <div class="section-head">
            <h3 class="section-title"><span class="title-bar title-bar-cyan"></span>热门视频</h3>
          </div>
          <div v-for="v in hotVideos" :key="v.id" class="video-item" @click="$router.push(`/videos/${v.id}`)">
            <div class="v-cover">
              <img v-if="v.coverUrl" :src="v.coverUrl" loading="lazy" />
              <span v-else class="v-placeholder"><el-icon :size="28"><VideoPlay /></el-icon></span>
              <span class="v-play"><el-icon><VideoPlay /></el-icon></span>
            </div>
            <div class="v-info">
              <div class="v-title">{{ v.title }}</div>
              <div class="v-meta">{{ v.viewCount || 0 }}次观看 · {{ v.likeCount || 0 }}赞</div>
            </div>
          </div>
          <el-empty v-if="!hotVideos.length" description="暂无热门视频" />
        </section>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getActiveBanners } from '@/api/banner'
import { getActiveNotices } from '@/api/notice'
import { getPopularQuestions } from '@/api/question'
import { getPopularVideos } from '@/api/video'
import { getPaperList } from '@/api/paper'

const banners = ref([])
const notices = ref([])
const hotQuestions = ref([])
const hotVideos = ref([])
const exams = ref([])

onMounted(async () => {
  try { const r = await getActiveBanners(); banners.value = r || []; } catch {}
  try { const r = await getActiveNotices(); notices.value = r || []; } catch {}
  try { const r = await getPopularQuestions(10); hotQuestions.value = r || []; } catch {}
  try { const r = await getPopularVideos(5); hotVideos.value = r || []; } catch {}
  try { const r = await getPaperList({ status: 'PUBLISHED' }); exams.value = (r || []).slice(0, 3); } catch {}
})

function goLink(url) {
  if (url && url.startsWith('http')) window.open(url, '_blank')
}
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

// 轮播图
.home-banner {
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 20px;
  box-shadow: $shadow-lg;
}
.banner-slide {
  height: 100%;
  background-size: cover;
  background-position: center;
  position: relative;
  .banner-overlay {
    position: absolute;
    inset: 0;
    background: linear-gradient(90deg, rgba(15, 23, 42, 0.75) 0%, rgba(15, 23, 42, 0.3) 55%, transparent 100%);
  }
  .banner-text {
    position: absolute;
    left: 48px;
    bottom: 40px;
    color: #fff;
    z-index: 1;
    max-width: 60%;
    h2 { font-size: 28px; margin-bottom: 8px; text-shadow: 0 2px 8px rgba(0,0,0,.4); }
    p { font-size: 14px; opacity: 0.9; margin-bottom: 14px; }
    .banner-btn { border-radius: 20px; padding: 6px 20px; }
  }
}

// 公告
.notice-list {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.notice-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 10px;
  border-left: 4px solid $info;
  box-shadow: $shadow-sm;
  transition: transform $transition-fast;
  &:hover { transform: translateX(4px); }
  &.priority-2 { border-left-color: $danger; .notice-icon { color: $danger; } }
  &.priority-1 { border-left-color: $warning; .notice-icon { color: $warning; } }
  .notice-icon { color: $info; flex-shrink: 0; }
  .notice-content {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
    .notice-title { font-weight: 600; font-size: 14px; white-space: nowrap; }
    .notice-desc {
      color: $text-muted;
      font-size: 13px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

// 区块
.section {
  @include glass;
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 20px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 700;
  color: $text-primary;
  .title-bar {
    width: 4px;
    height: 20px;
    border-radius: 4px;
    background: $gradient-primary;
  }
  .title-bar-red { background: linear-gradient(180deg, #ef4444, #f97316); }
  .title-bar-cyan { background: linear-gradient(180deg, #06b6d4, #3b82f6); }
}

// 考试卡片
.exam-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  @media (max-width: 768px) { grid-template-columns: 1fr; }
}
.exam-card {
  border: 1px solid $border;
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  background: #fff;
  @include top-accent;
  .exam-card-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
    .exam-type { font-size: 28px; }
  }
  .exam-name {
    font-size: 15px;
    font-weight: 600;
    margin-bottom: 10px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .exam-meta {
    display: flex;
    gap: 14px;
    font-size: 12px;
    color: $text-muted;
    span { display: flex; align-items: center; gap: 4px; }
  }
}

// 热门题目
.question-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  transition: background $transition-fast;
  &:hover { background: rgba(99, 102, 241, 0.05); }
  &:last-child { border-bottom: none; }
  .q-index {
    width: 24px;
    height: 24px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    background: rgba(99, 102, 241, 0.1);
    color: $primary;
    flex-shrink: 0;
    &.rank-1 { background: linear-gradient(135deg, #f59e0b, #fbbf24); color: #fff; }
    &.rank-2 { background: linear-gradient(135deg, #94a3b8, #cbd5e1); color: #fff; }
    &.rank-3 { background: linear-gradient(135deg, #d97706, #f59e0b); color: #fff; }
  }
  .q-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
  .q-type { color: $text-muted; font-size: 12px; white-space: nowrap; }
}

// 热门视频
.video-item {
  display: flex;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  cursor: pointer;
  transition: background $transition-fast;
  &:hover { background: rgba(99, 102, 241, 0.05); }
  &:last-child { border-bottom: none; }
  .v-cover {
    width: 110px;
    height: 68px;
    border-radius: 8px;
    overflow: hidden;
    background: $gradient-sidebar;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    position: relative;
    img { width: 100%; height: 100%; object-fit: cover; transition: transform $transition-normal; }
    .v-placeholder { color: #a5b4fc; }
    .v-play {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      background: rgba(0, 0, 0, 0.3);
      opacity: 0;
      transition: opacity $transition-fast;
      font-size: 20px;
    }
    &:hover {
      img { transform: scale(1.08); }
      .v-play { opacity: 1; }
    }
  }
  .v-title { font-size: 14px; font-weight: 500; margin-bottom: 4px; line-height: 1.4; }
  .v-meta { font-size: 12px; color: $text-muted; }
}
</style>
