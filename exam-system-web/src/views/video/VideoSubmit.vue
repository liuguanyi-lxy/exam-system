<template>
  <div class="page-container submit-page" style="max-width:720px;margin:0 auto">
    <div class="page-header">
      <h2>视频投稿</h2>
    </div>

    <el-form :model="form" label-width="80px" class="submit-form">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="请输入视频标题" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="介绍一下视频内容" />
      </el-form-item>
      <el-form-item label="分类" required>
        <el-select v-model="form.categoryId" placeholder="选择分类" style="width:240px">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-input v-model="form.tags" placeholder="多个用逗号分隔，如：Java,教程" />
      </el-form-item>
      <el-form-item label="上传者">
        <el-input v-model="form.uploaderName" :disabled="true" />
      </el-form-item>

      <el-form-item label="视频文件" required>
        <el-upload :show-file-list="false" :before-upload="handleVideoSelect" accept="video/*">
          <div class="upload-zone" :class="{ filled: form.videoFile }">
            <el-icon :size="40" class="zone-icon"><VideoCamera /></el-icon>
            <p class="zone-text">{{ form.videoFile ? form.videoFile.name : '点击选择视频文件' }}</p>
            <p class="zone-sub" v-if="form.videoFile">时长: {{ form.duration ? `${Math.floor(form.duration/60)}:${(form.duration%60).toString().padStart(2,'0')}` : '读取中...' }}</p>
          </div>
        </el-upload>
      </el-form-item>

      <el-form-item label="封面图">
        <el-upload :show-file-list="false" :before-upload="(f) => { form.coverFile = f; return false }" accept="image/*">
          <div class="upload-zone small" :class="{ filled: form.coverFile }">
            <el-icon :size="28" class="zone-icon"><Picture /></el-icon>
            <p class="zone-text">{{ form.coverFile ? form.coverFile.name : '选择封面（可选）' }}</p>
          </div>
        </el-upload>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" class="btn-gradient" round @click="handleSubmit" :loading="submitting" style="width:160px">
          提交投稿
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitVideo } from '@/api/video'
import { getVideoCategories } from '@/api/videoCategory'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const categories = ref([])
const submitting = ref(false)
const form = reactive({ title: '', description: '', categoryId: null, tags: '', uploaderName: '', videoFile: null, coverFile: null, duration: null })

// 选择视频文件时读取时长（后端 submit 接口需要 duration 参数）
function handleVideoSelect(file) {
  form.videoFile = file
  form.duration = null
  const url = URL.createObjectURL(file)
  const el = document.createElement('video')
  el.preload = 'metadata'
  el.src = url
  el.onloadedmetadata = () => {
    form.duration = Math.round(el.duration)
    URL.revokeObjectURL(url)
  }
  return false
}

async function handleSubmit() {
  if (!form.title || !form.categoryId || !form.videoFile) { ElMessage.warning('请填写完整信息'); return }
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('title', form.title)
    if (form.description) fd.append('description', form.description)
    fd.append('categoryId', form.categoryId)
    if (form.tags) fd.append('tags', form.tags)
    fd.append('uploaderName', form.uploaderName || '匿名用户')
    fd.append('duration', form.duration || 60)
    fd.append('videoFile', form.videoFile)
    if (form.coverFile) fd.append('coverFile', form.coverFile)
    await submitVideo(fd)
    ElMessage.success('投稿成功，等待审核')
    router.push('/videos')
  } catch (e) {
    ElMessage.error(e?.message || '投稿失败，请重试')
  } finally { submitting.value = false }
}

onMounted(async () => {
  try { categories.value = await getVideoCategories() || []; } catch {}
  form.uploaderName = userStore.realName || userStore.username || '匿名用户'
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.submit-page {
  @include glass;
}

.upload-zone {
  width: 100%;
  max-width: 480px;
  padding: 28px;
  border: 2px dashed $primary-lighter;
  border-radius: 10px;
  text-align: center;
  cursor: pointer;
  background: rgba(238, 242, 255, 0.4);
  transition: all $transition-fast;
  &:hover {
    border-color: $primary;
    background: rgba(238, 242, 255, 0.7);
  }
  &.filled {
    border-color: $success;
    background: rgba(16, 185, 129, 0.06);
  }
  &.small {
    padding: 16px;
    max-width: 300px;
  }
  .zone-icon { color: $primary; margin-bottom: 8px; }
  .zone-text { font-size: 14px; color: $text-secondary; font-weight: 500; word-break: break-all; }
  .zone-sub { font-size: 12px; color: $success; margin-top: 4px; }
}
</style>
