<template>
  <div class="page-container">
    <div class="page-header"><h2>AI 智能组卷</h2></div>

    <el-form :model="form" label-width="80px" style="max-width:800px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="试卷名称" required><el-input v-model="form.name" placeholder="如：Java基础测试卷" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="考试时长"><el-input-number v-model="form.duration" :min="10" :max="300" /> 分钟</el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
    </el-form>

    <div class="page-header" style="margin-top:24px">
      <h3>组卷规则</h3>
      <el-button size="small" @click="addRule">+ 添加规则</el-button>
    </div>

    <div v-for="(rule, i) in form.rules" :key="i" class="rule-row">
      <el-row :gutter="12" align="middle">
        <el-col :span="5">
          <el-select v-model="rule.type" placeholder="题型">
            <el-option label="选择题" value="CHOICE" /><el-option label="判断题" value="JUDGE" /><el-option label="简答题" value="TEXT" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-select v-model="rule.categoryIds" multiple placeholder="分类(可选)" collapse-tags>
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <span class="rule-label">数量</span>
          <el-input-number v-model="rule.count" :min="1" :max="50" size="small" style="width:80px" />
        </el-col>
        <el-col :span="4">
          <span class="rule-label">每题分值</span>
          <el-input-number v-model="rule.score" :min="1" :max="50" size="small" style="width:80px" />
        </el-col>
        <el-col :span="3">
          <el-button @click="form.rules.splice(i,1)" type="danger" size="small" :icon="'Delete'" circle />
        </el-col>
      </el-row>
    </div>

    <div v-if="!form.rules.length" style="padding:40px;text-align:center;color:#94a3b8">
      暂未添加规则，请点击"添加规则"配置组卷要求
    </div>

    <div class="rule-summary" v-if="form.rules.length">
      <span>总题数：{{ totalCount }} 题</span>
      <span>总分：{{ totalScore }} 分</span>
    </div>

    <div style="margin-top:24px;text-align:right">
      <el-button @click="$router.back()">取消</el-button>
      <el-button type="primary" @click="handleGenerate" :loading="generating">开始 AI 组卷</el-button>
    </div>

    <!-- 结果 -->
    <div v-if="result" style="margin-top:32px">
      <el-alert title="AI 组卷完成" type="success" show-icon closable=false />
      <el-descriptions :column="2" border style="margin-top:16px">
        <el-descriptions-item label="试卷名称">{{ result.name }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ result.status === 'DRAFT' ? '草稿' : result.status }}</el-descriptions-item>
        <el-descriptions-item label="总题数">{{ result.questionCount }}</el-descriptions-item>
        <el-descriptions-item label="总分">{{ result.totalScore }}</el-descriptions-item>
        <el-descriptions-item label="时长">{{ result.duration }}分钟</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiCreatePaper } from '@/api/paper'
import { getCategories } from '@/api/category'

const categories = ref([])
const generating = ref(false)
const result = ref(null)

const form = reactive({
  name: '',
  description: '',
  duration: 60,
  rules: [{ type: 'CHOICE', categoryIds: [], count: 10, score: 3 }],
})

const totalCount = computed(() => form.rules.reduce((s, r) => s + (r.count || 0), 0))
const totalScore = computed(() => form.rules.reduce((s, r) => s + (r.count || 0) * (r.score || 0), 0))

function addRule() {
  form.rules.push({ type: 'CHOICE', categoryIds: [], count: 5, score: 3 })
}

async function handleGenerate() {
  if (!form.name) { ElMessage.warning('请输入试卷名称'); return }
  if (!form.rules.length) { ElMessage.warning('请添加组卷规则'); return }
  generating.value = true
  try {
    result.value = await aiCreatePaper({
      name: form.name, description: form.description, duration: form.duration,
      rules: form.rules.map(r => ({ type: r.type, categoryIds: r.categoryIds.length ? r.categoryIds : undefined, count: r.count, score: r.score })),
    })
    ElMessage.success('AI 组卷成功！请在试卷列表中查看')
  } finally { generating.value = false }
}

onMounted(async () => {
  try { categories.value = await getCategories() || []; } catch {}
})
</script>

<style lang="scss" scoped>
.rule-row { padding: 12px; background: #f8fafc; border-radius: 8px; margin-bottom: 10px; }
.rule-label { font-size: 12px; color: #94a3b8; margin-right: 6px; }
.rule-summary { margin-top: 16px; padding: 12px 16px; background: #eef2ff; border-radius: 8px;
  display: flex; gap: 32px; font-weight: 600; color: #4f46e5; }
</style>
