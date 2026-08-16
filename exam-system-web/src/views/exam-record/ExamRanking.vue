<template>
  <div class="page-container">
    <div class="page-header">
      <h2>成绩排行榜</h2>
    </div>

    <div class="search-bar">
      <el-select v-model="paperId" placeholder="筛选试卷" clearable @change="loadData" style="width:240px">
        <el-option v-for="p in papers" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
    </div>

    <el-table :data="tableData" v-loading="loading" :row-class-name="rowClass">
      <el-table-column label="排名" width="80">
        <template #default="{ $index }">
          <span v-if="$index < 3" class="rank-badge" :class="`rank-${$index + 1}`">
            <el-icon v-if="$index === 0"><Trophy /></el-icon>
            <span v-else>{{ $index + 1 }}</span>
          </span>
          <span v-else class="rank-plain">{{ $index + 1 }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="studentName" label="考生" width="120">
        <template #default="{ row }">
          <span class="student-name">{{ row.studentName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="试卷" min-width="160">
        <template #default="{ row }">{{ row.paperName || row.paper?.name || '-' }}</template>
      </el-table-column>
      <el-table-column label="得分" width="130">
        <template #default="{ row }">
          <strong class="score-num text-gradient">{{ row.score }}</strong>
          <span class="score-total"> / {{ row.paperTotalScore || row.paper?.totalScore || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="用时" width="100">
        <template #default="{ row }">{{ row.duration ? `${row.duration}分钟` : '-' }}</template>
      </el-table-column>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ row.startTime || '-' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getExamRanking } from '@/api/examRecord'
import { getPaperList } from '@/api/paper'

const loading = ref(false)
const tableData = ref([])
const papers = ref([])
const paperId = ref(null)

function rowClass({ rowIndex }) {
  if (rowIndex === 0) return 'row-first'
  if (rowIndex === 1) return 'row-second'
  if (rowIndex === 2) return 'row-third'
  return ''
}

async function loadData() {
  loading.value = true
  try { tableData.value = await getExamRanking({ paperId: paperId.value || undefined, limit: 50 }) || []; }
  finally { loading.value = false }
}

onMounted(async () => {
  try { papers.value = await getPaperList({ status: 'PUBLISHED' }) || []; } catch {}
  loadData()
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  &.rank-1 {
    background: linear-gradient(135deg, #f59e0b, #fbbf24);
    box-shadow: 0 4px 12px rgba(245, 158, 11, 0.4);
  }
  &.rank-2 {
    background: linear-gradient(135deg, #94a3b8, #cbd5e1);
    box-shadow: 0 4px 12px rgba(148, 163, 184, 0.4);
  }
  &.rank-3 {
    background: linear-gradient(135deg, #d97706, #f59e0b);
    box-shadow: 0 4px 12px rgba(217, 119, 6, 0.4);
  }
}
.rank-plain {
  color: $text-secondary;
  font-weight: 500;
  padding-left: 10px;
}
.student-name { font-weight: 500; }
.score-num { font-size: 18px; font-weight: 700; }
.score-total { color: $text-muted; font-size: 13px; }

:deep(.row-first) { background: rgba(251, 191, 36, 0.06); }
:deep(.row-second) { background: rgba(203, 213, 225, 0.06); }
:deep(.row-third) { background: rgba(245, 158, 11, 0.05); }
</style>
