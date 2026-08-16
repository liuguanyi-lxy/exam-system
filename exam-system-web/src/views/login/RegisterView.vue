<template>
  <div class="register-page">
    <!-- 背景装饰 -->
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>
    <div class="bg-orb orb-3"></div>
    <div class="bg-grid"></div>

    <div class="register-card">
      <div class="register-header">
        <div class="register-icon">
          <el-icon :size="32"><UserFilled /></el-icon>
        </div>
        <h1>注册账号</h1>
        <p>加入智能考试系统，开启学习之旅</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleRegister">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（3-20个字符）" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名（选填）" :prefix-icon="Postcard" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（6-32个字符）" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" :prefix-icon="Lock" show-password
            @keyup.enter="handleRegister" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" class="btn-gradient register-btn">
            {{ loading ? '注册中...' : '注 册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-footer">
        <span>已有账号？</span>
        <router-link to="/login" class="login-link">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Postcard, UserFilled } from '@element-plus/icons-vue'
import { register } from '@/api/auth'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  realName: '',
  password: '',
  confirmPassword: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度需在3-20个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在6-32个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      realName: form.realName || undefined,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    ElMessage.error(e?.message || '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.register-page {
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 40%, #4f46e5 70%, #7c3aed 100%);
  background-size: 300% 300%;
  animation: gradient-shift 12s ease infinite;
}

// 背景装饰球
.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.5;
  animation: float-orb 14s ease-in-out infinite;
}
.orb-1 {
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(129, 140, 248, 0.7), transparent 70%);
  top: -100px;
  left: -80px;
}
.orb-2 {
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.5), transparent 70%);
  bottom: -80px;
  right: -60px;
  animation-delay: -5s;
}
.orb-3 {
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(196, 181, 253, 0.6), transparent 70%);
  top: 20%;
  right: 20%;
  animation-delay: -9s;
}
.bg-grid {
  position: absolute;
  inset: 0;
  @include dotted-bg(rgba(255, 255, 255, 0.06), 30px);
}

// 注册卡片（玻璃态）
.register-card {
  position: relative;
  z-index: 1;
  width: 440px;
  max-width: 92vw;
  padding: 40px 40px 32px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 24px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.3);
  animation: fade-in-up 0.5s ease-out both;

  .register-header {
    text-align: center;
    margin-bottom: 28px;

    .register-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      border-radius: 18px;
      background: $gradient-primary;
      color: #fff;
      margin-bottom: 16px;
      box-shadow: $shadow-glow;
    }
    h1 {
      font-size: 26px;
      font-weight: 700;
      color: $text-primary;
      margin-bottom: 8px;
      letter-spacing: 1px;
    }
    p {
      color: $text-muted;
      font-size: 14px;
    }
  }

  .register-btn {
    width: 100%;
    height: 44px;
    font-size: 16px;
    letter-spacing: 4px;
    border-radius: 12px;
  }

  .register-footer {
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px dashed $border;
    text-align: center;
    font-size: 14px;
    color: $text-secondary;
    .login-link {
      color: $primary;
      font-weight: 600;
      margin-left: 4px;
      &:hover { text-decoration: underline; }
    }
  }
}

// 深色背景下输入框微调
:deep(.el-input__wrapper) {
  background: rgba(248, 250, 252, 0.9);
  border-radius: 10px;
  &:focus-within {
    box-shadow: 0 0 0 1px $primary-light inset;
  }
}
</style>
