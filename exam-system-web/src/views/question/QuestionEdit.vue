<template>
  <div class="page-container">
    <div class="page-header">
      <h2>{{ isEdit ? '编辑题目' : '新增题目' }}</h2>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" style="max-width:800px" v-loading="pageLoading">
      <el-form-item label="题目" prop="title">
        <el-input v-model="form.title" placeholder="请输入题目标题" />
      </el-form-item>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="题型" prop="type">
            <el-select v-model="form.type" @change="onTypeChange">
              <el-option label="选择题" value="CHOICE" /><el-option label="判断题" value="JUDGE" /><el-option label="简答题" value="TEXT" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="难度" prop="difficulty">
            <el-select v-model="form.difficulty">
              <el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="分值" prop="score"><el-input-number v-model="form.score" :min="1" :max="100" /></el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="分类">
        <el-select v-model="form.categoryId" placeholder="选择分类" clearable>
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>

      <!-- 选择题选项编辑 -->
      <template v-if="form.type === 'CHOICE'">
        <el-form-item label="多选题"><el-switch v-model="form.multi" /></el-form-item>
        <el-form-item label="选项">
          <div v-for="(ch, i) in form.choices" :key="i" class="choice-row">
            <el-checkbox v-model="ch.isCorrect" :title="form.multi ? '正确答案' : '正确答案(单选)'" />
            <el-input v-model="ch.content" :placeholder="`选项${String.fromCharCode(65+i)}`" style="flex:1" />
            <el-button :disabled="form.choices.length <= 2" @click="form.choices.splice(i,1)" type="danger" :icon="'Delete'" circle size="small" />
          </div>
          <el-button @click="form.choices.push({ content:'', isCorrect:false, sort:form.choices.length })" size="small" style="margin-top:8px">+ 添加选项</el-button>
        </el-form-item>
      </template>

      <!-- 判断题答案 -->
      <template v-if="form.type === 'JUDGE'">
        <el-form-item label="正确答案">
          <el-radio-group v-model="answerText">
            <el-radio value="正确">正确</el-radio><el-radio value="错误">错误</el-radio>
          </el-radio-group>
        </el-form-item>
      </template>

      <!-- 简答题答案 -->
      <template v-if="form.type === 'TEXT'">
        <el-form-item label="参考答案"><el-input v-model="answerText" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="评分关键词"><el-input v-model="answerKeywords" placeholder="多个关键词用逗号分隔" /></el-form-item>
      </template>

      <el-form-item label="题目解析">
        <el-input v-model="form.analysis" type="textarea" :rows="3" placeholder="选填，考试结束后展示给学生" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
        <el-button @click="$router.back()">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getQuestionById, createQuestion, updateQuestion } from '@/api/question'
import { getCategories } from '@/api/category'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const saving = ref(false)
const pageLoading = ref(false)
const categories = ref([])
const isEdit = computed(() => !!route.params.id)

const answerText = ref('')
const answerKeywords = ref('')

const form = reactive({
  id: null,
  title: '',
  type: 'CHOICE',
  multi: false,
  categoryId: null,
  difficulty: 'EASY',
  score: 5,
  analysis: '',
  choices: [{ content: '', isCorrect: false, sort: 0 }, { content: '', isCorrect: false, sort: 1 }],
})

const rules = {
  title: [{ required: true, message: '请输入题目', trigger: 'blur' }],
  type: [{ required: true }],
  difficulty: [{ required: true }],
  score: [{ required: true }],
}

function onTypeChange() {
  if (form.type === 'CHOICE' && !form.choices.length) {
    form.choices = [{ content: '', isCorrect: false, sort: 0 }, { content: '', isCorrect: false, sort: 1 }]
  }
}

function buildSubmitData() {
  const data = { ...form }
  if (data.type === 'CHOICE') {
    data.answer = { answer: data.choices.filter(c => c.isCorrect).map(c => c.content).join(',') }
  } else if (data.type === 'JUDGE') {
    data.answer = { answer: answerText.value }
  } else {
    data.answer = { answer: answerText.value, keywords: answerKeywords.value }
  }
  return data
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const data = buildSubmitData()
    if (isEdit.value) { await updateQuestion(form.id, data); ElMessage.success('更新成功') }
    else { await createQuestion(data); ElMessage.success('创建成功') }
    router.push('/admin/questions')
  } finally { saving.value = false }
}

onMounted(async () => {
  try { categories.value = await getCategories() || []; } catch {}
  if (isEdit.value) {
    pageLoading.value = true
    try {
      const q = await getQuestionById(Number(route.params.id))
      if (q) {
        Object.assign(form, {
          id: q.id, title: q.title, type: q.type, multi: q.multi || false,
          categoryId: q.categoryId, difficulty: q.difficulty, score: q.score, analysis: q.analysis || '',
          choices: q.choices?.length ? q.choices.map(c => ({ content: c.content, isCorrect: c.isCorrect, sort: c.sort })) : [],
        })
        if (q.answer) {
          answerText.value = q.answer.answer || ''
          answerKeywords.value = q.answer.keywords || ''
        }
      }
    } finally { pageLoading.value = false }
  }
})
</script>

<style lang="scss" scoped>
.choice-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
</style>
