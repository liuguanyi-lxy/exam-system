<template>
  <div class="page-container">
    <div class="page-header">
      <h2>成绩排行榜</h2>
    </div>

    <div class="search-bar">
      <el-select v-model="paperId" placeholder="按试卷筛选" clearable @change="loadData" style="width:240px">
        <el-option v-for="p in papers" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <span class="search-hint">按得分从高到低排列，仅统计已批阅的考试</span>
    </div>

    <el-table :data="tableData" v-loading="loading">
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

.search-hint {
  font-size: 12.5px;
  color: $text-muted;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-weight: 600;
  font-size: 12px;
  &.rank-1 {
    background: #fffaeb;
    color: #b54708;
  }
  &.rank-2 {
    background: #f2f4f7;
    color: $text-secondary;
  }
  &.rank-3 {
    background: #fef0e6;
    color: #b54708;
  }
}
.rank-plain {
  display: inline-block;
  width: 24px;
  text-align: center;
  color: $text-muted;
  font-weight: 500;
}
.student-name { font-weight: 500; }
.score-num {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  font-variant-numeric: tabular-nums;
}
.score-total { color: $text-muted; font-size: 12.5px; }
</style>
