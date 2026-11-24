<template>
  <div class="page-container">
    <div class="page-header">
      <h2>班级管理</h2>
      <div>
        <el-button v-if="isAdmin" type="primary" @click="openCreate">新建班级</el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="filters.name" placeholder="班级名称" clearable @clear="search" @keyup.enter="search" style="width:220px" />
      <el-select v-model="filters.status" placeholder="状态" clearable @change="search" style="width:130px">
        <el-option label="启用" value="ENABLED" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon title="班级列表加载失败，请稍后重试" />
    <el-table :data="tableData" v-loading="loading">
      <template #empty><el-empty :description="filters.name || filters.status ? '没有符合条件的班级' : '暂无班级'" /></template>
      <el-table-column prop="id" label="ID" width="64" />
      <el-table-column label="班级名称" min-width="170">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="$router.push(`/admin/classes/${row.id}`)">{{ row.name }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="direction" label="培训方向" width="130">
        <template #default="{ row }">{{ row.direction || '-' }}</template>
      </el-table-column>
      <el-table-column label="起止时间" width="200">
        <template #default="{ row }">
          <span v-if="row.startDate || row.endDate">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
            {{ row.status === 'ENABLED' ? '启用' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="studentCount" label="学员数" width="80" align="center" />
      <el-table-column prop="teacherCount" label="教师数" width="80" align="center" />
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="$router.push(`/admin/classes/${row.id}`)">详情</el-button>
          <el-button v-if="isAdmin" size="small" link :type="row.status === 'ENABLED' ? 'warning' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
          </el-button>
          <el-button v-if="isAdmin" size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:16px;text-align:right">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total,sizes,prev,pager,next" @change="loadData" />
    </div>

    <!-- 新建班级 -->
    <el-dialog v-model="createVisible" title="新建班级" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="全局唯一，不超过 50 字" />
        </el-form-item>
        <el-form-item label="培训方向">
          <el-input v-model="form.direction" placeholder="如：Java 后端" />
        </el-form-item>
        <el-form-item label="起止时间">
      <div class="date-fields">
        <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" clearable />
        <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" clearable />
      </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">创建并进入详情</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getClassList, createClass, updateClassStatus, deleteClass } from '@/api/classManage'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'ADMIN')
const loading = ref(false)
const loadError = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const filters = reactive({ name: '', status: '' })

// 新建班级
const createVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({ name: '', direction: '', startDate: null, endDate: null })
const rules = {
  name: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
}

function openCreate() {
  form.name = ''
  form.direction = ''
  form.startDate = null
  form.endDate = null
  createVisible.value = true
}

async function loadData() {
  loading.value = true
  loadError.value = false
  try {
    const res = await getClassList({ page: page.value, size: size.value, ...filters })
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } catch {
    loadError.value = true
    tableData.value = []
    total.value = 0
  } finally { loading.value = false }
}

function search() { page.value = 1; loadData() }
function resetFilters() {
  Object.assign(filters, { name: '', status: '' })
  search()
}

async function handleCreate() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.startDate && form.endDate && form.startDate > form.endDate) {
    ElMessage.warning('结束日期不能早于开始日期')
    return
  }
  saving.value = true
  try {
    const created = await createClass({
      name: form.name,
      direction: form.direction || undefined,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
    })
    createVisible.value = false
    ElMessage.success('创建成功')
    if (created?.id) router.push(`/admin/classes/${created.id}`)
    else loadData()
  } catch {} finally { saving.value = false }
}

async function toggleStatus(row) {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await updateClassStatus(row.id, next)
  ElMessage.success(next === 'ENABLED' ? '已启用' : '已停用')
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除班级"${row.name}"？删除后学员/教师将无法再看到该班。`, '删除班级', { type: 'warning' })
  await deleteClass(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';

.muted { color: $text-muted; }
</style>
