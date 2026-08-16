<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>
    <div class="bg-orb orb-3"></div>
    <div class="bg-grid"></div>

    <div class="login-card">
      <div class="login-header">
        <div class="login-icon">
          <el-icon :size="32"><Reading /></el-icon>
        </div>
        <h1>智能考试系统</h1>
        <p>基于 AI 的在线考试与智能批阅平台</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password
            @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" class="btn-gradient login-btn">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-accounts">
        <p>演示账号</p>
        <div class="account-tags">
          <span>管理员 <b>admin</b></span>
          <span>教师 <b>teacher1</b></span>
          <span>学生 <b>student1</b></span>
        </div>
        <p class="pwd-hint">密码均为 <b>123456</b></p>
      </div>

      <div class="login-register">
        <span>还没有账号？</span>
        <router-link to="/register" class="register-link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Reading } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    ElMessage.success(`欢迎回来，${userStore.realName || userStore.username}`)
    const redirect = route.query.redirect || (userStore.isAdmin ? '/admin/dashboard' : '/home')
    router.push(redirect)
  } catch {
    ElMessage.error('登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.login-page {
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

// 登录卡片（玻璃态）
.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  max-width: 92vw;
  padding: 44px 40px 36px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 24px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.3);
  animation: fade-in-up 0.5s ease-out both;

  .login-header {
    text-align: center;
    margin-bottom: 32px;

    .login-icon {
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

  .login-btn {
    width: 100%;
    height: 44px;
    font-size: 16px;
    letter-spacing: 4px;
    border-radius: 12px;
  }

  .login-accounts {
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px dashed $border;
    text-align: center;

    p {
      font-size: 12px;
      color: $text-muted;
      margin-bottom: 8px;
    }
    .account-tags {
      display: flex;
      justify-content: center;
      gap: 8px;
      flex-wrap: wrap;
      span {
        padding: 4px 10px;
        border-radius: 8px;
        background: rgba(99, 102, 241, 0.08);
        font-size: 12px;
        color: $text-secondary;
        b {
          color: $primary;
          font-weight: 600;
        }
      }
    }
    .pwd-hint {
      margin-top: 8px;
      margin-bottom: 0;
      b { color: $primary; }
    }
  }

  .login-register {
    margin-top: 16px;
    text-align: center;
    font-size: 14px;
    color: $text-secondary;
    .register-link {
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
