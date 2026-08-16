<template>
  <div class="page-container">
    <div class="page-header">
      <h2>公告管理</h2>
      <el-button type="primary" @click="handleAdd">新增公告</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === 'SYSTEM' ? '' : row.type === 'FEATURE' ? 'success' : 'warning'" size="small">
            {{ row.type === 'SYSTEM' ? '系统' : row.type === 'FEATURE' ? '功能' : '通知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="100">
        <template #default="{ row }">
          <el-tag :type="row.priority === 2 ? 'danger' : row.priority === 1 ? 'warning' : 'info'" size="small">
            {{ row.priority === 2 ? '紧急' : row.priority === 1 ? '重要' : '普通' }}
          </el-tag>
        </template>
      </el-table-column>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑公告' : '新增公告'" width="600px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option label="系统" value="SYSTEM" />
            <el-option label="功能" value="FEATURE" />
            <el-option label="通知" value="NOTICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority">
            <el-option label="普通" :value="0" />
            <el-option label="重要" :value="1" />
            <el-option label="紧急" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="5" /></el-form-item>
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
import { getNotices, addNotice, updateNotice, deleteNotice, toggleNotice } from '@/api/notice'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive({ id: null, title: '', content: '', type: 'NOTICE', priority: 0, isActive: true })

async function loadData() {
  loading.value = true
  try { tableData.value = await getNotices() || []; } finally { loading.value = false }
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, { id: null, title: '', content: '', type: 'NOTICE', priority: 0, isActive: true })
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
    if (isEdit.value) { await updateNotice({ ...form }); ElMessage.success('更新成功') }
    else { await addNotice({ ...form }); ElMessage.success('添加成功') }
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确认删除该公告？', '提醒', { type: 'warning' })
  await deleteNotice(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleToggle(id, isActive) {
  await toggleNotice(id, isActive)
  ElMessage.success(isActive ? '已启用' : '已禁用')
  loadData()
}

onMounted(loadData)
</script>
