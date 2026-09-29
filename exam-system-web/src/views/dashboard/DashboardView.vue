<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <div class="stat-cards" v-loading="loading">
      <div v-for="item in statCards" :key="item.key" class="stat-card slide-up" :style="{ animationDelay: `${item.delay}ms` }">
        <div class="stat-icon" :style="{ background: item.gradient }">
          <el-icon :size="26"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats[item.key] ?? 0 }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :xs="24" :md="14">
        <div class="chart-card">
          <div class="chart-head">
            <h3 class="chart-title">题目难度分布</h3>
          </div>
          <div ref="pieRef" class="chart-body"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="10">
        <div class="chart-card">
          <div class="chart-head">
            <h3 class="chart-title">题目分类 TOP6</h3>
          </div>
          <div ref="barRef" class="chart-body"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <div class="quick-actions">
      <div class="chart-card">
        <div class="chart-head">
          <h3 class="chart-title">快捷操作</h3>
        </div>
        <div class="action-grid">
          <div class="action-item" @click="$router.push('/admin/questions/create')">
            <span class="action-icon icon-question"><el-icon><EditPen /></el-icon></span>
            <span class="action-name">新增题目</span>
          </div>
          <div class="action-item" @click="$router.push('/admin/papers/create')">
            <span class="action-icon icon-paper"><el-icon><Document /></el-icon></span>
            <span class="action-name">手动组卷</span>
          </div>
          <div class="action-item" @click="$router.push('/admin/papers/ai-create')">
            <span class="action-icon icon-ai"><el-icon><MagicStick /></el-icon></span>
            <span class="action-name">AI 组卷</span>
          </div>
          <div class="action-item" @click="$router.push('/admin/banners')">
            <span class="action-icon icon-banner"><el-icon><PictureFilled /></el-icon></span>
            <span class="action-name">管理轮播图</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
// echarts 按需引入（只用饼图+柱状图），全量引入会让构建产物多出约 800KB
import * as echarts from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getStatsOverview } from '@/api/stats'
import { getQuestionList } from '@/api/question'
import { getPaperList } from '@/api/paper'
import { getBanners } from '@/api/banner'
import { getVideoCategories } from '@/api/videoCategory'

echarts.use([BarChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const loading = ref(false)
const stats = reactive({
  questionCount: 0, userCount: 0, examCount: 0,
  todayExamCount: 0, categoryCount: 0, paperCount: 0,
})

const statCards = [
  { key: 'questionCount', label: '题目总数', icon: 'EditPen', gradient: 'linear-gradient(135deg,#6366f1,#8b5cf6)', delay: 0 },
  { key: 'userCount', label: '用户总数', icon: 'User', gradient: 'linear-gradient(135deg,#06b6d4,#3b82f6)', delay: 60 },
  { key: 'examCount', label: '考试总数', icon: 'Document', gradient: 'linear-gradient(135deg,#10b981,#14b8a6)', delay: 120 },
  { key: 'todayExamCount', label: '今日考试', icon: 'Lightning', gradient: 'linear-gradient(135deg,#f59e0b,#f97316)', delay: 180 },
  { key: 'categoryCount', label: '分类数量', icon: 'Folder', gradient: 'linear-gradient(135deg,#8b5cf6,#d946ef)', delay: 240 },
  { key: 'paperCount', label: '试卷总数', icon: 'Tickets', gradient: 'linear-gradient(135deg,#ec4899,#f43f5e)', delay: 300 },
]

// 图表
const pieRef = ref(null)
const barRef = ref(null)
let pieChart = null
let barChart = null

const DIFFICULTY_MAP = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
const COLORS = ['#6366f1', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b', '#ec4899']

function buildDifficultyData(questions) {
  const count = { EASY: 0, MEDIUM: 0, HARD: 0 }
  questions.forEach((q) => { if (count[q.difficulty] !== undefined) count[q.difficulty]++ })
  return Object.entries(count).map(([key, value]) => ({
    name: DIFFICULTY_MAP[key], value,
    itemStyle: { color: key === 'EASY' ? '#10b981' : key === 'MEDIUM' ? '#f59e0b' : '#ef4444' },
  }))
}

function buildCategoryData(questions) {
  const map = {}
  questions.forEach((q) => {
    const name = q.category?.name || '未分类'
    map[name] = (map[name] || 0) + 1
  })
  return Object.entries(map)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
    .map(([name, value]) => ({ name, value }))
}

function renderCharts(questions) {
  if (pieRef.value && !pieChart) pieChart = echarts.init(pieRef.value)
  if (barRef.value && !barChart) barChart = echarts.init(barRef.value)

  if (pieChart) {
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, icon: 'circle' },
      series: [{
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontWeight: 'bold', fontSize: 16 } },
        data: buildDifficultyData(questions),
      }],
    })
  }

  if (barChart) {
    const cat = buildCategoryData(questions)
    barChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 8, right: 16, top: 16, bottom: 24, containLabel: true },
      xAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f1f5f9' } },
        axisLabel: { color: '#94a3b8' },
      },
      yAxis: {
        type: 'category',
        data: cat.map((i) => i.name),
        axisLabel: { color: '#64748b' },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series: [{
        type: 'bar',
        data: cat.map((i, idx) => ({
          value: i.value,
          itemStyle: { color: COLORS[idx % COLORS.length], borderRadius: [0, 8, 8, 0] },
        })),
        barWidth: 18,
        label: { show: true, position: 'right', color: '#64748b' },
      }],
    })
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getStatsOverview()
    if (res) Object.assign(stats, res)

    // 加载题目做图表
    const qRes = await getQuestionList({ page: 1, size: 200 })
    const questions = qRes?.records || []
    renderCharts(questions)

    // 补充统计：分类/试卷/轮播图（后端 overview 若缺失）
    try {
      const [cats, papers, banners] = await Promise.all([
        getVideoCategories().catch(() => []),
        getPaperList().catch(() => []),
        getBanners().catch(() => []),
      ])
      if (!stats.categoryCount && cats?.length) stats.categoryCount = cats.length
      if (!stats.paperCount && papers?.length) stats.paperCount = papers.length
    } catch {}
  } finally { loading.value = false }
}

function handleResize() {
  pieChart?.resize()
  barChart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose()
  barChart?.dispose()
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.dashboard { padding: 4px; }

// 统计卡片
.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 20px;
  @media (max-width: 992px) { grid-template-columns: repeat(2, 1fr); }
  @media (max-width: 576px) { grid-template-columns: 1fr; }
}
.stat-card {
  @include glass;
  border-radius: 16px;
  padding: 22px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: transform $transition-normal, box-shadow $transition-normal;
  &:hover { transform: translateY(-3px); box-shadow: $shadow-lg; }
  .stat-icon {
    width: 56px;
    height: 56px;
    border-radius: 14px;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
  }
  .stat-value { font-size: 30px; font-weight: 800; color: $text-primary; line-height: 1.1; }
  .stat-label { font-size: 13px; color: $text-muted; margin-top: 4px; }
}

// 图表
.chart-row { margin-bottom: 20px; }
.chart-card {
  @include glass;
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 20px;
  .chart-head {
    margin-bottom: 16px;
    .chart-title {
      font-size: 16px;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 8px;
      &::before {
        content: '';
        width: 4px;
        height: 18px;
        border-radius: 4px;
        background: $gradient-primary;
      }
    }
  }
  .chart-body { height: 300px; }
}

// 快捷操作
.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  @media (max-width: 768px) { grid-template-columns: repeat(2, 1fr); }
}
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 12px;
  border-radius: 14px;
  cursor: pointer;
  background: rgba(248, 250, 252, 0.8);
  border: 1px solid rgba(226, 232, 240, 0.6);
  transition: all $transition-normal;
  &:hover {
    transform: translateY(-3px);
    box-shadow: $shadow-md;
    border-color: $primary-lighter;
  }
  .action-icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    &.icon-question { background: linear-gradient(135deg,#6366f1,#8b5cf6); }
    &.icon-paper { background: linear-gradient(135deg,#06b6d4,#3b82f6); }
    &.icon-ai { background: linear-gradient(135deg,#8b5cf6,#d946ef); }
    &.icon-banner { background: linear-gradient(135deg,#10b981,#14b8a6); }
  }
  .action-name { font-size: 14px; font-weight: 500; color: $text-primary; }
}
</style>
