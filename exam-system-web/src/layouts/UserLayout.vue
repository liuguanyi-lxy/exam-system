<template>
  <div class="user-layout">
    <!-- 毛玻璃粘性顶栏 -->
    <header class="user-header">
      <div class="header-content">
        <router-link to="/home" class="logo">
          <span class="logo-icon"><el-icon :size="18"><Reading /></el-icon></span>
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
                <span class="user-name">{{ userStore.realName || userStore.username }}</span>
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
            <el-button type="primary" @click="$router.push('/login')">登录</el-button>
          </template>
        </div>
        <!-- 移动端汉堡按钮 -->
        <el-icon class="mobile-menu-btn" :size="22" @click="menuOpen = !menuOpen">
          <Close v-if="menuOpen" />
          <Menu v-else />
        </el-icon>
      </div>
      <!-- 移动端下拉导航 -->
      <transition name="drop">
        <div v-if="menuOpen" class="mobile-menu" @click="menuOpen = false">
          <router-link to="/home" class="mobile-item" :class="{ active: isActive('/home') }">首页</router-link>
          <router-link to="/exam/list" class="mobile-item" :class="{ active: isActive('/exam/list') }">在线考试</router-link>
          <router-link to="/videos" class="mobile-item" :class="{ active: isActive('/videos') }">视频中心</router-link>
          <router-link to="/ranking" class="mobile-item" :class="{ active: isActive('/ranking') }">排行榜</router-link>
          <div class="mobile-divider"></div>
          <router-link to="/exam/history" class="mobile-item">
            <el-icon><Notebook /></el-icon> 我的考试
          </router-link>
          <router-link v-if="userStore.isAdmin" to="/admin/dashboard" class="mobile-item">
            <el-icon><Setting /></el-icon> 管理后台
          </router-link>
          <a v-if="userStore.isLoggedIn" class="mobile-item danger" @click.prevent="handleLogout">
            <el-icon><SwitchButton /></el-icon> 退出登录
          </a>
          <router-link v-else to="/login" class="mobile-item">
            <el-icon><User /></el-icon> 登录 / 注册
          </router-link>
        </div>
      </transition>
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
          <div class="footer-logo">
            <el-icon :size="16"><Reading /></el-icon>
            <span>智能考试系统</span>
          </div>
          <p>在线考试 · 智能组卷 · AI 批阅</p>
        </div>
        <div class="footer-links">
          <span>© 2026 智能考试系统</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menuOpen = ref(false)
const isActive = (path) => route.path.startsWith(path)
const activeMenu = computed(() => route.path)

// 路由变化时收起移动端菜单
watch(() => route.path, () => {
  menuOpen.value = false
})

function handleLogout() {
  menuOpen.value = false
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

// ---- 顶栏 ----
.user-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: $surface;
  border-bottom: 1px solid $border;
  height: 60px;

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
      gap: 8px;
      margin-right: 32px;
      white-space: nowrap;
      text-decoration: none;
      .logo-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        height: 30px;
        border-radius: $radius-md;
        background: $primary;
        color: #fff;
      }
      .logo-text {
        font-size: 15px;
        font-weight: 600;
        color: $text-primary;
        letter-spacing: 0.2px;
      }
    }

    .nav-menu {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 2px;

      .nav-item {
        position: relative;
        padding: 7px 12px;
        border-radius: $radius-sm;
        font-size: 14px;
        color: $text-secondary;
        font-weight: 500;
        transition: all $transition-fast;
        text-decoration: none;

        &:hover {
          color: $text-primary;
          background: $bg-soft;
        }
        &.active {
          color: $primary;
          background: $primary-soft;
          font-weight: 500;
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
        font-size: 14px;
        font-weight: 500;
        color: $text-primary;
        padding: 4px 6px;
        border-radius: $radius-sm;
        transition: background $transition-fast;
        &:hover { background: $bg-soft; }
        .avatar {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 28px;
          height: 28px;
          border-radius: 50%;
          background: $primary;
          color: #fff;
          font-size: 13px;
          font-weight: 600;
        }
      }
    }

    .mobile-menu-btn {
      display: none;
      margin-left: 10px;
      color: $text-secondary;
      cursor: pointer;
      &:hover { color: $primary; }
    }
  }

  // ---- 移动端下拉导航（桌面端隐藏） ----
  .mobile-menu {
    display: none;
  }
}

// ---- 响应式：窄屏收纳导航 ----
@media (max-width: 768px) {
  .user-header {
    .header-content {
      padding: 0 14px;
      .logo {
        margin-right: 0;
        .logo-text { font-size: 15px; }
      }
      .nav-menu { display: none; }
      .header-right {
        .user-name { display: none; } // 只保留头像，省出横向空间
      }
      .mobile-menu-btn { display: flex; align-items: center; }
    }
    .mobile-menu {
      display: block;
      position: absolute;
      top: 60px;
      left: 0;
      right: 0;
      background: $surface;
      border-bottom: 1px solid $border;
      box-shadow: $shadow-lg;
      padding: 8px 12px 12px;

      .mobile-item {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 11px 10px;
        border-radius: $radius-sm;
        font-size: 14px;
        color: $text-primary;
        font-weight: 500;
        text-decoration: none;
        cursor: pointer;
        &:hover { background: $bg-soft; }
        &.active { color: $primary; background: $primary-soft; }
        &.danger { color: $danger; }
      }
      .mobile-divider {
        height: 1px;
        background: $border;
        margin: 6px 4px;
      }
    }
  }
}

// 移动端菜单展开/收起过渡
.drop-enter-active,
.drop-leave-active {
  transition: opacity $transition-fast, transform $transition-fast;
}
.drop-enter-from,
.drop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

// ---- 内容区 ----
.user-main {
  flex: 1;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 24px 20px 56px;
}

// ---- 页脚 ----
.user-footer {
  background: $surface;
  border-top: 1px solid $border;
  margin-top: auto;

  .footer-content {
    max-width: 1200px;
    margin: 0 auto;
    padding: 24px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;

    .footer-brand {
      .footer-logo {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 14px;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: 4px;
      }
      p { font-size: 12px; color: $text-muted; }
    }
    .footer-links {
      font-size: 12px;
      color: $text-muted;
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
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
}
</style>
