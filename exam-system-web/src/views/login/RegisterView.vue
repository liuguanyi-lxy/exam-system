<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <aside class="auth-aside">
      <div class="aside-brand">
        <span class="brand-mark"><el-icon :size="18"><Reading /></el-icon></span>
        <span class="brand-name">智能考试系统</span>
      </div>

      <div class="aside-main">
        <h1>注册一个账号<br />开始你的第一场考试</h1>
        <p class="aside-desc">注册后即可参加已发布的考试、观看学习视频，并随时回看自己的成绩记录。</p>

        <ul class="aside-list">
          <li>
            <span class="li-icon"><el-icon :size="15"><CircleCheck /></el-icon></span>
            <div>
              <b>三步完成注册</b>
              <span>填写用户名与密码即可，无需邮箱验证</span>
            </div>
          </li>
          <li>
            <span class="li-icon"><el-icon :size="15"><VideoCamera /></el-icon></span>
            <div>
              <b>学习与考试打通</b>
              <span>视频中心边看边练，考试检验掌握程度</span>
            </div>
          </li>
          <li>
            <span class="li-icon"><el-icon :size="15"><Medal /></el-icon></span>
            <div>
              <b>记录长期保留</b>
              <span>每次考试的成绩与答卷都可回看</span>
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
          <h2>创建账号</h2>
          <p>填写下面的信息完成注册</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleRegister">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名（3-20 个字符）" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="realName">
            <el-input v-model="form.realName" placeholder="真实姓名（选填）" :prefix-icon="Postcard" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码（6-32 个字符）" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" :prefix-icon="Lock" show-password
              @keyup.enter="handleRegister" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="loading" class="register-btn">
              {{ loading ? '注册中...' : '注册' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="card-foot">
          <span>已有账号？</span>
          <router-link to="/login">去登录</router-link>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Postcard, Reading } from '@element-plus/icons-vue'
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

.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 80px;
  padding: 40px 24px;
  background:
    radial-gradient(900px 460px at 20% -10%, #eef2ff 0%, rgba(238, 242, 255, 0) 62%),
    $bg;
}

// ---- 左侧品牌区 ----
.auth-aside {
  display: flex;
  flex-direction: column;
  width: 440px;
  flex-shrink: 0;
  min-height: 380px;

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
    margin-bottom: 22px;
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

  .register-btn {
    width: 100%;
    height: 40px;
    font-size: 14px;
    font-weight: 500;
  }

  .card-foot {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid $border;
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

// ---- 响应式 ----
@media (max-width: 1100px) {
  .auth-page {
    gap: 44px;
  }
  .auth-aside {
    width: 360px;
    h1 { font-size: 27px; }
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
