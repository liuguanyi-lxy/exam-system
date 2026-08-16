<template>
  <div class="page-container">
    <div class="page-header">
      <h2>我的考试记录</h2>
    </div>

    <el-table :data="tableData" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="试卷" min-width="160">
        <template #default="{ row }">{{ row.paper?.name || '-' }}</template>
      </el-table-column>
      <el-table-column prop="studentName" label="考生" width="100" />
      <el-table-column label="得分" width="140">
        <template #default="{ row }">
          <span v-if="row.score != null" class="score-text" :class="scoreClass(row)">
            {{ row.score }}
          </span>
          <span v-else class="text-muted">-</span>
          <span class="score-total" v-if="row.paper?.totalScore">/ {{ row.paper.totalScore }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '已批阅' ? 'success' : row.status === '已完成' ? 'info' : 'warning'" size="small" effect="light">
            {{ row.status || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ row.createTime || row.startTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain round @click="$router.push(`/exam/result/${row.id}`)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:16px;text-align:right">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        layout="total,prev,pager,next" @change="loadData" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { getExamRecordList } from '@/api/examRecord'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

function scoreClass(row) {
  const total = row.paper?.totalScore
  if (!total) return ''
  const pct = row.score / total
  if (pct >= 0.8) return 'score-high'
  if (pct >= 0.6) return 'score-mid'
  return 'score-low'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getExamRecordList({ page: page.value, size: size.value, studentName: userStore.realName || userStore.username })
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } finally { loading.value = false }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';

.score-text {
  font-size: 18px;
  font-weight: 700;
  &.score-high { color: $success; }
  &.score-mid { color: $warning; }
  &.score-low { color: $danger; }
}
.score-total {
  color: $text-muted;
  font-size: 13px;
}
.text-muted { color: $text-muted; }
</style>
