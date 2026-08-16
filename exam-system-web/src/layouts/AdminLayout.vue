<template>
  <el-container class="admin-layout">
    <!-- 侧边栏：深色渐变 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="admin-aside" :class="{ collapsed: isCollapse }">
      <div class="logo">
        <span class="logo-icon"><el-icon :size="22"><Reading /></el-icon></span>
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
              {{ userStore.realName || userStore.username }}
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
import { computed } from 'vue'
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
  box-shadow: 4px 0 24px rgba(30, 27, 75, 0.3);
  z-index: 10;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    color: #fff;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    .logo-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.12);
      backdrop-filter: blur(8px);
      color: #a5b4fc;
      flex-shrink: 0;
    }
    .logo-text {
      font-size: 17px;
      font-weight: 700;
      letter-spacing: 0.5px;
      background: linear-gradient(135deg, #c7d2fe 0%, #ddd6fe 100%);
      -webkit-background-clip: text;
      background-clip: text;
      -webkit-text-fill-color: transparent;
      white-space: nowrap;
    }
  }

  .side-menu {
    background: transparent;
    border-right: none;
    padding: 8px 0;

    // 菜单项样式
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      color: rgba(255, 255, 255, 0.75);
      height: 48px;
      line-height: 48px;
      margin: 2px 8px;
      border-radius: 10px;
      transition: all $transition-fast;
      &:hover {
        background: rgba(255, 255, 255, 0.08);
        color: #fff;
      }
      .el-icon { color: inherit; }
    }

    // 激活状态
    :deep(.el-menu-item.is-active) {
      background: linear-gradient(90deg, rgba(99, 102, 241, 0.35), rgba(124, 58, 237, 0.25));
      color: #fff;
      position: relative;
      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 20%;
        bottom: 20%;
        width: 3px;
        border-radius: 3px;
        background: linear-gradient(180deg, #818cf8, #a78bfa);
      }
    }

    // 子菜单展开背景
    :deep(.el-sub-menu .el-menu) {
      background: rgba(0, 0, 0, 0.15);
      border-radius: 10px;
      margin: 2px 8px;
    }

    // 折叠时菜单项
    &.el-menu--collapse {
      .el-menu-item,
      .el-sub-menu__title {
        margin: 2px 4px;
      }
    }
  }
}

// ---- 顶栏 ----
.admin-header {
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 18px;
    .collapse-btn {
      cursor: pointer;
      color: $text-secondary;
      transition: all $transition-fast;
      &:hover { color: $primary; transform: scale(1.1); }
    }
    :deep(.el-breadcrumb) {
      .el-breadcrumb__inner {
        color: $text-secondary;
        font-weight: 500;
        &.is-link:hover { color: $primary; }
      }
    }
  }

  .header-right {
    .user-info {
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      color: $text-primary;
      font-weight: 500;
      transition: color $transition-fast;
      &:hover { color: $primary; }
      .avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        height: 30px;
        border-radius: 50%;
        background: $gradient-primary;
        color: #fff;
        font-size: 14px;
        font-weight: 600;
      }
    }
  }
}

// ---- 内容区 ----
.admin-main {
  background: transparent;
  padding: 24px;
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
