<template>
  <div class="page-container">
    <div class="page-header">
      <h2>我的班级</h2>
    </div>

    <el-table :data="classList" v-loading="loading">
      <el-table-column prop="name" label="班级名称" min-width="160" />
      <el-table-column label="培训方向" width="130">
        <template #default="{ row }">{{ row.direction || '-' }}</template>
      </el-table-column>
      <el-table-column label="起止时间" width="200">
        <template #default="{ row }">
          <span v-if="row.startDate || row.endDate">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="班级教师" min-width="140">
        <template #default="{ row }">{{ teacherNames(row) }}</template>
      </el-table-column>
      <el-table-column prop="studentCount" label="同学人数" width="90" align="center" />
      <el-table-column prop="joinedAt" label="我的入班时间" width="160" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ENABLED' ? '正常' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="$router.push(`/my/classes/${row.classId}`)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !classList.length" description="暂未加入任何班级" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyClasses } from '@/api/myClass'

const loading = ref(false)
const classList = ref([])

function teacherNames(row) {
  const names = row.teachers?.map((t) => t.realName).filter(Boolean) || []
  return names.length ? names.join('、') : '-'
}

async function loadData() {
  loading.value = true
  try {
    classList.value = await getMyClasses() || []
  } catch {} finally { loading.value = false }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';

.muted { color: $text-muted; }
</style>
