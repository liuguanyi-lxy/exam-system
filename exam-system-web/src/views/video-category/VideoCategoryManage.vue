<template>
  <div class="page-container">
    <div class="page-header">
      <h2>视频分类管理</h2>
      <el-button type="primary" @click="handleAdd">新增分类</el-button>
    </div>

    <el-table :data="tableData" row-key="id" v-loading="loading" default-expand-all>
      <el-table-column prop="name" label="分类名称" min-width="200" />
      <el-table-column prop="sortOrder" label="排序" width="100" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '禁用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="success" @click="handleAddChild(row)">添加子分类</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑分类' : '新增分类'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="父分类"><el-input :model-value="parentLabel" disabled /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" :value="1" /><el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getVideoCategoryTree, addVideoCategory, updateVideoCategory, deleteVideoCategory } from '@/api/videoCategory'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const parentLabel = ref('顶级分类')
const form = reactive({ id: null, name: '', description: '', parentId: 0, sortOrder: 0, status: 1 })

async function loadData() {
  loading.value = true
  try { tableData.value = await getVideoCategoryTree() || []; } finally { loading.value = false }
}

function handleAdd() {
  isEdit.value = false; parentLabel.value = '顶级分类'
  Object.assign(form, { id: null, name: '', description: '', parentId: 0, sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

function handleAddChild(row) {
  isEdit.value = false; parentLabel.value = row.name
  Object.assign(form, { id: null, name: '', description: '', parentId: row.id, sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, { id: row.id, name: row.name, description: row.description || '', parentId: row.parentId || 0, sortOrder: row.sortOrder || 0, status: row.status })
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (isEdit.value) { await updateVideoCategory({ ...form }); ElMessage.success('更新成功') }
    else { await addVideoCategory({ ...form }); ElMessage.success('添加成功') }
    dialogVisible.value = false
    loadData()
  } catch {}
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确认删除该分类？', '警告', { type: 'warning' })
  await deleteVideoCategory(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
