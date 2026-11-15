<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div class="head-left">
        <el-button link @click="$router.push('/admin/papers')">
          <el-icon><ArrowLeft /></el-icon> 试卷管理
        </el-button>
        <h2>{{ paper?.name || '试卷成绩' }}</h2>
        <el-tag v-if="paper" size="small" :type="paper.status === 'PUBLISHED' ? 'success' : 'info'" effect="light">
          {{ statusText(paper.status) }}
        </el-tag>
      </div>
      <div class="head-right">
        <span class="paper-meta" v-if="paper">
          {{ paper.questionCount }} 题 · 满分 {{ paper.totalScore }} 分 · {{ paper.duration }} 分钟
        </span>
        <el-button @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 概览 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalRecords ?? 0 }}</div>
        <div class="stat-label">参考人次</div>
        <div class="stat-sub">已批阅 {{ stats.gradedRecords ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.average ?? 0 }}</div>
        <div class="stat-label">平均分</div>
        <div class="stat-sub">满分 {{ stats.totalScore ?? paper?.totalScore ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.max ?? 0 }} <span class="sep">/</span> {{ stats.min ?? 0 }}</div>
        <div class="stat-label">最高分 / 最低分</div>
        <div class="stat-sub">仅统计已批阅</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.passRate ?? 0 }}<span class="unit">%</span></div>
        <div class="stat-label">及格率</div>
        <div class="stat-sub">≥ 60% 满分</div>
      </div>
    </div>

    <!-- 分数段分布 -->
    <div class="dist-card" v-if="stats.totalRecords">
      <div class="dist-head">分数段分布</div>
      <div class="dist-bar">
        <div v-for="(item, i) in distribution" :key="i" v-show="item.count" class="dist-seg"
          :class="`seg-${i}`" :style="{ flexGrow: item.count }">
          <span v-if="item.count">{{ item.count }}</span>
        </div>
      </div>
      <div class="dist-legend">
        <span v-for="(item, i) in distribution" :key="i" class="legend-item">
          <i :class="`dot seg-${i}`"></i>{{ item.label }} {{ item.count }} 人
        </span>
      </div>
    </div>

    <!-- 考生列表 -->
    <el-table :data="tableData" style="margin-top:16px">
      <el-table-column type="index" label="#" width="52" />
      <el-table-column prop="studentName" label="考生" width="130">
        <template #default="{ row }">{{ row.studentName || '-' }}</template>
      </el-table-column>
      <el-table-column label="得分" width="150">
        <template #default="{ row }">
          <template v-if="row.score != null && row.status === '已批阅'">
            <span class="score">{{ row.score }}</span>
            <span class="score-total"> / {{ totalScore }}</span>
            <span class="score-rate" :class="rateClass(row.score)">{{ rateOf(row.score) }}%</span>
          </template>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="light"
            :type="row.status === '已批阅' ? 'success' : row.status === '已结束' ? 'warning' : 'info'">
            {{ row.status || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="用时" width="100">
        <template #default="{ row }">{{ usedMinutes(row) }}</template>
      </el-table-column>
      <el-table-column label="切屏" width="80">
        <template #default="{ row }">
          <span :class="{ warn: (row.windowSwitches || 0) > 0 }">{{ row.windowSwitches ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ row.endTime || row.createTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="$router.push(`/admin/exam-records/${row.id}`)">
            查看答卷
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="这张试卷还没有人考过" :image-size="70" />
      </template>
    </el-table>

    <div v-if="total > size" style="margin-top:16px;text-align:right">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        layout="total,prev,pager,next" @change="loadData" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPaperById } from '@/api/paper'
import { getExamRecordList, getPaperStats } from '@/api/examRecord'

const route = useRoute()
const paperId = Number(route.params.id)
const loading = ref(false)
const paper = ref(null)
const stats = ref({})
const tableData = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const totalScore = computed(() => stats.value.totalScore ?? paper.value?.totalScore ?? 100)

const distribution = computed(() => {
  const d = stats.value.distribution || [0, 0, 0, 0]
  return [
    { label: '不及格 (<60%)', count: d[0] || 0 },
    { label: '及格 (60-79%)', count: d[1] || 0 },
    { label: '良好 (80-99%)', count: d[2] || 0 },
    { label: '满分', count: d[3] || 0 },
  ]
})

const STATUS_TEXT = { PUBLISHED: '已发布', DRAFT: '草稿', STOPPED: '已停止' }
function statusText(s) {
  return STATUS_TEXT[s] || s || '-'
}

function rateOf(score) {
  const t = totalScore.value || 100
  return Math.round((score / t) * 100)
}

function rateClass(score) {
  const r = rateOf(score)
  if (r >= 80) return 'high'
  if (r >= 60) return 'mid'
  return 'low'
}

function usedMinutes(row) {
  if (!row.startTime || !row.endTime) return '-'
  const s = new Date(String(row.startTime).replace(/-/g, '/')).getTime()
  const e = new Date(String(row.endTime).replace(/-/g, '/')).getTime()
  if (!s || !e) return '-'
  if (e <= s) return '<1 分钟'
  return `${Math.max(1, Math.round((e - s) / 60000))} 分钟`
}

async function loadData() {
  loading.value = true
  try {
    const [p, s, list] = await Promise.all([
      getPaperById(paperId),
      getPaperStats(paperId),
      getExamRecordList({ paperId, page: page.value, size: size.value }),
    ])
    paper.value = p
    stats.value = s || {}
    tableData.value = list?.records || []
    total.value = list?.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .head-left {
    display: flex;
    align-items: center;
    gap: 12px;
    .el-button { font-size: 13px; }
    h2 { margin: 0; }
  }
  .head-right {
    display: flex;
    align-items: center;
    gap: 12px;
    .paper-meta { font-size: 12.5px; color: $text-muted; }
  }
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin: 4px 0 14px;
  @media (max-width: 900px) { grid-template-columns: repeat(2, 1fr); }

  .stat-card {
    background: $surface;
    border: 1px solid $border;
    border-radius: $radius-lg;
    padding: 16px 18px;
    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: $text-primary;
      font-variant-numeric: tabular-nums;
      line-height: 1.2;
      .sep { color: $border-strong; font-weight: 400; margin: 0 2px; }
      .unit { font-size: 14px; color: $text-muted; margin-left: 2px; }
    }
    .stat-label { font-size: 12.5px; color: $text-secondary; margin-top: 4px; }
    .stat-sub { font-size: 11.5px; color: $text-muted; margin-top: 2px; }
  }
}

.dist-card {
  background: $surface;
  border: 1px solid $border;
  border-radius: $radius-lg;
  padding: 16px 18px;

  .dist-head { font-size: 13px; font-weight: 600; color: $text-primary; margin-bottom: 12px; }
  .dist-bar {
    display: flex;
    height: 28px;
    border-radius: $radius-sm;
    overflow: hidden;
    gap: 2px;
    background: $bg-soft;

    .dist-seg {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 11.5px;
      color: #fff;
      font-weight: 600;
      min-width: 2px;
      transition: flex-grow 0.3s;
    }
    .seg-0 { background: #f97066; }
    .seg-1 { background: #fdb022; }
    .seg-2 { background: #4f46e5; }
    .seg-3 { background: #12b76a; }
  }
  .dist-legend {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    margin-top: 10px;
    font-size: 12px;
    color: $text-secondary;
    .legend-item { display: flex; align-items: center; gap: 6px; }
    .dot { width: 8px; height: 8px; border-radius: 2px; display: inline-block; }
  }
}

.score { font-weight: 600; font-variant-numeric: tabular-nums; color: $text-primary; }
.score-total { color: $text-muted; font-size: 12.5px; }
.score-rate {
  margin-left: 8px;
  font-size: 11.5px;
  padding: 1px 6px;
  border-radius: 4px;
  &.high { background: #ecfdf3; color: #067647; }
  &.mid { background: $primary-soft; color: $primary-dark; }
  &.low { background: #fef3f2; color: #b42318; }
}
.warn { color: #b54708; font-weight: 600; }
</style>
