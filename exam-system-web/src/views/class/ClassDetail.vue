<template>
  <div class="page-container">
    <div v-loading="detailLoading">
    <!-- 加载失败（403 越权 / 班级不存在） -->
    <template v-if="loadError">
      <el-empty description="无权访问该班级或班级不存在">
        <el-button type="primary" @click="$router.push('/admin/classes')">返回班级列表</el-button>
      </el-empty>
    </template>

    <template v-else>
      <div class="page-header">
        <h2>
          <el-icon class="back-btn" @click="$router.push('/admin/classes')"><ArrowLeft /></el-icon>
          {{ detail?.name || '班级详情' }}
          <el-tag v-if="detail" :type="detail.status === 'ENABLED' ? 'success' : 'info'" size="small">
            {{ detail.status === 'ENABLED' ? '启用' : '已停用' }}
          </el-tag>
        </h2>
        <div>
          <el-button v-if="isAdmin" @click="openEdit">编辑信息</el-button>
          <el-button v-if="isAdmin && detail" :type="detail.status === 'ENABLED' ? 'warning' : 'success'" @click="toggleStatus">
            {{ detail.status === 'ENABLED' ? '禁用班级' : '启用班级' }}
          </el-button>
        </div>
      </div>

      <!-- 班级信息 -->
      <el-descriptions v-if="detail" :column="3" border class="info-block">
        <el-descriptions-item label="班级名称">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="培训方向">{{ detail.direction || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.status === 'ENABLED' ? 'success' : 'info'" size="small">
            {{ detail.status === 'ENABLED' ? '启用' : '已停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始日期">{{ detail.startDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束日期">{{ detail.endDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 教师分配 -->
      <div class="section-card">
        <div class="section-head">
          <span class="section-title">班级教师（{{ detail?.teachers?.length || 0 }}）</span>
          <el-button v-if="isAdmin" type="primary" size="small" @click="openAssignTeacher">分配教师</el-button>
        </div>
        <div class="teacher-tags">
          <template v-if="detail?.teachers?.length">
            <el-tag v-for="t in detail.teachers" :key="t.userId" :closable="isAdmin" size="large" @close="handleRemoveTeacher(t)">
              {{ t.realName }}（{{ t.username }}）
            </el-tag>
          </template>
          <span v-else class="muted">暂无教师，点击"分配教师"添加（可批量）</span>
        </div>
      </div>

      <!-- 成员管理 -->
      <div v-if="memberError" class="section-card">
        <el-alert type="error" :closable="false" show-icon title="成员列表加载失败，请重试" />
        <el-button style="margin-top:12px" @click="loadMembers">重试</el-button>
      </div>
      <div class="section-card">
        <div class="section-head">
          <span class="section-title">成员管理（学员 {{ detail?.studentCount ?? '-' }} 人）</span>
          <div v-if="isAdmin">
            <el-button type="primary" size="small" @click="openAddMember">添加学员</el-button>
            <el-button size="small" @click="openImport">Excel 批量导入</el-button>
          </div>
        </div>

        <div class="search-bar">
          <el-select v-model="memberFilters.roleInClass" placeholder="班内角色" clearable @change="searchMembers" style="width:130px">
            <el-option label="全部角色" value="" />
            <el-option label="学员" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
          </el-select>
          <el-input v-model="memberFilters.keyword" placeholder="用户名 / 姓名" clearable
            @clear="searchMembers" @keyup.enter="searchMembers" style="width:200px" />
          <el-button type="primary" @click="searchMembers">搜索</el-button>
        </div>

        <el-table :data="memberData" v-loading="memberLoading">
          <template #empty><el-empty :description="memberFilters.keyword ? '没有符合条件的成员' : '该班级暂无成员'" /></template>
          <el-table-column prop="username" label="用户名" min-width="120" />
          <el-table-column prop="realName" label="姓名" width="110">
            <template #default="{ row }">{{ row.realName || '-' }}</template>
          </el-table-column>
          <el-table-column label="班内角色" width="90">
            <template #default="{ row }">
              <el-tag :type="row.roleInClass === 'TEACHER' ? 'warning' : 'primary'" size="small">
                {{ row.roleInClass === 'TEACHER' ? '教师' : '学员' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="账号状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.accountStatus === 'ACTIVE' ? 'success' : 'danger'" size="small">
                {{ row.accountStatus === 'ACTIVE' ? '正常' : '已禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="joinedAt" label="入班时间" width="160" />
          <el-table-column prop="examCount" label="考试次数" width="90" align="center">
            <template #default="{ row }">{{ row.examCount ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="lastExamTime" label="最近考试" width="160">
            <template #default="{ row }">{{ row.lastExamTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <template v-if="isAdmin && row.roleInClass === 'TEACHER'">
                <el-button size="small" link type="danger" @click="handleRemoveTeacher(row)">移除教师</el-button>
              </template>
              <template v-else-if="isAdmin">
                <el-button size="small" link type="primary" @click="handleResetPassword(row)">重置密码</el-button>
                <el-button size="small" link :type="row.accountStatus === 'ACTIVE' ? 'warning' : 'success'"
                  @click="handleToggleAccount(row)">
                  {{ row.accountStatus === 'ACTIVE' ? '禁用账号' : '启用账号' }}
                </el-button>
                <el-button size="small" link type="danger" @click="handleRemoveMember(row)">移除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top:16px;text-align:right">
          <el-pagination v-model:current-page="memberPage" v-model:page-size="memberSize" :total="memberTotal"
            layout="total,prev,pager,next" @change="loadMembers" />
        </div>
      </div>
    </template>
    </div>

    <!-- 编辑班级信息 -->
    <el-dialog v-model="editVisible" title="编辑班级信息" width="460px">
      <el-form ref="editFormRef" :model="editForm" :rules="rules" label-width="90px">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="editForm.name" maxlength="50" placeholder="全局唯一，不超过 50 字" />
        </el-form-item>
        <el-form-item label="培训方向">
          <el-input v-model="editForm.direction" placeholder="如：Java 后端" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="editForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" clearable style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="editForm.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" clearable style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleEditSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加学员 -->
    <el-dialog v-model="addMemberVisible" title="添加学员" width="420px" :before-close="closeUserIdDialog">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"
        title="请确认账号已存在且角色为学员，再输入用户 ID。需要创建账号时，请用「Excel 批量导入」。" />
      <el-form id="class-add-member-form" label-width="110px" :disabled="saving" @submit.prevent="handleAddMember">
        <el-form-item label="学员用户 ID" :error="addMemberIdError">
          <el-input v-model="addMemberIdText" inputmode="numeric" placeholder="例如：21"
            @input="addMemberIdError = ''" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="addMemberVisible = false">取消</el-button>
        <el-button type="primary" native-type="submit" form="class-add-member-form" :loading="saving">添加</el-button>
      </template>
    </el-dialog>

    <!-- 分配教师 -->
    <el-dialog v-model="assignTeacherVisible" title="分配教师" width="460px" :before-close="closeUserIdDialog">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"
        title="请确认每个账号已存在且角色为教师。已是本班教师的账号会跳过；无效账号会使整批分配失败。" />
      <el-form id="class-assign-teachers-form" label-width="110px" :disabled="saving" @submit.prevent="handleAssignTeacher">
        <el-form-item label="教师用户 ID" :error="teacherIdsError">
          <el-input v-model="teacherIdsText" type="textarea" :rows="3"
            placeholder="多个 ID 用逗号、空格或换行分隔，例如：10, 12" @input="teacherIdsError = ''" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="assignTeacherVisible = false">取消</el-button>
        <el-button type="primary" native-type="submit" form="class-assign-teachers-form" :loading="saving">分配</el-button>
      </template>
    </el-dialog>

    <!-- Excel 批量导入 -->
    <el-dialog v-model="importVisible" title="Excel 批量导入学员" width="640px" :close-on-click-modal="false">
      <div class="import-steps">
        <el-button size="small" :loading="templateLoading" @click="handleDownloadTemplate">
          <el-icon style="margin-right:4px"><Download /></el-icon>下载导入模板
        </el-button>
        <span class="muted">列 A = 用户名（必填），列 B = 姓名（选填），数据从第 2 行起，单次最多 500 行</span>
      </div>

      <el-upload v-if="!importResult" drag :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls"
        :disabled="importing" :on-change="onImportFileChange" style="margin-top:16px">
        <el-icon style="font-size:36px;color:#c0c4cc"><UploadFilled /></el-icon>
        <div>点击选择 Excel 文件（.xlsx / .xls）</div>
      </el-upload>
      <div v-if="importFile && !importResult" class="picked-file">
        已选择：{{ importFile.name }}
        <el-button size="small" link type="primary" @click="handleImport" :loading="importing">开始导入</el-button>
      </div>

      <!-- 导入结果 -->
      <template v-if="importResult">
        <el-alert type="success" show-icon :closable="false" style="margin-top:16px"
          :title="`导入完成：共 ${importResult.totalRows ?? 0} 行，成功 ${importResult.successRows ?? 0} 行（新建账号 ${importResult.createdAccounts ?? 0} 个，实际入班 ${importResult.joinedCount ?? 0} 人）`" />
        <template v-if="importResult.failedRows?.length">
          <el-alert type="error" show-icon :closable="false" style="margin-top:12px"
            :title="`${importResult.failedRows.length} 行失败，可修正后重新导入`" />
          <el-table :data="importResult.failedRows" size="small" max-height="240" style="margin-top:12px">
            <el-table-column prop="row" label="Excel 行号" width="100" />
            <el-table-column prop="username" label="用户名" width="160">
              <template #default="{ row }">{{ row.username || '-' }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="失败原因" min-width="200" />
          </el-table>
        </template>
      </template>

      <template #footer>
        <el-button @click="closeImport">{{ importResult ? '完成' : '取消' }}</el-button>
        <el-button v-if="importResult" type="primary" @click="closeImport">刷新成员列表</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getClassDetail, updateClass, updateClassStatus,
  getClassMembers, addClassMember, importClassMembers, downloadMemberTemplate,
  removeClassMember, assignClassTeachers, removeClassTeacher,
  resetUserPassword, updateUserStatus,
} from '@/api/classManage'

const route = useRoute()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'ADMIN')
const classId = Number(route.params.id)

const loadError = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const saving = ref(false)

// ---- 编辑信息 ----
const editVisible = ref(false)
const editFormRef = ref()
const editForm = reactive({ name: '', direction: '', startDate: null, endDate: null })
const rules = {
  name: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
}

// ---- 成员 ----
const memberLoading = ref(false)
const memberError = ref(false)
const memberData = ref([])
const memberPage = ref(1)
const memberSize = ref(10)
const memberTotal = ref(0)
const memberFilters = reactive({ roleInClass: 'STUDENT', keyword: '' })

// ---- 添加学员 / 分配教师 ----
const addMemberVisible = ref(false)
const addMemberUserId = ref(null)
const assignTeacherVisible = ref(false)
const teacherIdsText = ref('')

// ---- Excel 导入 ----
const importVisible = ref(false)
const importFile = ref(null)
const importing = ref(false)
const templateLoading = ref(false)
const importResult = ref(null)

async function loadDetail() {
  detailLoading.value = true
  try {
    detail.value = await getClassDetail(classId)
    loadError.value = false
  } catch {
    loadError.value = true
  } finally {
    detailLoading.value = false
  }
}

function openEdit() {
  editForm.name = detail.value?.name || ''
  editForm.direction = detail.value?.direction || ''
  editForm.startDate = detail.value?.startDate || null
  editForm.endDate = detail.value?.endDate || null
  editVisible.value = true
}

async function handleEditSave() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (editForm.startDate && editForm.endDate && editForm.startDate > editForm.endDate) {
    ElMessage.warning('结束日期不能早于开始日期')
    return
  }
  saving.value = true
  try {
    detail.value = await updateClass(classId, {
      name: editForm.name,
      direction: editForm.direction,
      startDate: editForm.startDate || null,
      endDate: editForm.endDate || null,
    })
    editVisible.value = false
    ElMessage.success('保存成功')
  } catch {} finally { saving.value = false }
}

async function toggleStatus() {
  const next = detail.value.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await updateClassStatus(classId, next)
  detail.value = { ...detail.value, status: next }
  ElMessage.success(next === 'ENABLED' ? '已启用' : '已停用（学员端仍可见并标记"已停用"）')
}

// ---- 成员 ----
async function loadMembers() {
  memberLoading.value = true
  memberError.value = false
  try {
    const params = {
      page: memberPage.value,
      size: memberSize.value,
      keyword: memberFilters.keyword || undefined,
      includeSummary: true,
    }
    if (memberFilters.roleInClass) params.roleInClass = memberFilters.roleInClass
    const res = await getClassMembers(classId, params)
    memberData.value = res?.records || []
    memberTotal.value = res?.total || 0
  } catch {
    memberError.value = true
    memberData.value = []
    memberTotal.value = 0
  } finally { memberLoading.value = false }
}

function searchMembers() { memberPage.value = 1; loadMembers() }

function openAddMember() {
  addMemberUserId.value = null
  addMemberVisible.value = true
}

async function handleAddMember() {
  if (!addMemberUserId.value) {
    ElMessage.warning('请输入用户 ID')
    return
  }
  saving.value = true
  try {
    const res = await addClassMember(classId, addMemberUserId.value)
    addMemberVisible.value = false
    ElMessage.success(res?.alreadyInClass ? '该用户已在班内，无需重复添加' : '添加成功')
    loadDetail()
    searchMembers()
  } catch {} finally { saving.value = false }
}

function parseIdList(text) {
  const values = String(text).split(/[\s,，、;；]+/).filter(Boolean)
  if (!values.every((value) => /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value)))) {
    return null
  }
  return [...new Set(values.map(Number))]
}

function openAssignTeacher() {
  teacherIdsText.value = ''
  assignTeacherVisible.value = true
}

async function handleAssignTeacher() {
  const userIds = parseIdList(teacherIdsText.value)
  if (userIds === null) {
    ElMessage.warning('教师用户 ID 必须是有效的正整数')
    return
  }
  if (!userIds.length) {
    ElMessage.warning('请输入至少一个教师用户 ID')
    return
  }
  saving.value = true
  try {
    const res = await assignClassTeachers(classId, userIds)
    assignTeacherVisible.value = false
    ElMessage.success(`分配成功，新增 ${res?.addedCount ?? 0} 名教师`)
    loadDetail()
    searchMembers()
  } catch {} finally { saving.value = false }
}

async function handleRemoveTeacher(t) {
  await ElMessageBox.confirm(`确认移除教师"${t.realName || t.username || t.userId}"？`, '移除教师', { type: 'warning' })
  await removeClassTeacher(classId, t.userId)
  ElMessage.success('已移除')
  loadDetail()
  searchMembers()
}

async function handleRemoveMember(row) {
  await ElMessageBox.confirm(`确认将学员"${row.realName || row.username}"移出班级？`, '移除学员', { type: 'warning' })
  await removeClassMember(classId, row.userId)
  ElMessage.success('已移除')
  loadDetail()
  loadMembers()
}

async function handleResetPassword(row) {
  await ElMessageBox.confirm(
    `确认将"${row.realName || row.username}"的密码重置为默认值？`,
    '重置密码',
    { type: 'warning' },
  )
  const res = await resetUserPassword(row.userId)
  ElMessage.success(`密码已重置为 ${res?.defaultPassword || '123456'}`)
}

async function handleToggleAccount(row) {
  const enable = row.accountStatus !== 'ACTIVE'
  await ElMessageBox.confirm(
    enable ? `确认启用账号"${row.realName || row.username}"？` : `确认禁用账号"${row.realName || row.username}"？禁用后该账号无法登录。`,
    enable ? '启用账号' : '禁用账号',
    { type: 'warning' },
  )
  await updateUserStatus(row.userId, enable)
  ElMessage.success(enable ? '已启用' : '已禁用')
  loadMembers()
}

// ---- Excel 导入 ----
function openImport() {
  importFile.value = null
  importResult.value = null
  importVisible.value = true
}

function onImportFileChange(uploadFile) {
  const file = uploadFile?.raw
  importFile.value = null
  if (!file) return
  if (!/\.xlsx?$/i.test(file.name)) {
    ElMessage.warning('请上传 .xlsx 或 .xls 文件')
    return
  }
  importFile.value = file
}

async function handleDownloadTemplate() {
  templateLoading.value = true
  try { await downloadMemberTemplate() } catch {} finally { templateLoading.value = false }
}

async function handleImport() {
  if (!importFile.value || importing.value) return
  importing.value = true
  try {
    importResult.value = await importClassMembers(classId, importFile.value)
    ElMessage.success('导入完成')
  } catch {} finally { importing.value = false }
}

function closeImport() {
  const hadResult = !!importResult.value
  importVisible.value = false
  if (hadResult) {
    loadDetail()
    searchMembers()
  }
}

onMounted(() => {
  loadDetail()
  loadMembers()
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
  .section-title {
    font-size: 14.5px;
    font-weight: 600;
    color: $text-primary;
  }
}

.teacher-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $space-2;
  align-items: center;
}

.search-bar {
  margin-bottom: $space-3;
}

.muted { color: $text-muted; font-size: 13px; }

.dialog-tip {
  font-size: 12.5px;
  color: $text-muted;
  margin: 0 0 0 90px;
}

.import-steps {
  display: flex;
  align-items: center;
  gap: $space-3;
  flex-wrap: wrap;
}

.picked-file {
  margin-top: $space-3;
  display: flex;
  align-items: center;
  gap: $space-3;
  font-size: 13px;
  color: $text-secondary;
}
</style>
