<template>
  <div class="page-container">
    <div class="page-header">
      <h2>视频管理</h2>
      <el-upload :show-file-list="false" :before-upload="handleUpload" accept="video/*" style="display:inline-block">
        <el-button type="primary">上传视频</el-button>
      </el-upload>
    </div>

    <div class="search-bar">
      <el-select v-model="filters.status" placeholder="状态" clearable @change="search" style="width:130px">
        <el-option label="待审核" :value="0" /><el-option label="已发布" :value="1" /><el-option label="已拒绝" :value="2" /><el-option label="已下架" :value="3" />
      </el-select>
      <el-input v-model="filters.keyword" placeholder="搜索" clearable @keyup.enter="search" style="width:200px" />
      <el-button type="primary" @click="search">搜索</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading">
      <el-table-column label="视频" min-width="220">
        <template #default="{ row }">
          <div style="display:flex;gap:10px;align-items:center">
            <img v-if="row.coverUrl" :src="row.coverUrl" style="width:80px;height:48px;border-radius:4px;object-fit:cover" />
            <div>{{ row.title }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="uploaderName" label="上传者" width="100" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : row.status === 3 ? 'info' : 'warning'" size="small">
            {{ row.status === 0 ? '待审核' : row.status === 1 ? '已发布' : row.status === 2 ? '已拒绝' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="观看/点赞" width="120">
        <template #default="{ row }">{{ row.viewCount || 0 }} / {{ row.likeCount || 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" size="small" type="success" @click="handleAudit(row, 1)">通过</el-button>
          <el-button v-if="row.status === 0" size="small" type="danger" @click="handleAudit(row, 2)">拒绝</el-button>
          <el-button v-if="row.status === 1" size="small" type="warning" @click="handleOffline(row)">下架</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:16px;text-align:right">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
        layout="total,prev,pager,next" @change="loadData" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { getAdminVideos, auditVideo, offlineVideo, deleteVideo, adminUploadVideo } from '@/api/videoAdmin'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filters = reactive({ status: null, keyword: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await getAdminVideos({
      page: page.value, size: size.value,
      status: filters.status != null ? filters.status : undefined,
      keyword: filters.keyword || undefined,
    })
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } finally { loading.value = false }
}

function search() { page.value = 1; loadData() }

async function handleUpload(file) {
  const fd = new FormData()
  fd.append('title', file.name)
  fd.append('categoryId', '1')
  fd.append('uploaderName', '管理员')
  fd.append('videoFile', file)
  try { await adminUploadVideo(fd); ElMessage.success('上传成功'); loadData() } catch {}
  return false
}

async function handleAudit(row, status) {
  try {
    const reason = status === 2 ? prompt('拒绝原因(可选):') : undefined
    await auditVideo(row.id, status, reason)
    ElMessage.success(status === 1 ? '审核通过' : '已拒绝')
    loadData()
  } catch {}
}

async function handleOffline(row) {
  await ElMessageBox.confirm('确认下架该视频？', '提醒', { type: 'warning' })
  await offlineVideo(row.id)
  ElMessage.success('已下架')
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确认删除该视频？', '警告', { type: 'warning' })
  await deleteVideo(row.id)
  ElMessage.success('删除成功')
  loadData()
}

import { onMounted } from 'vue'
onMounted(loadData)
</script>
