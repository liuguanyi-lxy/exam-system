<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <div class="stat-cards" v-loading="loading">
      <div v-for="item in statCards" :key="item.key" class="stat-card slide-up" :style="{ animationDelay: `${item.delay}ms` }">
        <div class="stat-icon" :style="{ background: item.bg, color: item.color }">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
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
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getStatsOverview } from '@/api/stats'
import { getQuestionList } from '@/api/question'
import { getPaperList } from '@/api/paper'
import { getCategories } from '@/api/category'

echarts.use([BarChart, PieChart, GridComponent, LegendComponent, TitleComponent, TooltipComponent, CanvasRenderer])

const loading = ref(false)
const stats = reactive({
  questionCount: 0, userCount: 0, examCount: 0,
  todayExamCount: 0, categoryCount: 0, paperCount: 0,
})

const statCards = [
  { key: 'questionCount', label: '题目总数', icon: 'EditPen', bg: '#eef2ff', color: '#4f46e5', delay: 0 },
  { key: 'userCount', label: '用户总数', icon: 'User', bg: '#eff8ff', color: '#2e90fa', delay: 40 },
  { key: 'examCount', label: '考试总数', icon: 'Document', bg: '#ecfdf3', color: '#12b76a', delay: 80 },
  { key: 'todayExamCount', label: '今日考试', icon: 'Lightning', bg: '#fffaeb', color: '#f79009', delay: 120 },
  { key: 'categoryCount', label: '分类数量', icon: 'Folder', bg: '#f4f3ff', color: '#7a5af8', delay: 160 },
  { key: 'paperCount', label: '试卷总数', icon: 'Tickets', bg: '#fdf2fa', color: '#ee46bc', delay: 200 },
]

// 图表
const pieRef = ref(null)
const barRef = ref(null)
let pieChart = null
let barChart = null
// 题目分类字典：categoryId -> 分类名
const categoryNameMap = ref({})

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
    // 题目列表接口不带嵌套 category，用字典按 categoryId 翻译，否则全被算成"未分类"
    const name = q.category?.name || categoryNameMap.value[q.categoryId] || '未分类'
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

  const empty = !questions.length
  const emptyOption = {
    title: {
      text: '暂无数据',
      left: 'center',
      top: 'middle',
      textStyle: { color: '#98a2b3', fontSize: 13, fontWeight: 'normal' },
    },
  }

  if (pieChart) {
    pieChart.setOption(empty ? emptyOption : {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#475467', fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['52%', '72%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontWeight: 'bold', fontSize: 14 } },
        data: buildDifficultyData(questions),
      }],
    }, true)
  }

  if (barChart) {
    if (empty) {
      barChart.setOption(emptyOption, true)
    } else {
      const cat = buildCategoryData(questions)
      barChart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 8, right: 24, top: 12, bottom: 8, containLabel: true },
        xAxis: {
          type: 'value',
          splitLine: { lineStyle: { color: '#f2f4f7' } },
          axisLabel: { color: '#98a2b3', fontSize: 11 },
        },
        yAxis: {
          type: 'category',
          data: cat.map((i) => i.name),
          axisLabel: { color: '#475467', fontSize: 12 },
          axisLine: { show: false },
          axisTick: { show: false },
        },
        series: [{
          type: 'bar',
          data: cat.map((i, idx) => ({
            value: i.value,
            itemStyle: { color: COLORS[idx % COLORS.length], borderRadius: [0, 4, 4, 0] },
          })),
          barWidth: 14,
          label: { show: true, position: 'right', color: '#98a2b3', fontSize: 11 },
        }],
      }, true)
    }
  }
}

async function loadData() {
  loading.value = true
  let questions = []
  try {
    const res = await getStatsOverview()
    if (res) Object.assign(stats, res)
  } catch {
    // 统计接口不可用时保留默认值，不影响图表渲染
  }

  try {
    const cats = await getCategories()
    const map = {}
    ;(cats || []).forEach((c) => { map[c.id] = c.name })
    categoryNameMap.value = map
    if (!stats.categoryCount && cats?.length) stats.categoryCount = cats.length
  } catch {
    categoryNameMap.value = {}
  }

  try {
    const qRes = await getQuestionList({ page: 1, size: 200 })
    questions = qRes?.records || []
  } catch {
    questions = []
  }
  renderCharts(questions)

  // 补充统计：试卷数量（后端 overview 若缺失）
  try {
    const papers = await getPaperList().catch(() => [])
    if (!stats.paperCount && papers?.length) stats.paperCount = papers.length
  } catch {}

  loading.value = false
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

.dashboard { padding: 0; }

// 统计卡片
.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 14px;
  @media (max-width: 992px) { grid-template-columns: repeat(2, 1fr); }
  @media (max-width: 576px) { grid-template-columns: 1fr; }
}
.stat-card {
  background: $surface;
  border: 1px solid $border;
  border-radius: $radius-xl;
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: $shadow-sm;
  transition: border-color $transition-fast, box-shadow $transition-fast;
  &:hover {
    border-color: $border-strong;
    box-shadow: $shadow-md;
  }
  .stat-icon {
    width: 40px;
    height: 40px;
    border-radius: $radius-md;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .stat-value {
    font-size: 24px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.2;
    font-variant-numeric: tabular-nums;
  }
  .stat-label { font-size: 12.5px; color: $text-muted; margin-top: 2px; }
}

// 图表
.chart-row { margin-bottom: 0; }
.chart-card {
  background: $surface;
  border: 1px solid $border;
  border-radius: $radius-xl;
  padding: 18px 20px;
  margin-bottom: 14px;
  box-shadow: $shadow-sm;
  .chart-head {
    margin-bottom: 12px;
    .chart-title {
      font-size: 14px;
      font-weight: 600;
      color: $text-primary;
    }
  }
  .chart-body { height: 288px; }
}

// 快捷操作
.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  @media (max-width: 768px) { grid-template-columns: repeat(2, 1fr); }
}
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  padding: 18px 12px;
  border-radius: $radius-lg;
  cursor: pointer;
  background: $surface;
  border: 1px solid $border;
  transition: border-color $transition-fast, box-shadow $transition-fast;
  &:hover {
    border-color: $primary-lighter;
    box-shadow: $shadow-md;
  }
  .action-icon {
    width: 38px;
    height: 38px;
    border-radius: $radius-md;
    display: flex;
    align-items: center;
    justify-content: center;
    &.icon-question { background: #eef2ff; color: #4f46e5; }
    &.icon-paper { background: #eff8ff; color: #2e90fa; }
    &.icon-ai { background: #f4f3ff; color: #7a5af8; }
    &.icon-banner { background: #ecfdf3; color: #12b76a; }
  }
  .action-name { font-size: 13px; font-weight: 500; color: $text-secondary; }
}
</style>
