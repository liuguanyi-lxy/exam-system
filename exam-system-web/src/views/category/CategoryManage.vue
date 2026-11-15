<template>
  <div class="page-container">
    <div class="page-header">
      <h2>题目分类管理</h2>
      <el-button type="primary" @click="handleAdd">新增分类</el-button>
    </div>

    <el-table :data="tableData" row-key="id" v-loading="loading" default-expand-all>
      <el-table-column prop="name" label="分类名称" min-width="200" />
      <el-table-column prop="sort" label="排序" width="100" />
      <el-table-column prop="count" label="题目数量" width="100">
        <template #default="{ row }">{{ row.count ?? 0 }}</template>
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
        <el-form-item label="分类名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="父分类">
          <el-input :model-value="parentName" disabled />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCategoryTree, addCategory, updateCategory, deleteCategory } from '@/api/category'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const parentName = ref('顶级分类')
const form = ref({ id: null, name: '', parentId: 0, sort: 0, parentName: '' })

async function loadData() {
  loading.value = true
  try { tableData.value = await getCategoryTree() || []; } finally { loading.value = false }
}

function handleAdd() {
  isEdit.value = false
  parentName.value = '顶级分类'
  form.value = { id: null, name: '', parentId: 0, sort: 0 }
  dialogVisible.value = true
}

function handleAddChild(row) {
  isEdit.value = false
  parentName.value = row.name
  form.value = { id: null, name: '', parentId: row.id, sort: 0 }
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form.value, { id: row.id, name: row.name, parentId: row.parentId || 0, sort: row.sort })
  parentName.value = row.parentId ? '—' : '顶级分类'
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (isEdit.value) {
      await updateCategory({ id: form.value.id, name: form.value.name, parentId: form.value.parentId, sort: form.value.sort })
      ElMessage.success('更新成功')
    } else {
      await addCategory({ name: form.value.name, parentId: form.value.parentId, sort: form.value.sort })
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadData()
  } catch { /* error handled by interceptor */ }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确认删除该分类？子分类和关联题目会受影响。', '警告', { type: 'warning' })
  try {
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* cancelled or error */ }
}

onMounted(loadData)
</script>
