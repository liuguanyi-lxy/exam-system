<template>
  <div class="page-container">
    <div class="page-header"><h2>批量导入 / AI生成题目</h2></div>

    <el-tabs v-model="activeTab">
      <!-- Excel导入 -->
      <el-tab-pane label="Excel 导入" name="excel">
        <el-steps :active="excelStep" finish-status="success" align-center style="margin-bottom:32px">
          <el-step title="下载模板" /><el-step title="预览数据" /><el-step title="确认导入" />
        </el-steps>

        <div v-if="excelStep === 0" style="text-align:center;padding:40px">
          <p style="margin-bottom:16px">下载标准模板，按格式填写题目数据</p>
          <el-button type="primary" @click="downloadTemplate">下载 Excel 模板</el-button>
          <div style="margin-top:24px">
            <el-upload :show-file-list="false" :before-upload="handlePreview" accept=".xlsx,.xls" drag>
              <el-icon style="font-size:40px;color:#c0c4cc"><UploadFilled /></el-icon>
              <p>将填好的 Excel 拖到这里或点击上传</p>
            </el-upload>
          </div>
        </div>

        <div v-if="excelStep === 1">
          <el-alert title="预览数据" :description="`共 ${previewData.length} 条题目`" type="info" show-icon closable=false style="margin-bottom:16px" />
          <el-table :data="previewData" max-height="400">
            <el-table-column prop="title" label="题目" min-width="200" show-overflow-tooltip />
            <el-table-column prop="type" label="题型" width="80" />
            <el-table-column prop="difficulty" label="难度" width="70" />
            <el-table-column prop="score" label="分值" width="60" />
          </el-table>
          <div style="margin-top:16px;text-align:right">
            <el-button @click="excelStep = 0">返回</el-button>
            <el-button type="primary" @click="handleImport" :loading="importing">确认导入</el-button>
          </div>
        </div>

        <div v-if="excelStep === 2" style="text-align:center;padding:40px">
          <el-result icon="success" title="导入成功" sub-title="题目已成功导入题库" />
          <el-button type="primary" @click="excelStep = 0; previewData = []">继续导入</el-button>
        </div>
      </el-tab-pane>

      <!-- AI生成 -->
      <el-tab-pane label="AI 生成" name="ai">
        <el-form :model="aiForm" label-width="100px" style="max-width:600px">
          <el-form-item label="生成主题" required>
            <el-input v-model="aiForm.topic" placeholder="如：Java基础、Spring框架、数据结构" />
          </el-form-item>
          <el-form-item label="题目数量" required>
            <el-input-number v-model="aiForm.count" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="题目难度">
            <el-select v-model="aiForm.difficulty" clearable>
              <el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="aiForm.categoryId" clearable>
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="包含多选题"><el-switch v-model="aiForm.includeMultiple" /></el-form-item>
          <el-form-item label="额外要求">
            <el-input v-model="aiForm.requirements" type="textarea" :rows="2" placeholder="如：重点考察面向对象特性" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleAiGenerate" :loading="aiGenerating">开始生成</el-button>
          </el-form-item>
        </el-form>

        <div v-if="aiResults.length" style="margin-top:20px">
          <el-alert :title="`AI 生成了 ${aiResults.length} 条题目`" type="success" show-icon closable=false style="margin-bottom:16px" />
          <el-table :data="aiResults" max-height="400">
            <el-table-column prop="title" label="题目" min-width="240" show-overflow-tooltip />
            <el-table-column prop="type" label="题型" width="80" />
            <el-table-column prop="difficulty" label="难度" width="70" />
            <el-table-column prop="score" label="分值" width="60" />
          </el-table>
          <div style="margin-top:12px;text-align:right">
            <el-button type="primary" @click="handleBatchImport" :loading="importing">导入题库</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadTemplate, previewExcel, importExcel, aiGenerateQuestions, batchImportQuestions } from '@/api/questionBatch'
import { getCategories } from '@/api/category'

const activeTab = ref('excel')
const categories = ref([])

// Excel
const excelStep = ref(0)
const previewData = ref([])
const importing = ref(false)
const excelFile = ref(null)

async function handlePreview(file) {
  try {
    previewData.value = await previewExcel(file) || []
    excelFile.value = file
    excelStep.value = 1
  } catch {}
  return false
}

async function handleImport() {
  importing.value = true
  try { await importExcel(excelFile.value); excelStep.value = 2; ElMessage.success('导入成功') }
  finally { importing.value = false }
}

// AI
const aiGenerating = ref(false)
const aiResults = ref([])
const aiForm = reactive({ topic: '', count: 5, difficulty: '', categoryId: null, includeMultiple: false, requirements: '' })

async function handleAiGenerate() {
  if (!aiForm.topic) { ElMessage.warning('请输入生成主题'); return }
  aiGenerating.value = true
  try { aiResults.value = await aiGenerateQuestions({ ...aiForm }) || []; }
  catch {} finally { aiGenerating.value = false }
}

async function handleBatchImport() {
  importing.value = true
  try { await batchImportQuestions(aiResults.value); ElMessage.success('导入成功'); aiResults.value = [] }
  finally { importing.value = false }
}

onMounted(async () => {
  try { categories.value = await getCategories() || []; } catch {}
})
</script>
