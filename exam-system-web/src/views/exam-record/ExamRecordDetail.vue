<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h2>考试记录详情</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <template v-if="record">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="考生">{{ record.studentName }}</el-descriptions-item>
        <el-descriptions-item label="试卷">{{ record.paper?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="得分">{{ record.score != null ? record.score : '未批阅' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ record.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="切屏次数">{{ record.windowSwitches ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="考试时间">{{ record.startTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="record.summary" style="margin-top:16px">
        <el-card><template #header>AI 评语</template>{{ record.summary }}</el-card>
      </div>

      <div v-if="record.answerRecords?.length" style="margin-top:20px">
        <h3>答题详情</h3>
        <el-table :data="record.answerRecords" style="margin-top:12px">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="questionId" label="题目ID" width="80" />
          <el-table-column prop="userAnswer" label="考生答案" min-width="180" />
          <el-table-column prop="score" label="得分" width="70" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.isCorrect === 1 ? 'success' : row.isCorrect === 2 ? 'warning' : 'danger'" size="small">
                {{ row.isCorrect === 1 ? '正确' : row.isCorrect === 2 ? '部分' : '错误' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getExamRecordById } from '@/api/examRecord'

const route = useRoute()
const loading = ref(false)
const record = ref(null)

onMounted(async () => {
  loading.value = true
  try { record.value = await getExamRecordById(Number(route.params.id)); } finally { loading.value = false }
})
</script>
