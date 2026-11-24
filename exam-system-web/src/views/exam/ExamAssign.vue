<template>
  <div class="page-container">
    <div class="page-header">
      <h2>考试分配</h2>
      <el-button type="primary" @click="openCreate">新建分配</el-button>
    </div>

    <div class="search-bar">
      <el-radio-group v-model="filters.viewMode" @change="switchView">
        <el-radio-button value="paper">按试卷</el-radio-button>
        <el-radio-button value="class">按班级</el-radio-button>
      </el-radio-group>
      <el-select
        v-if="filters.viewMode === 'paper'"
        v-model="filters.examId" placeholder="选择试卷" clearable filterable :loading="optionsLoading"
        style="width:240px" @change="search"
      >
        <el-option v-for="p in papers" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-select
        v-else v-model="filters.classId" placeholder="选择班级" clearable filterable :loading="optionsLoading"
        style="width:240px" @change="search"
      >
        <el-option v-for="item in classes" :key="item.id" :label="formatExamClassOption(item)" :value="item.id" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-alert v-if="optionsFailed" class="assignment-message" type="error" :closable="false" show-icon>
      <template #title>班级或试卷选项加载失败 <el-button link type="primary" :loading="optionsLoading" @click="loadOptions">重试</el-button></template>
    </el-alert>

    <el-table :data="tableData" v-loading="loading" row-key="id">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="试卷" min-width="180">
        <template #default="{ row }">{{ row.paperName || row.examName || '-' }}</template>
      </el-table-column>
      <el-table-column label="班级" min-width="150">
        <template #default="{ row }">{{ row.className || '-' }}</template>
      </el-table-column>
      <el-table-column label="考试窗口（北京时间）" min-width="240">
        <template #default="{ row }">
          <span :class="{ muted: !row.startTime && !row.endTime }">
            {{ fmtSide(row.startTime) }} ~ {{ fmtSide(row.endTime) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="总次数" width="80" align="center">
        <template #default="{ row }">{{ row.maxAttempts ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
            {{ row.status === 'ENABLED' ? '启用' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" link type="primary" @click="openStudents(row)">学员进度</el-button>
          <el-button size="small" link type="danger" :disabled="revokingId !== null" :loading="revokingId === row.id" @click="handleRevoke(row)">撤销</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="listFailed ? '分配列表加载失败' : '没有符合条件的考试分配'">
          <el-button v-if="listFailed" @click="loadData">重新加载</el-button>
        </el-empty>
      </template>
    </el-table>

    <div style="margin-top:16px;text-align:right">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total,sizes,prev,pager,next" @change="loadData" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingRow ? '编辑分配' : '新建分配'" width="min(560px, calc(100% - 32px))"
      destroy-on-close :show-close="!saving" :close-on-click-modal="!saving" :close-on-press-escape="!saving">
      <el-form ref="formRef" :model="form" :rules="rules" :disabled="saving" label-width="100px">
        <el-form-item v-if="editingRow" label="试卷">
          <el-input :model-value="editingRow.paperName || editingRow.examName || String(editingRow.examId)" disabled />
        </el-form-item>
        <el-form-item v-else label="试卷" prop="examId">
          <el-select v-model="form.examId" filterable :loading="optionsLoading" :disabled="optionsFailed"
            placeholder="仅已发布试卷可分配" no-data-text="暂无已发布试卷" style="width:100%">
            <el-option v-for="p in publishedPapers" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingRow" label="目标班级" prop="classIds">
          <el-select v-model="form.classIds" multiple filterable collapse-tags collapse-tags-tooltip :multiple-limit="100" placement="top-start"
            :loading="optionsLoading" :disabled="optionsFailed" placeholder="选择启用的班级（最多 100 个）"
            no-data-text="暂无可分配班级" style="width:100%">
            <el-option v-for="item in enabledClasses" :key="item.id" :label="formatExamClassOption(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="目标班级">
          <el-input :model-value="editingRow.className || editingRow.classId" disabled />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" clearable placeholder="留空 = 立即生效"
            value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" clearable placeholder="留空 = 不限结束"
            value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="总考试次数" prop="maxAttempts">
          <el-input-number v-model="form.maxAttempts" :min="1" :max="2147483647" :precision="0" />
          <span class="form-tip">{{ editingRow ? '已有学员开考后不可修改' : '含首次，设为 1 表示不能重考' }}</span>
        </el-form-item>
      </el-form>
      <el-alert v-if="!editingRow && optionsFailed" type="error" :closable="false" show-icon>
        <template #title>班级或试卷选项加载失败 <el-button link type="primary" :loading="optionsLoading" @click="loadOptions">重试</el-button></template>
      </el-alert>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="createUnavailable" @click="handleSubmit">
          {{ editingRow ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="studentsVisible" :title="`学员进度 - ${currentRow?.paperName || currentRow?.examName || ''} × ${currentRow?.className || ''}`"
      width="min(680px, calc(100% - 32px))" destroy-on-close>
      <el-table :data="students" v-loading="studentsLoading" row-key="userId" max-height="420">
        <el-table-column label="姓名" min-width="120">
          <template #default="{ row }">{{ row.realName || '-' }} <span v-if="row.userId" class="muted">#{{ row.userId }}</span></template>
        </el-table-column>
        <el-table-column label="尝试次数" width="100" align="center">
          <template #default="{ row }">{{ row.attemptCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="最高分" width="100" align="center">
          <template #default="{ row }">{{ row.maxScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="最近状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.latestStatus)" size="small">{{ row.latestStatus || '未考' }}</el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="studentsFailed ? '学员进度加载失败' : '暂无学员进度'">
            <el-button v-if="studentsFailed" @click="openStudents(currentRow)">重新加载</el-button>
          </el-empty>
        </template>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createExamClass, deleteExamClass, formatExamClassOption, getExamClassList,
  getExamClassOptions, getExamClassStudents, normalizeWindowTime, updateExamClass,
} from '@/api/examClass'
import { getPaperList } from '@/api/paper'

const route = useRoute()
const loading = ref(false)
const listFailed = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
let listRequest = 0

const filters = reactive({ viewMode: 'paper', examId: null, classId: null })

const papers = ref([])
const classes = ref([])
const optionsLoading = ref(false)
const optionsFailed = ref(false)
const publishedPapers = computed(() => papers.value.filter((p) => p.status === 'PUBLISHED'))
const enabledClasses = computed(() => classes.value.filter((item) => item.status === 'ENABLED'))
const preselectExamId = computed(() => {
  const id = Number(route.query.examId)
  return Number.isSafeInteger(id) && id > 0 ? id : null
})

async function loadData() {
  const request = ++listRequest
  loading.value = true
  listFailed.value = false
  try {
    const params = { page: page.value, size: size.value }
    if (filters.viewMode === 'paper' && filters.examId) params.examId = filters.examId
    if (filters.viewMode === 'class' && filters.classId) params.classId = filters.classId
    const result = await getExamClassList(params)
    if (request !== listRequest) return
    tableData.value = result.records
    total.value = result.total
  } catch {
    if (request !== listRequest) return
    tableData.value = []
    listFailed.value = true
  } finally {
    if (request === listRequest) loading.value = false
  }
}

function search() {
  if (page.value === 1) loadData()
  else page.value = 1
}

function switchView() {
  filters.examId = null
  filters.classId = null
  search()
}

function resetFilters() {
  filters.viewMode = 'paper'
  filters.examId = preselectExamId.value
  filters.classId = null
  search()
}

async function loadOptions() {
  if (optionsLoading.value) return
  optionsLoading.value = true
  optionsFailed.value = false
  const [paperResult, classResult] = await Promise.allSettled([getPaperList(), getExamClassOptions()])
  papers.value = paperResult.status === 'fulfilled' ? paperResult.value || [] : []
  classes.value = classResult.status === 'fulfilled' ? classResult.value : []
  optionsFailed.value = paperResult.status === 'rejected' || classResult.status === 'rejected'
  optionsLoading.value = false
}

function fmtSide(value) {
  return normalizeWindowTime(value)?.slice(0, 16) || '不限'
}

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const editingRow = ref(null)
const form = reactive({ examId: null, classIds: [], startTime: '', endTime: '', maxAttempts: 1 })
const createUnavailable = computed(() => !editingRow.value &&
  (optionsLoading.value || optionsFailed.value || !publishedPapers.value.length || !enabledClasses.value.length))
const rules = {
  examId: [{ required: true, message: '请选择试卷', trigger: 'change' }],
  classIds: [{ required: true, type: 'array', min: 1, message: '请至少选择一个班级', trigger: 'change' }],
  maxAttempts: [{ required: true, message: '请填写总考试次数', trigger: 'blur' }],
}

function openCreate() {
  const classId = filters.viewMode === 'class' ? filters.classId : null
  const examId = filters.viewMode === 'paper' ? filters.examId : null
  editingRow.value = null
  form.examId = publishedPapers.value.some((item) => item.id === examId) ? examId : null
  form.classIds = enabledClasses.value.some((item) => item.id === classId) ? [classId] : []
  form.startTime = ''
  form.endTime = ''
  form.maxAttempts = 1
  dialogVisible.value = true
}

function openEdit(row) {
  editingRow.value = row
  form.examId = row.examId
  form.classIds = []
  form.startTime = normalizeWindowTime(row.startTime) || ''
  form.endTime = normalizeWindowTime(row.endTime) || ''
  form.maxAttempts = row.maxAttempts ?? 1
  dialogVisible.value = true
}

async function handleSubmit() {
  if (saving.value || createUnavailable.value) return
  saving.value = true
  try {
    if (!await formRef.value.validate().catch(() => false)) return
    if (form.startTime && form.endTime && form.startTime > form.endTime) {
      ElMessage.warning('结束时间不能早于开始时间')
      return
    }
    if (editingRow.value) {
      await updateExamClass(editingRow.value.id, {
        startTime: form.startTime,
        endTime: form.endTime,
        maxAttempts: form.maxAttempts,
      })
      ElMessage.success('保存成功')
    } else {
      const results = await createExamClass({
        examId: form.examId,
        classIds: form.classIds,
        startTime: form.startTime,
        endTime: form.endTime,
        maxAttempts: form.maxAttempts,
      })
      const created = results.filter((item) => item.status === 'CREATED').length
      const revived = results.filter((item) => item.status === 'REVIVED').length
      const existing = results.filter((item) => item.status === 'EXISTS').length
      if (created + revived === 0) ElMessage.info('所选班级均已分配此试卷，原设置未变更')
      else ElMessage.success(`新增 ${created} 个、恢复 ${revived} 个，已分配未变更 ${existing} 个`)
    }
    dialogVisible.value = false
    loadData()
  } catch {} finally { saving.value = false }
}

const revokingId = ref(null)

async function handleRevoke(row) {
  if (revokingId.value !== null) return
  revokingId.value = row.id
  try {
    await ElMessageBox.confirm(
      `确认撤销「${row.paperName || row.examName || '该试卷'}」对「${row.className || '该班级'}」的分配？撤销后不能通过该分配发起新考试，历史成绩保留。`,
      '撤销分配', { type: 'warning' },
    )
    await deleteExamClass(row.id)
    ElMessage.success('已撤销')
    if (tableData.value.length === 1 && page.value > 1) page.value--
    else loadData()
  } catch {} finally { revokingId.value = null }
}

const studentsVisible = ref(false)
const studentsLoading = ref(false)
const studentsFailed = ref(false)
const students = ref([])
const currentRow = ref(null)
let studentsRequest = 0

async function openStudents(row) {
  const request = ++studentsRequest
  currentRow.value = row
  students.value = []
  studentsVisible.value = true
  studentsLoading.value = true
  studentsFailed.value = false
  try {
    const result = await getExamClassStudents(row.id)
    if (request === studentsRequest) students.value = result || []
  } catch {
    if (request === studentsRequest) studentsFailed.value = true
  } finally {
    if (request === studentsRequest) studentsLoading.value = false
  }
}

function statusTagType(status) {
  if (status === '已批阅') return 'success'
  if (status === '已交') return 'primary'
  if (status === '进行中') return 'warning'
  return 'info'
}

watch(preselectExamId, resetFilters, { immediate: true })
onMounted(loadOptions)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';

.muted { color: $text-muted; }
.assignment-message { margin-bottom: 16px; }
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: $text-muted;
}
</style>
