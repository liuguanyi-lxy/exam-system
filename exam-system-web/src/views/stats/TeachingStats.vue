<template>
  <div class="page-container teaching-stats">
    <div class="page-header">
      <div>
        <h2>教学统计</h2>
        <p class="subtitle">按班级聚合学习完成率、考试表现、未完成名单与复核差异</p>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <span class="filter-label">选择班级</span>
        <el-select v-model="classId" placeholder="选择班级" style="width: 260px" @change="loadAll">
          <el-option v-for="item in classes" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <span class="filter-label">活跃窗口</span>
        <el-radio-group v-model="days" @change="loadStats">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
      </div>
    </el-card>

    <template v-if="classId">
      <!-- 概览卡 -->
      <div class="stat-cards">
        <div class="stat-card">
          <p class="stat-num">{{ stats?.classInfo?.studentCount ?? 0 }}</p>
          <p class="stat-label">班级学员数</p>
        </div>
        <div class="stat-card done">
          <p class="stat-num">{{ stats?.active?.activeCount ?? 0 }}</p>
          <p class="stat-label">近 {{ days }} 天活跃学员</p>
        </div>
        <div class="stat-card">
          <p class="stat-num">{{ activeRate }}%</p>
          <p class="stat-label">活跃率</p>
        </div>
        <div class="stat-card" :class="{ warn: reviewStats.reviewedCount < reviewStats.reportTotal }">
          <p class="stat-num">{{ reviewStats.reviewedCount }}/{{ reviewStats.reportTotal }}</p>
          <p class="stat-label">已人工复核报告</p>
        </div>
      </div>

      <div class="panel-grid">
        <div class="col">
          <!-- 课程完成率 -->
          <el-card shadow="never" class="panel">
            <template #header>课程学习完成率</template>
            <el-table :data="stats?.learning || []" v-loading="loading" size="small">
              <el-table-column prop="courseName" label="课程" min-width="160" />
              <el-table-column label="视频进度" width="140" align="center">
                <template #default="{ row }">{{ row.completedTotal }}/{{ row.videoTotal }}</template>
              </el-table-column>
              <el-table-column label="完成率" min-width="160">
                <template #default="{ row }">
                  <el-progress :percentage="row.completionRate" :stroke-width="8" />
                </template>
              </el-table-column>
              <template #empty><el-empty description="该班级暂未分配课程" :image-size="60" /></template>
            </el-table>
          </el-card>

          <!-- 考试统计（含分布与进步） -->
          <el-card shadow="never" class="panel">
            <template #header>考试统计</template>
            <el-table :data="stats?.exams || []" v-loading="loading" size="small">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div class="exam-expand">
                    <div class="dist-bars">
                      <div v-for="seg in row.distribution" :key="seg.range" class="dist-row">
                        <span class="dist-range">{{ seg.range }}</span>
                        <div class="dist-track">
                          <div class="dist-fill" :style="{ width: distWidth(seg.count, row.distribution) }" />
                        </div>
                        <span class="dist-count">{{ seg.count }} 人</span>
                      </div>
                    </div>
                    <div class="progress-list">
                      <p class="expand-title">多次尝试进步（最近 − 首次）</p>
                      <el-table :data="row.progress" size="small" v-if="row.progress.length">
                        <el-table-column prop="realName" label="学员" min-width="100" />
                        <el-table-column prop="firstScore" label="首次" width="70" align="center" />
                        <el-table-column prop="latestScore" label="最近" width="70" align="center" />
                        <el-table-column label="变化" width="80" align="center">
                          <template #default="{ row: p }">
                            <span :class="p.delta >= 0 ? 'delta-up' : 'delta-down'">
                              {{ p.delta >= 0 ? '+' : '' }}{{ p.delta }}
                            </span>
                          </template>
                        </el-table-column>
                      </el-table>
                      <el-empty v-else description="暂无多次尝试记录" :image-size="50" />
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="examName" label="考试" min-width="150" />
              <el-table-column label="参与" width="90" align="center">
                <template #default="{ row }">{{ row.participantCount }}/{{ row.classStudentCount }}</template>
              </el-table-column>
              <el-table-column prop="participationRate" label="参与率" width="80" align="center">
                <template #default="{ row }">{{ row.participationRate }}%</template>
              </el-table-column>
              <el-table-column label="均分" width="70" align="center">
                <template #default="{ row }">{{ row.avgScore ?? '—' }}</template>
              </el-table-column>
              <el-table-column prop="passRate" label="通过率" width="80" align="center">
                <template #default="{ row }">{{ row.passRate }}%</template>
              </el-table-column>
              <template #empty><el-empty description="该班级暂无分配考试" :image-size="60" /></template>
            </el-table>
          </el-card>

          <!-- 高频错题 -->
          <el-card shadow="never" class="panel">
            <template #header>高频错题 TOP 10</template>
            <el-table :data="stats?.topWrongQuestions || []" v-loading="loading" size="small">
              <el-table-column prop="content" label="题目" min-width="220" show-overflow-tooltip />
              <el-table-column prop="wrongCount" label="错误次数" width="90" align="center" />
              <el-table-column label="错误率" width="90" align="center">
                <template #default="{ row }">{{ row.wrongRate }}%</template>
              </el-table-column>
              <el-table-column prop="chatCount" label="追问次数" width="90" align="center" />
              <el-table-column label="知识点" min-width="120">
                <template #default="{ row }">
                  <el-tag v-for="name in row.knowledgePoints" :key="name" size="small" class="kp-tag">
                    {{ name }}
                  </el-tag>
                  <span v-if="!row.knowledgePoints.length">—</span>
                </template>
              </el-table-column>
              <template #empty><el-empty description="暂无错题数据" :image-size="60" /></template>
            </el-table>
          </el-card>
        </div>

        <div class="col">
          <!-- 未完成清单 -->
          <el-card shadow="never" class="panel">
            <template #header>未完成清单</template>
            <el-tabs v-model="uncompletedTab">
              <el-tab-pane label="缺考名单" name="absentees">
                <el-table :data="stats?.uncompleted?.examAbsentees || []" v-loading="loading" size="small" max-height="300">
                  <el-table-column prop="examName" label="考试" min-width="140" show-overflow-tooltip />
                  <el-table-column prop="realName" label="学员" width="100" />
                  <template #empty><el-empty description="无缺考学员" :image-size="60" /></template>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="逾期补课" name="overdue">
                <el-table :data="stats?.uncompleted?.overdueTasks || []" v-loading="loading" size="small" max-height="300">
                  <el-table-column prop="realName" label="学员" width="100" />
                  <el-table-column prop="knowledgePoint" label="知识点" min-width="120" show-overflow-tooltip />
                  <el-table-column label="截止时间" width="110" align="center">
                    <template #default="{ row }">{{ formatTime(row.dueTime) }}</template>
                  </el-table-column>
                  <template #empty><el-empty description="无逾期任务" :image-size="60" /></template>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </el-card>

          <!-- 复核差异 -->
          <el-card shadow="never" class="panel">
            <template #header>AI 与人工复核差异</template>
            <div class="review-grid" v-loading="loading" v-if="reviewStats.reportTotal > 0">
              <div class="review-item">
                <p class="review-num">{{ reviewStats.reportTotal }}</p>
                <p class="review-label">报告总数</p>
              </div>
              <div class="review-item">
                <p class="review-num" :class="{ warn: lowConfidenceRate > 0 }">{{ lowConfidenceRate }}%</p>
                <p class="review-label">低置信度占比</p>
              </div>
              <div class="review-item">
                <p class="review-num">{{ reviewedRate }}%</p>
                <p class="review-label">已复核占比</p>
              </div>
              <div class="review-item">
                <p class="review-num">{{ reviewStats.reviewNoteCount }}</p>
                <p class="review-label">复核意见条数</p>
              </div>
            </div>
            <el-empty v-else-if="!loading" description="暂无诊断报告" :image-size="50" />
          </el-card>

          <!-- 薄弱知识点排行（复用 M5 补课概览数据） -->
          <el-card shadow="never" class="panel">
            <template #header>薄弱知识点排行</template>
            <div v-loading="remediationLoading" class="weak-wrap">
              <ol v-if="weakPoints.length" class="weak-list">
                <li v-for="(point, index) in weakPoints" :key="point.knowledgePointId ?? index">
                  <span class="weak-name">{{ point.knowledgePointName }}</span>
                  <el-tag size="small" type="danger">{{ point.taskCount }} 个任务</el-tag>
                </li>
              </ol>
              <el-empty v-else-if="!remediationLoading" description="暂无薄弱知识点数据" :image-size="50" />
            </div>
          </el-card>
        </div>
      </div>
    </template>

    <el-card v-else-if="!classesLoading" shadow="never">
      <el-empty description="暂无可查看的班级（教师需先负责班级，管理员可查看全部）" />
    </el-card>

    <!-- 班级对比（教师 = 负责班级，管理员 = 全部） -->
    <el-card v-if="compareItems.length" shadow="never" class="compare-card">
      <template #header>班级对比</template>
      <el-table :data="compareItems" v-loading="compareLoading" size="small">
        <el-table-column prop="className" label="班级" min-width="160" />
        <el-table-column label="均分" width="100" align="center">
          <template #default="{ row }">{{ row.avgScore ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="通过率" width="120">
          <template #default="{ row }">
            <el-progress :percentage="row.passRate" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="补课完成率" width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.remediationCompletionRate" :stroke-width="8" color="#67c23a" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getTeachingStats, getTeachingStatsCompare } from '@/api/teachingStats'
import { getRemediationOverview } from '@/api/remediation'
import { getClassList } from '@/api/classManage'
import { formatDateTime } from '@/utils/format'

const classes = ref([])
const classId = ref(null)
const days = ref(7)
const stats = ref(null)
const loading = ref(false)
const classesLoading = ref(false)
const compareItems = ref([])
const compareLoading = ref(false)
const remediationLoading = ref(false)
const weakPoints = ref([])
const uncompletedTab = ref('absentees')

const reviewStats = computed(() => stats.value?.reviewStats || { reportTotal: 0, lowConfidenceCount: 0, reviewedCount: 0, reviewNoteCount: 0 })
const activeRate = computed(() => {
  const total = stats.value?.classInfo?.studentCount || 0
  const active = stats.value?.active?.activeCount || 0
  return total ? Math.floor((100 * active) / total) : 0
})
const lowConfidenceRate = computed(() => pct(reviewStats.value.lowConfidenceCount, reviewStats.value.reportTotal))
const reviewedRate = computed(() => pct(reviewStats.value.reviewedCount, reviewStats.value.reportTotal))

function pct(numerator, denominator) {
  return denominator ? Math.floor((100 * numerator) / denominator) : 0
}

function distWidth(count, distribution) {
  const max = Math.max(...distribution.map((d) => d.count), 1)
  return `${Math.max(Math.round((100 * count) / max), count > 0 ? 4 : 0)}%`
}

function formatTime(value) {
  return value ? formatDateTime(value) : '—'
}

// 与补课概览同口径：翻页取全量（GET /api/classes/list 对 TEACHER 仅返回负责班级）
async function loadClasses() {
  classesLoading.value = true
  try {
    const results = []
    let classPage = 1
    let pageCount = 1
    do {
      const data = await getClassList({ page: classPage, size: 100 })
      results.push(...(data?.records || []))
      pageCount = data?.pages || Math.ceil((data?.total || 0) / 100)
      classPage += 1
    } while (classPage <= pageCount)
    classes.value = results
    if (results.length) {
      classId.value = results[0].id
      await loadAll()
    }
  } catch {
    classes.value = []
  } finally {
    classesLoading.value = false
  }
}

async function loadStats() {
  if (!classId.value) return
  loading.value = true
  try {
    stats.value = await getTeachingStats(classId.value, days.value)
  } catch {
    stats.value = null
  } finally {
    loading.value = false
  }
}

async function loadWeakPoints() {
  if (!classId.value) return
  remediationLoading.value = true
  try {
    const overview = await getRemediationOverview(classId.value)
    weakPoints.value = overview?.weakPoints || []
  } catch {
    weakPoints.value = []
  } finally {
    remediationLoading.value = false
  }
}

async function loadCompare() {
  compareLoading.value = true
  try {
    const data = await getTeachingStatsCompare()
    compareItems.value = data?.items || []
  } catch {
    compareItems.value = []
  } finally {
    compareLoading.value = false
  }
}

function loadAll() {
  loadStats()
  loadWeakPoints()
}

onMounted(() => {
  loadClasses()
  loadCompare()
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.teaching-stats {
  .subtitle { margin-top: 6px; color: $text-muted; font-size: 13px; }
  .filter-card { margin-bottom: 18px; border-color: $border; }
  .filter-row { display: flex; align-items: center; gap: 12px; color: $text-secondary; flex-wrap: wrap; }
  .filter-label { font-size: 13px; color: $text-muted; }
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 18px;

  .stat-card {
    @include glass;
    border-radius: $radius-lg;
    padding: 20px 24px;

    .stat-num { margin: 0; font-size: 28px; font-weight: 700; color: $text-primary; line-height: 1.2; }
    .stat-label { margin: 6px 0 0; font-size: 13px; color: $text-muted; }
    &.done .stat-num { color: $success; }
    &.warn .stat-num { color: $warning; }
  }
}

.panel-grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 16px;
  align-items: start;

  .col { display: flex; flex-direction: column; gap: 16px; }
  .panel { @include glass; border-radius: $radius-lg; }
}

.compare-card {
  margin-top: 16px;
  @include glass;
  border-radius: $radius-lg;
}

.kp-tag { margin-right: 6px; margin-bottom: 4px; }

.exam-expand {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  padding: 8px 16px;

  .expand-title { margin: 0 0 8px; font-size: 13px; color: $text-secondary; font-weight: 600; }
}

.dist-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;

  .dist-range { width: 52px; font-size: 12px; color: $text-muted; text-align: right; }
  .dist-track { flex: 1; height: 10px; background: $bg-soft; border-radius: 5px; overflow: hidden; }
  .dist-fill { height: 100%; background: $primary; border-radius: 5px; transition: width 0.3s; }
  .dist-count { width: 44px; font-size: 12px; color: $text-secondary; }
}

.delta-up { color: $success; font-weight: 600; }
.delta-down { color: $danger; font-weight: 600; }

.review-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  min-height: 60px;

  .review-item { text-align: center; }
  .review-num { margin: 0; font-size: 22px; font-weight: 700; color: $text-primary; }
  .review-num.warn { color: $danger; }
  .review-label { margin: 4px 0 0; font-size: 12px; color: $text-muted; }
}

.weak-wrap { min-height: 100px; }
.weak-list {
  margin: 0;
  padding-left: 24px;

  li { padding: 8px 0; font-size: 14px; }
  .weak-name { margin-right: 10px; color: $text-primary; }
}

@media (max-width: 900px) {
  .panel-grid { grid-template-columns: 1fr; }
  .exam-expand { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .review-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
