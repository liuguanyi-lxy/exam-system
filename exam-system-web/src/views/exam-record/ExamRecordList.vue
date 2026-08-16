<template>
  <div class="page-container">
    <div class="page-header"><h2>考试记录管理</h2></div>

    <div class="search-bar">
      <el-input v-model="filters.studentName" placeholder="考生姓名" clearable style="width:180px" />
      <el-select v-model="filters.status" placeholder="状态" clearable style="width:140px">
        <el-option label="进行中" :value="0" /><el-option label="已完成" :value="1" /><el-option label="已批阅" :value="2" />
      </el-select>
      <el-date-picker v-model="filters.startDate" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width:160px" />
      <el-date-picker v-model="filters.endDate" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width:160px" />
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="studentName" label="考生" width="100" />
      <el-table-column label="试卷" min-width="160">
        <template #default="{ row }">{{ row.paper?.name || '-' }}</template>
      </el-table-column>
      <el-table-column label="得分" width="100">
        <template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === '已批阅' ? 'success' : 'info'" size="small">{{ row.status || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="切屏次数" width="90">
        <template #default="{ row }">{{ row.windowSwitches ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="考试时间" width="180">
        <template #default="{ row }">{{ row.startTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/admin/exam-records/${row.id}`)">详情</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:16px;text-align:right">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        layout="total,sizes,prev,pager,next" @change="loadData" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { getExamRecordList, deleteExamRecord } from '@/api/examRecord'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({ studentName: '', status: null, startDate: '', endDate: '' })

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filters.studentName) params.studentName = filters.studentName
    if (filters.status != null) params.status = filters.status
    if (filters.startDate) params.startDate = filters.startDate
    if (filters.endDate) params.endDate = filters.endDate
    const res = await getExamRecordList(params)
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } finally { loading.value = false }
}

function search() { page.value = 1; loadData() }
function resetFilters() { Object.assign(filters, { studentName: '', status: null, startDate: '', endDate: '' }); search() }

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除考生"${row.studentName}"的考试记录？`, '警告', { type: 'warning' })
  await deleteExamRecord(row.id)
  ElMessage.success('删除成功')
  loadData()
}

import { onMounted } from 'vue'
onMounted(loadData)
</script>
