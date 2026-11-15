<template>
  <div class="home">
    <!-- 轮播图 -->
    <el-carousel v-if="banners.length" height="300px" :interval="5000" class="home-banner" indicator-position="outside">
      <el-carousel-item v-for="b in banners" :key="b.id">
        <div class="banner-slide" :style="{ backgroundImage: `url(${b.imageUrl})` }">
          <div class="banner-overlay"></div>
          <div class="banner-text">
            <h2>{{ b.title }}</h2>
            <p>{{ b.description }}</p>
            <el-button v-if="b.linkUrl" class="banner-btn" @click="goLink(b.linkUrl)">
              了解更多
              <el-icon style="margin-left:4px"><Right /></el-icon>
            </el-button>
          </div>
        </div>
      </el-carousel-item>
    </el-carousel>

    <!-- 默认欢迎区：后台没有配置轮播图时展示 -->
    <section v-else class="home-hero">
      <div class="hero-copy">
        <span class="hero-badge">
          <el-icon :size="13"><MagicStick /></el-icon>
          AI 智能批阅
        </span>
        <h1>在线考试，从出卷到成绩一步到位</h1>
        <p>选择已发布的试卷开始答题，客观题自动判分、简答题 AI 批阅，考完即可查看结果与排名。</p>
        <div class="hero-actions">
          <el-button size="large" class="btn-gradient" @click="$router.push('/exam/list')">
            开始考试
            <el-icon style="margin-left:4px"><Right /></el-icon>
          </el-button>
          <el-button size="large" @click="$router.push('/ranking')">查看排行榜</el-button>
        </div>
      </div>

      <!-- 装饰：答题卡示意 -->
      <div class="hero-panel" aria-hidden="true">
        <div class="panel-card">
          <div class="panel-head">
            <span class="panel-label">第 1 题 / 共 20 题</span>
            <span class="panel-timer"><el-icon :size="12"><Timer /></el-icon> 29:58</span>
          </div>
          <div class="panel-question">下列哪一项属于 HTTP 幂等方法？</div>
          <div class="panel-option"><i>A</i> POST</div>
          <div class="panel-option selected"><i>B</i> GET</div>
          <div class="panel-option"><i>C</i> PATCH</div>
          <div class="panel-progress">
            <span style="width:62%"></span>
          </div>
        </div>
        <div class="panel-ghost"></div>
      </div>
    </section>

    <!-- 公告：首页只展示最新两条，避免把首屏挤满 -->
    <div class="notice-list" v-if="notices.length">
      <div v-for="n in notices.slice(0, 2)" :key="n.id" class="notice-item" :class="`priority-${n.priority || 0}`">
        <span class="notice-icon"><el-icon :size="15"><Bell /></el-icon></span>
        <div class="notice-content">
          <span class="notice-title">{{ n.title }}</span>
          <span class="notice-desc">{{ n.content }}</span>
        </div>
      </div>
    </div>

    <!-- 在线考试入口 -->
    <section class="section exam-entry">
      <div class="section-head">
        <h3 class="section-title">在线考试</h3>
        <router-link to="/exam/list" class="section-more">
          全部考试 <el-icon :size="12"><ArrowRight /></el-icon>
        </router-link>
      </div>
      <el-empty v-if="!exams.length" description="暂无已发布考试" :image-size="72" />
      <div v-else class="exam-cards">
        <div v-for="p in exams" :key="p.id" class="exam-card card-hover" @click="$router.push(`/exam/start/${p.id}`)">
          <div class="exam-card-top">
            <span class="exam-icon"><el-icon :size="17"><Document /></el-icon></span>
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

    <el-row :gutter="20" class="bottom-row">
      <!-- 热门题目 -->
      <el-col :xs="24" :md="14">
        <section class="section">
          <div class="section-head">
            <h3 class="section-title">热门题目</h3>
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
          <el-empty v-if="!hotQuestions.length" description="暂无热门题目" :image-size="72" />
        </section>
      </el-col>

      <!-- 热门视频 -->
      <el-col :xs="24" :md="10">
        <section class="section">
          <div class="section-head">
            <h3 class="section-title">热门视频</h3>
          </div>
          <div v-for="v in hotVideos" :key="v.id" class="video-item" @click="$router.push(`/videos/${v.id}`)">
            <div class="v-cover">
              <img v-if="v.coverUrl" :src="v.coverUrl" loading="lazy" />
              <span v-else class="v-placeholder"><el-icon :size="22"><VideoPlay /></el-icon></span>
              <span class="v-play"><el-icon><VideoPlay /></el-icon></span>
            </div>
            <div class="v-info">
              <div class="v-title">{{ v.title }}</div>
              <div class="v-meta">{{ v.viewCount || 0 }}次观看 · {{ v.likeCount || 0 }}赞</div>
            </div>
          </div>
          <el-empty v-if="!hotVideos.length" description="暂无热门视频" :image-size="72" />
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

// ---- 轮播图 ----
.home-banner {
  border-radius: $radius-xl;
  overflow: hidden;
  margin-bottom: 20px;
  border: 1px solid $border;

  :deep(.el-carousel__indicators--outside) {
    margin-top: 8px;
  }
  :deep(.el-carousel__button) {
    height: 4px;
    border-radius: 4px;
    background: $border-strong;
  }
  :deep(.el-carousel__indicator.is-active .el-carousel__button) {
    background: $primary;
  }
}

// ---- 默认欢迎区 ----
.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  align-items: center;
  gap: 40px;
  padding: 44px 40px;
  background: $surface;
  border: 1px solid $border;
  border-radius: $radius-xl;
  margin-bottom: 20px;
  overflow: hidden;

  .hero-copy {
    min-width: 0;

    .hero-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 10px;
      border-radius: 999px;
      background: $primary-soft;
      color: $primary-dark;
      font-size: 12px;
      font-weight: 500;
      margin-bottom: 16px;
    }
    h1 {
      font-size: 30px;
      line-height: 1.35;
      font-weight: 600;
      letter-spacing: -0.4px;
      color: $text-primary;
      margin-bottom: 12px;
    }
    p {
      font-size: 14px;
      line-height: 1.75;
      color: $text-secondary;
      max-width: 460px;
      margin-bottom: 24px;
    }
    .hero-actions {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }
  }

  .hero-panel {
    position: relative;
    display: flex;
    justify-content: center;

    .panel-ghost {
      position: absolute;
      inset: 14px -6px -14px 26px;
      border: 1px solid $border;
      border-radius: $radius-lg;
      background: $bg-soft;
      z-index: 0;
    }
    .panel-card {
      position: relative;
      z-index: 1;
      width: 100%;
      max-width: 340px;
      background: $surface;
      border: 1px solid $border;
      border-radius: $radius-lg;
      box-shadow: $shadow-lg;
      padding: 16px 18px;

      .panel-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding-bottom: 12px;
        margin-bottom: 14px;
        border-bottom: 1px solid $border;

        .panel-label { font-size: 12px; color: $text-muted; }
        .panel-timer {
          display: inline-flex;
          align-items: center;
          gap: 3px;
          font-size: 12px;
          font-weight: 600;
          color: $primary-dark;
          background: $primary-soft;
          padding: 2px 8px;
          border-radius: $radius-sm;
          font-variant-numeric: tabular-nums;
        }
      }
      .panel-question {
        font-size: 13.5px;
        font-weight: 500;
        line-height: 1.6;
        color: $text-primary;
        margin-bottom: 12px;
      }
      .panel-option {
        display: flex;
        align-items: center;
        gap: 9px;
        padding: 8px 10px;
        border: 1px solid $border;
        border-radius: $radius-sm;
        font-size: 12.5px;
        color: $text-secondary;
        margin-bottom: 7px;

        i {
          width: 18px;
          height: 18px;
          border-radius: 50%;
          border: 1px solid $border-strong;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 10px;
          font-style: normal;
          font-weight: 600;
          flex-shrink: 0;
        }
        &.selected {
          border-color: $primary;
          background: $primary-soft;
          color: $primary-dark;
          font-weight: 500;
          i {
            background: $primary;
            border-color: $primary;
            color: #fff;
          }
        }
      }
      .panel-progress {
        margin-top: 14px;
        height: 4px;
        border-radius: 4px;
        background: $border;
        overflow: hidden;
        span {
          display: block;
          height: 100%;
          border-radius: 4px;
          background: $primary;
        }
      }
    }
  }
}

.banner-slide {
  height: 100%;
  background-size: cover;
  background-position: center;
  position: relative;
  .banner-overlay {
    position: absolute;
    inset: 0;
    background: linear-gradient(90deg, rgba(15, 23, 42, 0.72) 0%, rgba(15, 23, 42, 0.25) 60%, transparent 100%);
  }
  .banner-text {
    position: absolute;
    left: 40px;
    bottom: 36px;
    color: #fff;
    z-index: 1;
    max-width: 60%;
    h2 { font-size: 24px; margin-bottom: 8px; color: #fff; }
    p { font-size: 13px; opacity: 0.85; margin-bottom: 14px; }
    .banner-btn {
      background: #fff;
      border-color: #fff;
      color: $text-primary;
      &:hover { background: rgba(255, 255, 255, 0.9); border-color: #fff; color: $text-primary; }
    }
  }
}

// ---- 公告 ----
.notice-list {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.notice-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: $surface;
  border: 1px solid $border;
  border-radius: $radius-md;

  .notice-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border-radius: 50%;
    background: $bg-soft;
    color: $text-secondary;
    flex-shrink: 0;
  }
  &.priority-2 .notice-icon { background: #fef3f2; color: $danger; }
  &.priority-1 .notice-icon { background: #fffaeb; color: $warning; }

  .notice-content {
    display: flex;
    align-items: baseline;
    gap: 10px;
    min-width: 0;
    .notice-title { font-weight: 500; font-size: 13.5px; color: $text-primary; white-space: nowrap; }
    .notice-desc {
      color: $text-muted;
      font-size: 13px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

// ---- 区块 ----
.section {
  background: $surface;
  border: 1px solid $border;
  border-radius: $radius-xl;
  padding: 20px;
  margin-bottom: 20px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}
.section-more {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 13px;
  color: $text-secondary;
  &:hover { color: $primary; }
}

// ---- 考试卡片 ----
.exam-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  @media (max-width: 768px) { grid-template-columns: 1fr; }
}
.exam-card {
  border: 1px solid $border;
  border-radius: $radius-lg;
  padding: 16px;
  cursor: pointer;
  background: $surface;
  @include card-hover(-2px, false);

  .exam-card-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    .exam-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: $radius-md;
      background: $bg-soft;
      color: $text-secondary;
    }
  }
  .exam-name {
    font-size: 14.5px;
    font-weight: 500;
    color: $text-primary;
    margin-bottom: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .exam-meta {
    display: flex;
    gap: 14px;
    font-size: 12px;
    color: $text-muted;
    padding-top: 12px;
    border-top: 1px solid $border;
    span { display: flex; align-items: center; gap: 4px; }
  }
}

// ---- 热门题目 ----
.question-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 6px;
  border-bottom: 1px solid $border;
  transition: background $transition-fast;
  border-radius: $radius-sm;
  &:hover { background: $bg-soft; }
  &:last-child { border-bottom: none; }
  .q-index {
    width: 20px;
    font-size: 13px;
    font-weight: 600;
    color: $text-muted;
    text-align: center;
    flex-shrink: 0;
    &.rank-1 { color: $warning; }
    &.rank-2 { color: $text-secondary; }
    &.rank-3 { color: #b54708; }
  }
  .q-title {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13.5px;
    color: $text-primary;
  }
  .q-type { color: $text-muted; font-size: 12px; white-space: nowrap; }
}

// ---- 热门视频 ----
.video-item {
  display: flex;
  gap: 12px;
  padding: 9px 6px;
  border-bottom: 1px solid $border;
  cursor: pointer;
  border-radius: $radius-sm;
  transition: background $transition-fast;
  &:hover { background: $bg-soft; }
  &:last-child { border-bottom: none; }
  .v-cover {
    width: 104px;
    height: 64px;
    border-radius: $radius-sm;
    overflow: hidden;
    background: $bg-soft;
    border: 1px solid $border;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    position: relative;
    img { width: 100%; height: 100%; object-fit: cover; }
    .v-placeholder { color: $text-muted; }
    .v-play {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      background: rgba(15, 23, 42, 0.35);
      opacity: 0;
      transition: opacity $transition-fast;
      font-size: 18px;
    }
    &:hover .v-play { opacity: 1; }
  }
  .v-info { min-width: 0; }
  .v-title {
    font-size: 13.5px;
    font-weight: 500;
    color: $text-primary;
    margin-bottom: 4px;
    line-height: 1.45;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .v-meta { font-size: 12px; color: $text-muted; }
}

// ---- 响应式 ----
@media (max-width: 900px) {
  .home-hero {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 32px 24px;
    .hero-copy h1 { font-size: 24px; }
  }
  .hero-panel { justify-content: flex-start; }
  .panel-ghost { display: none; }
}
@media (max-width: 560px) {
  .home-hero {
    padding: 26px 18px;
    .hero-copy {
      h1 { font-size: 21px; }
      p { font-size: 13px; }
      .hero-actions {
        flex-direction: column;
        align-items: stretch;
        .el-button { width: 100%; margin-left: 0; }
      }
    }
  }
  .section { padding: 16px; }
}
</style>
