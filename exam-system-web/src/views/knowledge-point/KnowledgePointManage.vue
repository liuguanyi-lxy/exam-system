<template>
  <div class="page-container">
    <div class="page-header">
      <h2>知识点管理</h2>
      <div>
        <el-button type="primary" @click="openCreate">新建知识点</el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="filters.name" placeholder="知识点名称" clearable @clear="search" @keyup.enter="search" style="width:220px" />
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <span class="sort-tip">仅与当前页相邻项交换排序号；同排序号请编辑排序值。</span>
    </div>

    <el-table :data="tableData" v-loading="loading">
      <el-table-column prop="sort" label="排序" width="70" align="center" />
      <el-table-column prop="name" label="名称" min-width="160">
        <template #default="{ row }">
          <el-tag size="small">{{ row.name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column prop="questionCount" label="引用题目数" width="110" align="center" />
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row, $index }">
          <el-button size="small" link type="primary" :disabled="!canMove($index, -1)" @click="move($index, -1)">
            <el-icon><Top /></el-icon>上移
          </el-button>
          <el-button size="small" link type="primary" :disabled="!canMove($index, 1)" @click="move($index, 1)">
            <el-icon><Bottom /></el-icon>下移
          </el-button>
          <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:16px;text-align:right">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        layout="total,prev,pager,next" @change="loadData" />
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑知识点' : '新建知识点'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="全局唯一，不超过 50 字" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sort" :min="0" :step="1" :precision="0" controls-position="right" />
          <span class="sort-tip">{{ isEdit ? '' : '留空则追加到末尾' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getKnowledgePointList, createKnowledgePoint, updateKnowledgePoint,
  deleteKnowledgePoint, sortKnowledgePoints,
} from '@/api/knowledgePoint'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const filters = reactive({ name: '' })

// 新建 / 编辑
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({ id: null, name: '', description: '', sort: undefined })
const rules = {
  name: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const res = await getKnowledgePointList({ page: page.value, size: size.value, ...filters })
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } catch {} finally { loading.value = false }
}

function search() { page.value = 1; loadData() }
function resetFilters() {
  Object.assign(filters, { name: '' })
  search()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, name: '', description: '', sort: undefined })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { id: row.id, name: row.name, description: row.description || '', sort: row.sort })
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const data = {
      name: form.name,
      description: form.description || undefined,
      sort: form.sort ?? undefined,
    }
    if (isEdit.value) await updateKnowledgePoint(form.id, data)
    else await createKnowledgePoint(data)
    dialogVisible.value = false
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    loadData()
  } catch {} finally { saving.value = false }
}

async function handleDelete(row) {
  const tip = row.questionCount > 0
    ? `知识点"${row.name}"已被 ${row.questionCount} 道题引用，删除将被拒绝。确认继续？`
    : `确认删除知识点"${row.name}"？`
  await ElMessageBox.confirm(tip, '删除知识点', { type: 'warning' })
  await deleteKnowledgePoint(row.id)
  ElMessage.success('删除成功')
  loadData()
}

// 上移 / 下移：与相邻行交换 sort 值后整对提交
function canMove(index, direction) {
  const row = tableData.value[index]
  const target = tableData.value[index + direction]
  return Boolean(row && target && row.sort !== target.sort)
}

async function move(index, direction) {
  if (!canMove(index, direction)) return
  const row = tableData.value[index]
  const target = tableData.value[index + direction]
  await sortKnowledgePoints([
    { id: row.id, sort: target.sort },
    { id: target.id, sort: row.sort },
  ])
  ElMessage.success('排序已保存')
  loadData()
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';

.sort-tip {
  margin-left: 10px;
  font-size: 12.5px;
  color: $text-muted;
}
</style>
