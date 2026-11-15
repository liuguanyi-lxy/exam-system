<template>
  <div class="page-container">
    <div class="page-header">
      <h2>题目管理</h2>
      <div>
        <el-button @click="$router.push('/admin/questions/create')" type="primary">新增题目</el-button>
        <el-button @click="$router.push('/admin/questions/batch')">批量导入 / AI生成</el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="filters.keyword" placeholder="搜索题目" clearable @clear="search" @keyup.enter="search" style="width:240px" />
      <el-select v-model="filters.categoryId" placeholder="分类" clearable @change="search" style="width:160px">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="filters.difficulty" placeholder="难度" clearable @change="search" style="width:120px">
        <el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" />
      </el-select>
      <el-select v-model="filters.type" placeholder="题型" clearable @change="search" style="width:120px">
        <el-option label="选择题" value="CHOICE" /><el-option label="判断题" value="JUDGE" /><el-option label="简答题" value="TEXT" /><el-option label="手写代码题" value="CODE" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="题目" min-width="240" show-overflow-tooltip />
      <el-table-column label="题型" width="90">
        <template #default="{ row }">{{ TYPE_LABELS[row.type] || '简答题' }}</template>
      </el-table-column>
      <el-table-column label="难度" width="80">
        <template #default="{ row }">
          <el-tag :type="row.difficulty === 'EASY' ? 'success' : row.difficulty === 'MEDIUM' ? 'warning' : 'danger'" size="small">
            {{ row.difficulty === 'EASY' ? '简单' : row.difficulty === 'MEDIUM' ? '中等' : '困难' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="score" label="分值" width="70" />
      <el-table-column label="分类" width="110">
        <template #default="{ row }">{{ categoryName(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/admin/questions/edit/${row.id}`)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:16px;text-align:right">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="loadData" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { getQuestionList, deleteQuestion } from '@/api/question'
import { getCategories } from '@/api/category'

const loading = ref(false)
const tableData = ref([])
const categories = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const filters = reactive({ keyword: '', categoryId: null, difficulty: '', type: '' })
const TYPE_LABELS = { CHOICE: '选择题', JUDGE: '判断题', TEXT: '简答题', CODE: '手写代码题' }

// 列表接口不返回嵌套的 category 对象，用分类字典补齐，避免"分类"列整列为空
function categoryName(row) {
  if (row.category?.name) return row.category.name
  const hit = categories.value.find((c) => c.id === row.categoryId)
  return hit ? hit.name : '未分类'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getQuestionList({ page: page.value, size: size.value, ...filters })
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } finally { loading.value = false }
}

function search() { page.value = 1; loadData() }
function resetFilters() {
  Object.assign(filters, { keyword: '', categoryId: null, difficulty: '', type: '' })
  search()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除题目"${row.title}"？`, '警告', { type: 'warning' })
  await deleteQuestion(row.id)
  loadData()
}

onMounted(async () => {
  try { categories.value = await getCategories() || []; } catch {}
  loadData()
})
</script>
