<template>
  <div class="page-container" v-loading="loading">
    <template v-if="!loading && loadError">
      <el-empty description="无权访问该班级或班级不存在">
        <el-button type="primary" @click="$router.push('/my/classes')">返回我的班级</el-button>
      </el-empty>
    </template>

    <template v-else-if="detail">
      <div class="page-header">
        <h2>
          <el-icon class="back-btn" @click="$router.push('/my/classes')"><ArrowLeft /></el-icon>
          {{ detail.name }}
          <el-tag v-if="detail.status === 'DISABLED'" type="danger" size="small">已停用</el-tag>
        </h2>
      </div>

      <!-- 班级信息 -->
      <el-descriptions :column="3" border class="info-block">
        <el-descriptions-item label="班级名称">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="培训方向">{{ detail.direction || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.status === 'ENABLED' ? 'success' : 'danger'" size="small">
            {{ detail.status === 'ENABLED' ? '正常' : '已停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始日期">{{ detail.startDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束日期">{{ detail.endDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="我的入班时间">{{ detail.joinedAt || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 班级教师 -->
      <div class="section-card">
        <div class="section-title">班级教师</div>
        <div class="tag-list">
          <template v-if="detail.teachers?.length">
            <el-tag v-for="t in detail.teachers" :key="t.userId" size="large">{{ t.realName }}</el-tag>
          </template>
          <span v-else class="muted">暂无授课教师</span>
        </div>
      </div>

      <!-- 同学列表 -->
      <div class="section-card">
        <div class="section-head">
          <span class="section-title">同学列表（{{ detail.studentCount ?? classmates.length }} 人）</span>
        </div>
        <el-table :data="classmates" v-loading="mateLoading">
          <el-table-column prop="realName" label="姓名" min-width="140">
            <template #default="{ row }">{{ row.realName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="joinedAt" label="入班时间" width="200" />
        </el-table>
        <el-empty v-if="!mateLoading && !classmates.length" description="暂无其他同学" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getMyClasses, getMyClassmates } from '@/api/myClass'

const route = useRoute()
const classId = Number(route.params.id)

const loading = ref(false)
const loadError = ref(false)
const detail = ref(null)

const mateLoading = ref(false)
const classmates = ref([])

async function loadDetail() {
  loading.value = true
  try {
    const list = await getMyClasses() || []
    detail.value = list.find((c) => c.classId === classId) || null
    loadError.value = !detail.value
  } catch {
    loadError.value = true
  } finally { loading.value = false }
}

async function loadClassmates() {
  mateLoading.value = true
  try {
    classmates.value = await getMyClassmates(classId) || []
  } catch {
    classmates.value = []
  } finally { mateLoading.value = false }
}

onMounted(() => {
  loadDetail()
  loadClassmates()
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.back-btn {
  cursor: pointer;
  margin-right: 6px;
  color: $text-secondary;
  vertical-align: -2px;
  &:hover { color: $primary; }
}

.info-block {
  margin-bottom: $space-4;
}

.section-card {
  @include glass;
  border-radius: $radius-lg;
  padding: $space-4 $space-5;
  margin-bottom: $space-4;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $space-3;
}

.section-title {
  display: block;
  font-size: 14.5px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $space-3;
}

.section-head .section-title {
  margin-bottom: 0;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: $space-2;
}

.muted { color: $text-muted; font-size: 13px; }
</style>
