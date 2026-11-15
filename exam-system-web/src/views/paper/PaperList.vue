<template>
  <div class="page-container">
    <div class="page-header">
      <h2>试卷管理</h2>
      <div>
        <el-button type="primary" @click="$router.push('/admin/papers/create')">手动组卷</el-button>
        <el-button type="primary" plain @click="$router.push('/admin/papers/ai-create')">AI 智能组卷</el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input v-model="searchName" placeholder="搜索试卷名称" clearable @keyup.enter="loadData" style="width:200px" />
      <el-select v-model="searchStatus" placeholder="状态" clearable @change="loadData" style="width:130px">
        <el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已停止" value="STOPPED" />
      </el-select>
      <el-button type="primary" @click="loadData">搜索</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="试卷名称" min-width="200" />
      <el-table-column prop="duration" label="时长(分)" width="90" />
      <el-table-column prop="questionCount" label="题目数" width="80" />
      <el-table-column prop="totalScore" label="总分" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'DRAFT' ? 'info' : 'danger'" size="small">
            {{ row.status === 'PUBLISHED' ? '已发布' : row.status === 'DRAFT' ? '草稿' : '已停止' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/admin/papers/create?id=${row.id}`)">编辑</el-button>
          <el-button size="small" link type="primary" @click="$router.push(`/admin/papers/${row.id}/records`)">考生成绩</el-button>
          <el-button size="small" v-if="row.status === 'DRAFT'" type="success" @click="changeStatus(row, 'PUBLISHED')">发布</el-button>
          <el-button size="small" v-if="row.status === 'PUBLISHED'" type="warning" @click="changeStatus(row, 'STOPPED')">停止</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { getPaperList, updatePaperStatus, deletePaper } from '@/api/paper'

const loading = ref(false)
const tableData = ref([])
const searchName = ref('')
const searchStatus = ref('')

async function loadData() {
  loading.value = true
  try {
    const params = {}
    if (searchName.value) params.name = searchName.value
    if (searchStatus.value) params.status = searchStatus.value
    tableData.value = await getPaperList(params) || []
  } finally { loading.value = false }
}

async function changeStatus(row, status) {
  await updatePaperStatus(row.id, status)
  ElMessage.success(status === 'PUBLISHED' ? '已发布' : '已停止')
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除试卷"${row.name}"？`, '警告', { type: 'warning' })
  await deletePaper(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
