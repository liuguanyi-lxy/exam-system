<template>
  <div class="page-container course-manage">
    <div class="page-header">
      <div>
        <h2>课程管理</h2>
        <p class="subtitle">组织课程内容、班级资源与学员进度</p>
      </div>
      <el-button type="primary" @click="openCourseDialog()">
        <el-icon><Plus /></el-icon> 新建课程
      </el-button>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="search">
        <el-form-item label="课程名称">
          <el-input v-model="filters.keyword" clearable placeholder="搜索课程" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" @change="search">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">筛选</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="courses" v-loading="loading" row-key="id" class="course-table">
      <el-table-column label="课程" min-width="260">
        <template #default="{ row }">
          <div class="course-name-cell">
            <el-image v-if="row.coverUrl" :src="row.coverUrl" fit="cover" class="course-thumb">
              <template #error><div class="thumb-placeholder"><el-icon><Reading /></el-icon></div></template>
            </el-image>
            <div v-else class="course-thumb thumb-placeholder"><el-icon><Reading /></el-icon></div>
            <div>
              <strong>{{ row.name }}</strong>
              <p class="muted line-clamp">{{ row.description || '暂无课程简介' }}</p>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="内容" width="130" align="center">
        <template #default="{ row }">{{ row.chapterCount || 0 }} 章节</template>
      </el-table-column>
      <el-table-column label="班级" width="130" align="center">
        <template #default="{ row }">{{ row.classCount || 0 }} 个班级</template>
      </el-table-column>
      <el-table-column prop="creatorName" label="创建者" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
            {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="最近更新" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openAssign(row)">{{ canEdit(row) ? '分配班级' : '查看分配' }}</el-button>
          <el-dropdown v-if="canEdit(row)" @command="(command) => handleCourseCommand(command, row)">
            <el-button link type="primary">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="'toggle'">{{ row.status === 'PUBLISHED' ? '转为草稿' : '发布课程' }}</el-dropdown-item>
                <el-dropdown-item command="edit">编辑信息</el-dropdown-item>
                <el-dropdown-item command="delete" divided class="danger-item">删除课程</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
      <template #empty><el-empty description="没有找到课程" /></template>
    </el-table>

    <div class="pagination-row">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @change="loadCourses" />
    </div>

    <el-dialog v-model="courseDialogVisible" :title="editingCourse ? '编辑课程信息' : '新建课程'" width="560px" destroy-on-close>
      <el-form ref="courseFormRef" :model="courseForm" :rules="courseRules" label-width="88px">
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="courseForm.name" maxlength="100" show-word-limit placeholder="例如：Java 入门" />
        </el-form-item>
        <el-form-item label="课程简介" prop="description">
          <el-input v-model="courseForm.description" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="介绍课程内容与学习目标" />
        </el-form-item>
        <el-form-item label="封面地址">
          <el-input v-model="courseForm.coverUrl" placeholder="https://…（可选）" />
          <div class="form-hint">可粘贴已上传的图片 URL；上传入口沿用文件服务。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="courseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCourse">{{ editingCourse ? '保存修改' : '创建课程' }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="assignVisible" title="分配课程班级" size="520px">
      <div v-loading="assignLoading" class="drawer-content">
        <p class="drawer-intro">教师只看到负责的班级。停用班级中的学员无法访问课程。</p>
        <el-checkbox-group v-model="selectedClassIds" class="class-options" :disabled="!canEdit(assignCourse)">
          <label v-for="item in classes" :key="item.id" class="class-option">
            <el-checkbox :value="item.id" />
            <span class="class-option-main">
              <strong>{{ item.name }}</strong>
              <small>{{ item.direction || '培训班级' }} · {{ item.studentCount || 0 }} 位学员</small>
            </span>
            <el-tag v-if="item.status === 'DISABLED'" type="info" size="small">已停用</el-tag>
          </label>
        </el-checkbox-group>
        <el-empty v-if="!assignLoading && !classes.length" description="暂无可分配班级" />
      </div>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button v-if="canEdit(assignCourse)" type="primary" :loading="assignSaving" :disabled="!assignReady || assignLoading" @click="saveAssignment">保存分配</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="progressVisible" :title="`${progressCourse?.name || '课程'} · 学习情况`" width="920px" destroy-on-close>
      <div class="progress-filter">
        <span>查看班级</span>
        <el-select v-model="progressClassId" placeholder="选择班级" style="width:260px" @change="loadProgress">
          <el-option v-for="item in progressClasses" :key="item.classId" :label="item.className" :value="item.classId" />
        </el-select>
      </div>
      <el-table :data="progressStudents" v-loading="progressLoading" max-height="480">
        <el-table-column label="学员" min-width="180">
          <template #default="{ row }">
            <span>{{ row.realName || row.username }}</span>
            <small class="muted username">{{ row.username }}</small>
          </template>
        </el-table-column>
        <el-table-column label="课程进度" min-width="210">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercent || 0" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="视频完成" width="130" align="center">
          <template #default="{ row }">{{ row.completedVideoCount || 0 }} / {{ row.requiredVideoTotal || 0 }} 必学</template>
        </el-table-column>
        <el-table-column label="章节完成" width="120" align="center">
          <template #default="{ row }">{{ row.completedChapterCount || 0 }} / {{ row.chapterTotal || 0 }}</template>
        </el-table-column>
        <el-table-column prop="lastLearnTime" label="最近学习" width="180">
          <template #default="{ row }">{{ row.lastLearnTime || '尚未开始' }}</template>
        </el-table-column>
        <template #empty><el-empty description="所选班级暂无学员进度" /></template>
      </el-table>
      <template #footer><el-button @click="progressVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Plus, Reading } from '@element-plus/icons-vue'
import { getClassList } from '@/api/classManage'
import { useUserStore } from '@/stores/user'
import {
  assignCourseClasses, createCourse, deleteCourse, getCourseClasses, getCourseClassProgress,
  getCourseList, updateCourse, updateCourseStatus,
} from '@/api/course'

const userStore = useUserStore()
const loading = ref(false)
const courses = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filters = reactive({ keyword: '', status: '' })

const courseDialogVisible = ref(false)
const courseFormRef = ref()
const editingCourse = ref(null)
const saving = ref(false)
const courseForm = reactive({ name: '', description: '', coverUrl: '' })
const courseRules = {
  name: [{ required: true, whitespace: true, message: '请输入课程名称', trigger: 'blur' }],
  description: [{ max: 500, message: '课程简介不能超过 500 字', trigger: 'blur' }],
}

const assignVisible = ref(false)
const assignLoading = ref(false)
const assignReady = ref(false)
const assignSaving = ref(false)
const assignCourse = ref(null)
const classes = ref([])
const selectedClassIds = ref([])

const progressVisible = ref(false)
const progressLoading = ref(false)
const progressCourse = ref(null)
const progressClasses = ref([])
const progressClassId = ref(null)
const progressStudents = ref([])

async function loadCourses() {
  loading.value = true
  try {
    const result = await getCourseList({
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
    })
    courses.value = result?.records || []
    total.value = result?.total || 0
  } catch {
    courses.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadCourses()
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

function openCourseDialog(course = null) {
  editingCourse.value = course
  courseForm.name = course?.name || ''
  courseForm.description = course?.description || ''
  courseForm.coverUrl = course?.coverUrl || ''
  courseDialogVisible.value = true
}

async function saveCourse() {
  const valid = await courseFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  const payload = {
    name: courseForm.name.trim(),
    description: courseForm.description.trim(),
    coverUrl: courseForm.coverUrl.trim() || null,
  }
  try {
    if (editingCourse.value) {
      await updateCourse(editingCourse.value.id, payload)
      ElMessage.success('课程信息已更新')
    } else {
      await createCourse(payload)
      ElMessage.success('课程已创建')
      page.value = 1
      filters.keyword = ''
      filters.status = ''
    }
    courseDialogVisible.value = false
    loadCourses()
  } catch {
    // 请求拦截器负责展示接口错误。
  } finally {
    saving.value = false
  }
}

function canEdit(course) {
  return !!course && (userStore.role === 'ADMIN' || String(course.createdBy) === String(userStore.userId))
}

async function handleCourseCommand(command, course) {
  if (command === 'edit') {
    openCourseDialog(course)
    return
  }
  if (command === 'toggle') {
    const status = course.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED'
    try {
      await updateCourseStatus(course.id, status)
      ElMessage.success(status === 'PUBLISHED' ? '课程已发布' : '课程已转为草稿')
      loadCourses()
    } catch {
      // 请求拦截器负责展示接口错误。
    }
    return
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm(`确认删除课程“${course.name}”？课程章节与班级分配将一并移除，学习历史会保留。`, '删除课程', { type: 'warning' })
      await deleteCourse(course.id)
      ElMessage.success('课程已删除')
      if (courses.value.length === 1 && page.value > 1) page.value -= 1
      loadCourses()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') return
    }
  }
}

async function openAssign(course) {
  if (assignLoading.value || assignSaving.value) return
  assignCourse.value = course
  assignVisible.value = true
  assignLoading.value = true
  assignReady.value = false
  classes.value = []
  selectedClassIds.value = []
  try {
    const allocation = await getCourseClasses(course.id)
    if (canEdit(course)) {
      const results = []
      let classPage = 1
      let pageCount = 1
      do {
        const data = await getClassList({ page: classPage, size: 100 })
        results.push(...(data?.records || []))
        pageCount = data?.pages || Math.ceil((data?.total || 0) / 100)
        classPage += 1
      } while (classPage <= pageCount)
      classes.value = results
    } else {
      classes.value = (allocation?.classes || []).map(item => ({ ...item, id: item.classId, name: item.className }))
    }
    selectedClassIds.value = allocation?.classIds || []
    assignReady.value = true
  } catch {
    classes.value = []
    selectedClassIds.value = []
  } finally {
    assignLoading.value = false
  }
}

async function saveAssignment() {
  if (!canEdit(assignCourse.value) || !assignReady.value || assignLoading.value || assignSaving.value) return
  assignSaving.value = true
  try {
    await assignCourseClasses(assignCourse.value.id, selectedClassIds.value)
    ElMessage.success('班级分配已保存')
    assignVisible.value = false
    loadCourses()
  } catch {
    // 请求拦截器负责展示接口错误。
  } finally {
    assignSaving.value = false
  }
}

async function openProgress(course) {
  progressCourse.value = course
  progressVisible.value = true
  progressLoading.value = true
  progressClassId.value = null
  progressStudents.value = []
  try {
    const result = await getCourseClasses(course.id)
    progressClasses.value = result?.classes || []
    progressClassId.value = progressClasses.value[0]?.classId || null
    if (progressClassId.value) await loadProgress()
  } catch {
    progressClasses.value = []
  } finally {
    progressLoading.value = false
  }
}

async function loadProgress() {
  if (!progressCourse.value || !progressClassId.value) return
  progressLoading.value = true
  try {
    const result = await getCourseClassProgress(progressCourse.value.id, progressClassId.value)
    progressStudents.value = result?.students || []
  } catch {
    progressStudents.value = []
  } finally {
    progressLoading.value = false
  }
}

onMounted(loadCourses)
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.course-manage {
  .page-header { align-items: flex-start; }
  .subtitle { margin-top: 6px; color: $text-muted; font-size: 13px; }
  .filter-card { margin-bottom: 18px; border-color: $border; }
  :deep(.filter-card .el-card__body) { padding-bottom: 0; }
  .course-table { @include glass; border-radius: $radius-lg; overflow: hidden; }
  .course-name-cell { display: flex; align-items: center; gap: 12px; }
  .course-thumb { flex: 0 0 64px; width: 64px; height: 44px; border-radius: $radius-sm; overflow: hidden; }
  .thumb-placeholder { display: flex; align-items: center; justify-content: center; background: $primary-soft; color: $primary; font-size: 20px; }
  .muted { color: $text-muted; font-size: 12px; }
  .line-clamp { max-width: 340px; margin-top: 5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .pagination-row { display: flex; justify-content: flex-end; margin-top: 18px; }
  .drawer-content { min-height: 180px; }
  .drawer-intro, .form-hint { color: $text-muted; font-size: 13px; line-height: 1.6; }
  .class-options { display: grid; gap: 10px; margin-top: 16px; }
  .class-option { display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid $border; border-radius: $radius-md; cursor: pointer; }
  .class-option-main { display: grid; gap: 4px; flex: 1; }
  .class-option-main small { color: $text-muted; }
  .progress-filter { display: flex; align-items: center; gap: 12px; margin-bottom: 18px; color: $text-secondary; }
  .username { display: block; margin-top: 3px; }
  :deep(.danger-item) { color: $danger; }
}

@media (max-width: 720px) {
  .course-manage .page-header { gap: 12px; }
  .course-manage .progress-filter { align-items: flex-start; flex-direction: column; }
}
</style>
