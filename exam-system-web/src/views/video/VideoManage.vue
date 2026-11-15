<template>
  <div class="page-container">
    <div class="page-header">
      <h2>视频管理</h2>
      <div style="display:inline-flex;gap:10px">
        <el-button @click="openExternalDialog">添加外链视频</el-button>
        <el-upload :show-file-list="false" :before-upload="handleUpload" accept="video/*" style="display:inline-block">
          <el-button type="primary">上传视频</el-button>
        </el-upload>
      </div>
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
      <el-table-column label="来源" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.sourceType === 1" size="small" type="warning" effect="plain">外链</el-tag>
          <el-tag v-else size="small" effect="plain">站内</el-tag>
        </template>
      </el-table-column>
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

    <!-- 外链视频录入 -->
    <el-dialog v-model="externalDialog" title="添加外链视频" width="460px">
      <el-form label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="externalForm.title" placeholder="视频标题" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="externalForm.categoryId" placeholder="选择分类" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="视频链接" required>
          <el-input v-model="externalForm.url" placeholder="https://www.bilibili.com/video/BV..." />
        </el-form-item>
        <el-form-item label="时长">
          <el-input-number v-model="externalForm.minutes" :min="0" :max="600" style="width:160px" />
          <span style="margin-left:8px;font-size:12px;color:#999">分钟（选填）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="externalDialog = false">取消</el-button>
        <el-button type="primary" :loading="externalSubmitting" @click="handleExternalSubmit">确认添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { getAdminVideos, auditVideo, offlineVideo, deleteVideo, adminUploadVideo } from '@/api/videoAdmin'
import { getVideoCategories } from '@/api/videoCategory'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filters = reactive({ status: null, keyword: '' })
const categories = ref([])
const externalDialog = ref(false)
const externalSubmitting = ref(false)
const externalForm = reactive({ title: '', categoryId: null, url: '', minutes: null })

function openExternalDialog() {
  externalForm.title = ''
  externalForm.categoryId = categories.value[0]?.id ?? null
  externalForm.url = ''
  externalForm.minutes = null
  externalDialog.value = true
}

async function handleExternalSubmit() {
  if (!externalForm.title || !externalForm.categoryId) { ElMessage.warning('请填写标题和分类'); return }
  if (!externalForm.url || !/^https?:\/\//.test(externalForm.url.trim())) {
    ElMessage.warning('请填写以 http(s):// 开头的视频链接'); return
  }
  externalSubmitting.value = true
  try {
    const fd = new FormData()
    fd.append('title', externalForm.title)
    fd.append('categoryId', externalForm.categoryId)
    fd.append('uploaderName', '管理员')
    fd.append('sourceType', 1)
    fd.append('externalUrl', externalForm.url.trim())
    fd.append('duration', externalForm.minutes ? externalForm.minutes * 60 : 0)
    await adminUploadVideo(fd)
    ElMessage.success('外链视频已添加')
    externalDialog.value = false
    loadData()
  } catch {} finally { externalSubmitting.value = false }
}

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
onMounted(async () => {
  loadData()
  try { categories.value = await getVideoCategories() || [] } catch {}
})
</script>
