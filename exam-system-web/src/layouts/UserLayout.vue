<template>
  <div class="user-layout">
    <!-- 毛玻璃粘性顶栏 -->
    <header class="user-header">
      <div class="header-content">
        <router-link to="/home" class="logo">
          <span class="logo-icon"><el-icon :size="22"><Reading /></el-icon></span>
          <span class="logo-text">智能考试系统</span>
        </router-link>
        <nav class="nav-menu">
          <router-link to="/home" class="nav-item" :class="{ active: isActive('/home') }">首页</router-link>
          <router-link to="/exam/list" class="nav-item" :class="{ active: isActive('/exam/list') }">在线考试</router-link>
          <router-link to="/videos" class="nav-item" :class="{ active: isActive('/videos') }">视频中心</router-link>
          <router-link to="/ranking" class="nav-item" :class="{ active: isActive('/ranking') }">排行榜</router-link>
        </nav>
        <div class="header-right">
          <template v-if="userStore.isLoggedIn">
            <el-dropdown trigger="click">
              <span class="user-info">
                <span class="avatar">{{ (userStore.realName || userStore.username || 'U').charAt(0) }}</span>
                {{ userStore.realName || userStore.username }}
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="$router.push('/exam/history')">
                    <el-icon><Notebook /></el-icon> 我的考试
                  </el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" @click="$router.push('/admin/dashboard')">
                    <el-icon><Setting /></el-icon> 管理后台
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">
                    <el-icon><SwitchButton /></el-icon> 退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button type="primary" round @click="$router.push('/login')">登录</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="user-main">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <footer class="user-footer">
      <div class="footer-content">
        <div class="footer-brand">
          <div class="footer-logo">📚 智能考试系统</div>
          <p>基于 AI 的智能考试平台，在线考试 · 智能组卷 · AI 批阅</p>
        </div>
        <div class="footer-links">
          <span>© 2026 智能考试系统. All Rights Reserved.</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isActive = (path) => route.path.startsWith(path)
const activeMenu = computed(() => route.path)

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

// ---- 毛玻璃粘性顶栏 ----
.user-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.5);
  height: 64px;

  .header-content {
    max-width: 1200px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    height: 100%;
    padding: 0 20px;

    .logo {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-right: 40px;
      white-space: nowrap;
      text-decoration: none;
      .logo-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 38px;
        height: 38px;
        border-radius: 11px;
        background: $gradient-primary;
        color: #fff;
        box-shadow: $shadow-glow;
      }
      .logo-text {
        font-size: 19px;
        font-weight: 700;
        @include gradient-text;
      }
    }

    .nav-menu {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 8px;

      .nav-item {
        position: relative;
        padding: 8px 16px;
        border-radius: 10px;
        font-size: 15px;
        color: $text-secondary;
        font-weight: 500;
        transition: all $transition-fast;
        text-decoration: none;

        &:hover {
          color: $primary;
          background: rgba(99, 102, 241, 0.08);
        }
        &.active {
          color: $primary;
          font-weight: 600;
          &::after {
            content: '';
            position: absolute;
            bottom: 2px;
            left: 50%;
            transform: translateX(-50%);
            width: 20px;
            height: 3px;
            border-radius: 3px;
            background: $gradient-primary;
          }
        }
      }
    }

    .header-right {
      margin-left: auto;
      .user-info {
        cursor: pointer;
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 500;
        color: $text-primary;
        transition: color $transition-fast;
        &:hover { color: $primary; }
        .avatar {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 32px;
          height: 32px;
          border-radius: 50%;
          background: $gradient-primary;
          color: #fff;
          font-size: 14px;
          font-weight: 600;
        }
      }
    }
  }
}

// ---- 内容区 ----
.user-main {
  flex: 1;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

// ---- 深色页脚 ----
.user-footer {
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 100%);
  color: rgba(255, 255, 255, 0.6);
  margin-top: auto;

  .footer-content {
    max-width: 1200px;
    margin: 0 auto;
    padding: 32px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 20px;
    flex-wrap: wrap;

    .footer-brand {
      .footer-logo {
        font-size: 17px;
        font-weight: 700;
        color: #fff;
        margin-bottom: 6px;
      }
      p { font-size: 13px; }
    }
    .footer-links {
      font-size: 13px;
    }
  }
}

// ---- 页面过渡 ----
.page-enter-active,
.page-leave-active {
  transition: opacity $transition-normal, transform $transition-normal;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
