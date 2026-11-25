<template>
  <div class="page-container remediation-overview">
    <div class="page-header">
      <div>
        <h2>补课概览</h2>
        <p class="subtitle">按班级查看补课任务进度与薄弱知识点排行</p>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <span class="filter-label">选择班级</span>
        <el-select v-model="classId" placeholder="选择班级" style="width: 280px" @change="loadOverview">
          <el-option v-for="item in classes" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </div>
    </el-card>

    <template v-if="classId">
      <div class="stat-cards">
        <div class="stat-card">
          <p class="stat-num">{{ overview?.total || 0 }}</p>
          <p class="stat-label">任务总数</p>
        </div>
        <div class="stat-card done">
          <p class="stat-num">{{ overview?.completed || 0 }}</p>
          <p class="stat-label">已完成</p>
        </div>
        <div class="stat-card overdue">
          <p class="stat-num">{{ overview?.overdue || 0 }}</p>
          <p class="stat-label">已逾期</p>
        </div>
      </div>

      <div class="panel-grid">
        <el-card shadow="never" class="panel">
          <template #header>学员明细</template>
          <el-table :data="overview?.students || []" v-loading="loading" max-height="460">
            <el-table-column prop="userName" label="学员" min-width="140" />
            <el-table-column prop="total" label="任务总数" width="100" align="center" />
            <el-table-column prop="completed" label="已完成" width="100" align="center" />
            <el-table-column label="已逾期" width="100" align="center">
              <template #default="{ row }">
                <span :class="{ 'overdue-num': row.overdue > 0 }">{{ row.overdue }}</span>
              </template>
            </el-table-column>
            <template #empty><el-empty description="该班级暂无补课任务" /></template>
          </el-table>
        </el-card>

        <el-card shadow="never" class="panel">
          <template #header>薄弱知识点排行</template>
          <div v-loading="loading" class="weak-wrap">
            <ol v-if="weakPoints.length" class="weak-list">
              <li v-for="(point, index) in weakPoints" :key="point.knowledgePointId ?? index">
                <span class="weak-name">{{ point.knowledgePointName }}</span>
                <el-tag size="small" type="danger">{{ point.taskCount }} 个任务</el-tag>
              </li>
            </ol>
            <el-empty v-else-if="!loading" description="暂无薄弱知识点数据" />
          </div>
        </el-card>
      </div>
    </template>

    <el-card v-else-if="!classesLoading" shadow="never">
      <el-empty description="暂无可查看的班级（教师需先负责班级，管理员可查看全部）" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getRemediationOverview } from '@/api/remediation'
import { getClassList } from '@/api/classManage'

const classes = ref([])
const classId = ref(null)
const overview = ref(null)
const loading = ref(false)
const classesLoading = ref(false)
const weakPoints = computed(() => overview.value?.weakPoints || [])

// 与课程管理"分配班级"同口径：翻页取全量（GET /api/classes/list 对 TEACHER 仅返回负责班级）
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
      await loadOverview()
    }
  } catch {
    classes.value = []
  } finally {
    classesLoading.value = false
  }
}

async function loadOverview() {
  if (!classId.value) return
  loading.value = true
  try {
    overview.value = await getRemediationOverview(classId.value)
  } catch {
    overview.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadClasses)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.remediation-overview {
  .subtitle { margin-top: 6px; color: $text-muted; font-size: 13px; }
  .filter-card { margin-bottom: 18px; border-color: $border; }
  .filter-row { display: flex; align-items: center; gap: 12px; color: $text-secondary; }
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 18px;

  .stat-card {
    @include glass;
    border-radius: $radius-lg;
    padding: 20px 24px;

    .stat-num { margin: 0; font-size: 30px; font-weight: 700; color: $text-primary; line-height: 1.2; }
    .stat-label { margin: 6px 0 0; font-size: 13px; color: $text-muted; }
    &.done .stat-num { color: $success; }
    &.overdue .stat-num { color: $danger; }
  }
}

.panel-grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 16px;
  align-items: start;

  .panel { @include glass; border-radius: $radius-lg; }
}

.overdue-num { color: $danger; font-weight: 600; }

.weak-wrap { min-height: 120px; }
.weak-list {
  margin: 0;
  padding-left: 24px;

  li { padding: 8px 0; font-size: 14px; }
  .weak-name { margin-right: 10px; color: $text-primary; }
}

@media (max-width: 900px) {
  .panel-grid { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .stat-cards { grid-template-columns: 1fr; }
}
</style>
