<template>
  <el-container class="admin-layout">
    <!-- 侧边栏：深色渐变 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="admin-aside" :class="{ collapsed: isCollapse }">
      <div class="logo">
        <span class="logo-icon"><el-icon :size="17"><Reading /></el-icon></span>
        <span v-if="!isCollapse" class="logo-text">智能考试系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        class="side-menu"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-sub-menu index="question-group">
          <template #title>
            <el-icon><List /></el-icon>
            <span>题库管理</span>
          </template>
          <el-menu-item index="/admin/questions">题目列表</el-menu-item>
          <el-menu-item index="/admin/questions/create">新增题目</el-menu-item>
          <el-menu-item index="/admin/questions/batch">批量导入</el-menu-item>
          <el-menu-item index="/admin/categories">题目分类</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="paper-group">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>试卷管理</span>
          </template>
          <el-menu-item index="/admin/papers">试卷列表</el-menu-item>
          <el-menu-item index="/admin/papers/create">手动组卷</el-menu-item>
          <el-menu-item index="/admin/papers/ai-create">AI组卷</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="class-group">
          <template #title>
            <el-icon><School /></el-icon>
            <span>班级管理</span>
          </template>
          <el-menu-item index="/admin/classes">班级列表</el-menu-item>
          <el-menu-item index="/admin/knowledge-points">知识点</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/admin/courses">
          <el-icon><Reading /></el-icon>
          <span>课程管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/exam-records">
          <el-icon><DataAnalysis /></el-icon>
          <span>考试记录</span>
        </el-menu-item>
        <el-sub-menu index="content-group">
          <template #title>
            <el-icon><PictureFilled /></el-icon>
            <span>内容管理</span>
          </template>
          <el-menu-item index="/admin/banners">轮播图</el-menu-item>
          <el-menu-item index="/admin/notices">公告</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="video-group">
          <template #title>
            <el-icon><VideoCameraFilled /></el-icon>
            <span>视频管理</span>
          </template>
          <el-menu-item index="/admin/videos">视频列表</el-menu-item>
          <el-menu-item index="/admin/video-categories">视频分类</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏：玻璃态 -->
      <el-header class="admin-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleSidebar" :size="22">
            <Fold v-if="!isCollapse" /><Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="user-info">
              <span class="avatar">{{ (userStore.realName || userStore.username || 'U').charAt(0) }}</span>
              <span class="user-name">{{ userStore.realName || userStore.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/home')">
                  <el-icon><HomeFilled /></el-icon> 前台首页
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="admin-main">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const isCollapse = computed(() => appStore.sidebarCollapsed)
const activeMenu = computed(() => route.path)
const breadcrumbs = computed(() => {
  const matched = route.matched.filter((r) => r.meta.title)
  return matched.map((r) => ({ path: r.path, title: r.meta.title }))
})

function toggleSidebar() {
  appStore.toggleSidebar()
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

// 窄屏自动收起侧边栏；宽屏不强制展开，保留用户手动切换的状态
function handleResize() {
  if (window.innerWidth < 768 && !appStore.sidebarCollapsed) {
    appStore.sidebarCollapsed = true
  }
}
onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.admin-layout {
  min-height: 100vh;
}

// ---- 侧边栏 ----
.admin-aside {
  background: $gradient-sidebar;
  overflow: hidden;
  transition: width $transition-normal;
  z-index: 10;

  .logo {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 9px;
    padding: 0 16px;
    color: #fff;
    border-bottom: 1px solid rgba(255, 255, 255, 0.07);
    .logo-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: $radius-sm;
      background: $primary;
      color: #fff;
      flex-shrink: 0;
    }
    .logo-text {
      font-size: 14px;
      font-weight: 600;
      color: #fff;
      letter-spacing: 0.2px;
      white-space: nowrap;
    }
  }

  .side-menu {
    background: transparent;
    border-right: none;
    padding: 10px 0;

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      color: #b9c0cf;
      height: 40px;
      line-height: 40px;
      margin: 2px 8px;
      border-radius: $radius-sm;
      font-size: 13.5px;
      transition: background $transition-fast, color $transition-fast;
      &:hover {
        background: rgba(255, 255, 255, 0.06);
        color: #fff;
      }
      .el-icon {
        color: inherit;
        font-size: 16px;
      }
    }

    // 激活状态
    :deep(.el-menu-item.is-active) {
      background: rgba(255, 255, 255, 0.09);
      color: #fff;
      position: relative;
      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 2px;
        height: 16px;
        border-radius: 2px;
        background: $primary-lighter;
      }
    }

    // 子菜单展开区
    :deep(.el-sub-menu .el-menu) {
      background: transparent;
      margin: 0;
      padding: 0;
    }
    :deep(.el-sub-menu .el-menu-item) {
      min-width: auto;
      padding-left: 44px !important;
      font-size: 13px;
      color: #9aa3b5;
    }

    // 折叠时
    &.el-menu--collapse {
      .el-menu-item,
      .el-sub-menu__title {
        margin: 2px 8px;
        padding: 0 !important;
        justify-content: center;
      }
    }
  }
}

// ---- 顶栏 ----
.admin-header {
  background: $surface;
  border-bottom: 1px solid $border;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 56px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
    .collapse-btn {
      cursor: pointer;
      color: $text-secondary;
      transition: color $transition-fast;
      &:hover { color: $primary; }
    }
    :deep(.el-breadcrumb) {
      font-size: 13px;
      .el-breadcrumb__inner {
        color: $text-muted;
        font-weight: 400;
        &:hover { color: $text-primary; }
      }
      .el-breadcrumb__item:last-child .el-breadcrumb__inner {
        color: $text-primary;
        font-weight: 500;
      }
      .el-breadcrumb__separator { color: $border-strong; }
    }
  }

  .header-right {
    .user-info {
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13.5px;
      color: $text-primary;
      padding: 4px 6px;
      border-radius: $radius-sm;
      transition: background $transition-fast;
      &:hover { background: $bg-soft; }
      .avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 26px;
        height: 26px;
        border-radius: 50%;
        background: $primary;
        color: #fff;
        font-size: 12px;
        font-weight: 600;
      }
    }
  }
}

// ---- 响应式：手机上隐藏面包屑，用户名只留头像 ----
@media (max-width: 560px) {
  .admin-header {
    padding: 0 14px;
    .header-left {
      gap: 10px;
      :deep(.el-breadcrumb) { display: none; }
    }
    .header-right .user-info {
      gap: 4px;
      .user-name { display: none; } // 手机上只留头像和箭头
    }
  }
}

// ---- 内容区 ----
.admin-main {
  background: $bg;
  padding: 20px;
  overflow-x: hidden;
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
