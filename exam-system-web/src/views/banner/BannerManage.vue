<template>
  <div class="page-container">
    <div class="page-header">
      <h2>轮播图管理</h2>
      <el-button type="primary" @click="handleAdd">新增轮播图</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="图片" width="120">
        <template #default="{ row }"><el-image :src="row.imageUrl" style="width:80px;height:45px;border-radius:4px" fit="cover" /></template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="150" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch :model-value="row.isActive" @change="(v) => handleToggle(row.id, v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑轮播图' : '新增轮播图'" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="图片">
          <el-upload :show-file-list="false" :before-upload="handleUpload" accept="image/*">
            <el-button>选择图片</el-button>
          </el-upload>
          <el-image v-if="form.imageUrl" :src="form.imageUrl" style="width:200px;height:113px;margin-top:8px;border-radius:6px" fit="cover" />
        </el-form-item>
        <el-form-item label="跳转链接"><el-input v-model="form.linkUrl" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.isActive" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBanners, addBanner, updateBanner, deleteBanner, toggleBanner, uploadBannerImage } from '@/api/banner'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive({ id: null, title: '', description: '', imageUrl: '', linkUrl: '', sortOrder: 0, isActive: true })

async function loadData() {
  loading.value = true
  try { tableData.value = await getBanners() || []; } finally { loading.value = false }
}

async function handleUpload(file) {
  const url = await uploadBannerImage(file)
  form.imageUrl = url
  ElMessage.success('上传成功')
  return false
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, { id: null, title: '', description: '', imageUrl: '', linkUrl: '', sortOrder: 0, isActive: true })
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (isEdit.value) { await updateBanner({ ...form }); ElMessage.success('更新成功') }
    else { await addBanner({ ...form }); ElMessage.success('添加成功') }
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确认删除该轮播图？', '提醒', { type: 'warning' })
  await deleteBanner(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleToggle(id, isActive) {
  await toggleBanner(id, isActive)
  ElMessage.success(isActive ? '已启用' : '已禁用')
  loadData()
}

onMounted(loadData)
</script>
