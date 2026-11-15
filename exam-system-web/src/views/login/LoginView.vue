<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <aside class="auth-aside">
      <div class="aside-brand">
        <span class="brand-mark"><el-icon :size="18"><Reading /></el-icon></span>
        <span class="brand-name">智能考试系统</span>
      </div>

      <div class="aside-main">
        <h1>学习、考试、成绩<br />都在一个地方</h1>
        <p class="aside-desc">从题库到组卷，从在线作答到 AI 批阅，教师和学习者用同一套流程完成。</p>

        <ul class="aside-list">
          <li>
            <span class="li-icon"><el-icon :size="15"><DocumentChecked /></el-icon></span>
            <div>
              <b>在线考试</b>
              <span>限时作答、答题卡导航、自动交卷</span>
            </div>
          </li>
          <li>
            <span class="li-icon"><el-icon :size="15"><MagicStick /></el-icon></span>
            <div>
              <b>AI 组卷与批阅</b>
              <span>按知识点智能组卷，简答题自动评分</span>
            </div>
          </li>
          <li>
            <span class="li-icon"><el-icon :size="15"><TrendCharts /></el-icon></span>
            <div>
              <b>成绩与排行</b>
              <span>考试记录可回看，成绩排名实时更新</span>
            </div>
          </li>
        </ul>
      </div>

      <p class="aside-foot">© 2026 智能考试系统</p>
    </aside>

    <!-- 右侧表单 -->
    <main class="auth-main">
      <div class="auth-card">
        <div class="card-head">
          <h2>登录账号</h2>
          <p>使用你的用户名和密码继续</p>
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
            <el-button type="primary" native-type="submit" :loading="loading" class="login-btn">
              {{ loading ? '登录中...' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="demo-block">
          <div class="demo-title">演示账号</div>
          <div class="demo-tags">
            <span>管理员 <b>admin</b></span>
            <span>教师 <b>teacher1</b></span>
            <span>学生 <b>student1</b></span>
          </div>
          <p class="demo-hint">密码均为 <b>123456</b></p>
        </div>

        <div class="card-foot">
          <span>还没有账号？</span>
          <router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </main>
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

.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 88px;
  padding: 40px 24px;
  background:
    radial-gradient(900px 460px at 20% -10%, #eef2ff 0%, rgba(238, 242, 255, 0) 62%),
    $bg;
}

// ---- 左侧品牌区 ----
.auth-aside {
  display: flex;
  flex-direction: column;
  width: 460px;
  flex-shrink: 0;
  min-height: 400px;

  .aside-brand {
    display: flex;
    align-items: center;
    gap: 9px;
    margin-bottom: auto;
    .brand-mark {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 30px;
      height: 30px;
      border-radius: $radius-md;
      background: $primary;
      color: #fff;
    }
    .brand-name {
      font-size: 15px;
      font-weight: 600;
      color: $text-primary;
    }
  }

  .aside-main {
    margin: auto 0;
    padding: 28px 0;

    h1 {
      font-size: 34px;
      line-height: 1.35;
      font-weight: 600;
      letter-spacing: -0.6px;
      color: $text-primary;
      margin-bottom: 14px;
    }
    .aside-desc {
      font-size: 14px;
      line-height: 1.75;
      color: $text-secondary;
      max-width: 400px;
      margin-bottom: 32px;
    }
    .aside-list {
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 16px;

      li {
        display: flex;
        gap: 12px;
        align-items: flex-start;

        .li-icon {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 28px;
          height: 28px;
          border-radius: $radius-sm;
          background: $surface;
          border: 1px solid $border;
          color: $primary;
          flex-shrink: 0;
        }
        div {
          display: flex;
          flex-direction: column;
          gap: 2px;
          b {
            font-size: 13.5px;
            font-weight: 500;
            color: $text-primary;
          }
          span {
            font-size: 12.5px;
            color: $text-muted;
          }
        }
      }
    }
  }

  .aside-foot {
    font-size: 12px;
    color: $text-muted;
  }
}

// ---- 右侧表单 ----
.auth-main {
  width: 400px;
  flex-shrink: 0;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  background: $surface;
  border: 1px solid $border;
  border-radius: $radius-xl;
  box-shadow: $shadow-md;
  padding: 32px;

  .card-head {
    margin-bottom: 24px;
    h2 {
      font-size: 20px;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 6px;
    }
    p {
      font-size: 13px;
      color: $text-muted;
    }
  }

  .login-btn {
    width: 100%;
    height: 40px;
    font-size: 14px;
    font-weight: 500;
  }

  .demo-block {
    margin-top: 8px;
    padding: 14px 16px;
    background: $bg-soft;
    border: 1px solid $border;
    border-radius: $radius-md;

    .demo-title {
      font-size: 12px;
      color: $text-muted;
      margin-bottom: 8px;
    }
    .demo-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      span {
        padding: 3px 8px;
        border-radius: $radius-sm;
        background: $surface;
        border: 1px solid $border;
        font-size: 12px;
        color: $text-secondary;
        b {
          color: $text-primary;
          font-weight: 600;
          margin-left: 2px;
        }
      }
    }
    .demo-hint {
      margin-top: 8px;
      font-size: 12px;
      color: $text-muted;
      b { color: $text-secondary; }
    }
  }

  .card-foot {
    margin-top: 18px;
    text-align: center;
    font-size: 13px;
    color: $text-muted;
    a {
      color: $primary;
      font-weight: 500;
      margin-left: 4px;
      &:hover { text-decoration: underline; }
    }
  }
}

// ---- 响应式：窄屏隐藏品牌区，表单居中 ----
@media (max-width: 1100px) {
  .auth-page {
    gap: 48px;
  }
  .auth-aside {
    width: 380px;
    h1 { font-size: 28px; }
  }
}

@media (max-width: 860px) {
  .auth-page {
    padding: 24px 18px;
  }
  .auth-aside {
    display: none;
  }
  .auth-main {
    width: 100%;
    max-width: 400px;
  }
  .auth-card {
    padding: 24px 20px;
    box-shadow: none;
  }
}
</style>
