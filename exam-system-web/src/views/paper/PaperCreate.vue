<template>
  <div class="page-container" v-loading="pageLoading">
    <div class="page-header"><h2>{{ isEdit ? '编辑试卷' : '手动组卷' }}</h2></div>

    <el-form :model="form" label-width="80px" style="max-width:800px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="试卷名称" required><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="考试时长" required><el-input-number v-model="form.duration" :min="1" :max="300" /> 分钟</el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
    </el-form>

    <div class="page-header" style="margin-top:24px">
      <h3>选择题目</h3>
      <span class="text-info">已选 {{ selectedCount }} 题，总分 {{ totalScore }}</span>
    </div>

    <!-- 题目池 -->
    <div class="search-bar">
      <el-select v-model="filterCategory" placeholder="分类" clearable @change="loadQuestions" style="width:160px">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="filterType" placeholder="题型" clearable @change="loadQuestions" style="width:120px">
        <el-option label="选择题" value="CHOICE" /><el-option label="判断题" value="JUDGE" /><el-option label="简答题" value="TEXT" /><el-option label="代码题" value="CODE" /><el-option label="代码题" value="CODE" />
      </el-select>
      <el-input v-model="filterKeyword" placeholder="搜索" clearable @keyup.enter="loadQuestions" style="width:180px" />
      <el-button @click="loadQuestions">搜索</el-button>
    </div>

    <el-table :data="questionList" ref="qTable" @selection-change="onSelectionChange" v-loading="qLoading" max-height="400">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="title" label="题目" min-width="240" show-overflow-tooltip />
      <el-table-column label="题型" width="80">
        <template #default="{ row }">{{ TYPE_LABELS[row.type] || row.type || '-' }}</template>
      </el-table-column>
      <el-table-column label="难度" width="70">
        <template #default="{ row }">{{ DIFF_LABELS[row.difficulty] || row.difficulty || '-' }}</template>
      </el-table-column>
      <el-table-column label="分值" width="100">
        <template #default="{ row }">
          <el-input-number v-model="scores[row.id]" :min="1" :max="50" size="small" controls-position="right" style="width:90px" />
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:20px;text-align:right">
      <el-button @click="$router.back()">取消</el-button>
      <el-button type="primary" @click="handleSave" :loading="saving">保存试卷</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getQuestionList } from '@/api/question'
import { getCategories } from '@/api/category'
import { createPaper, updatePaper, getPaperById } from '@/api/paper'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.query.id)
const pageLoading = ref(false)
const TYPE_LABELS = { CHOICE: '选择题', JUDGE: '判断题', TEXT: '简答题', CODE: '代码题' }
const DIFF_LABELS = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
const saving = ref(false)
const qLoading = ref(false)
const categories = ref([])
const questionList = ref([])
const selectedRows = ref([])
const scores = reactive({})

const form = reactive({ name: '', description: '', duration: 60 })
const filterCategory = ref(null)
const filterType = ref('')
const filterKeyword = ref('')

const selectedCount = computed(() => selectedRows.value.length)
const totalScore = computed(() => selectedRows.value.reduce((sum, r) => sum + (scores[r.id] || 0), 0))

function onSelectionChange(rows) { selectedRows.value = rows }

async function loadQuestions() {
  qLoading.value = true
  try {
    const res = await getQuestionList({
      page: 1, size: 100,
      categoryId: filterCategory.value || undefined,
      type: filterType.value || undefined,
      keyword: filterKeyword.value || undefined,
    })
    questionList.value = (res?.records || []).map(q => {
      if (!scores[q.id]) scores[q.id] = q.score || 5
      return q
    })
  } finally { qLoading.value = false }
}

async function handleSave() {
  if (!form.name) { ElMessage.warning('请输入试卷名称'); return }
  if (!selectedRows.value.length) { ElMessage.warning('请选择题目'); return }
  saving.value = true
  try {
    const questions = {}
    selectedRows.value.forEach(r => { questions[r.id] = scores[r.id] || r.score || 5 })
    const data = { name: form.name, description: form.description, duration: form.duration, questions }
    if (isEdit.value) { await updatePaper(Number(route.query.id), data); ElMessage.success('更新成功') }
    else { await createPaper(data); ElMessage.success('创建成功') }
    router.push('/admin/papers')
  } finally { saving.value = false }
}

async function loadPaper() {
  pageLoading.value = true
  try {
    const p = await getPaperById(Number(route.query.id))
    if (p) {
      form.name = p.name; form.description = p.description || ''; form.duration = p.duration
      if (p.questions) {
        p.questions.forEach(q => { scores[q.id] = q.paperScore || q.score || 5 })
        questionList.value = p.questions
        selectedRows.value = p.questions
      }
    }
  } finally { pageLoading.value = false }
}

onMounted(async () => {
  try { categories.value = await getCategories() || []; } catch {}
  if (isEdit.value) { await loadPaper() } else { loadQuestions() }
})
</script>
