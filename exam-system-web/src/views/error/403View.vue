<template>
  <div class="error-page">
    <!-- 装饰元素 -->
    <div class="float-orb orb-1"></div>
    <div class="float-orb orb-2"></div>

    <div class="error-card">
      <div class="error-visual">
        <el-icon :size="80" class="error-icon"><Lock /></el-icon>
      </div>
      <h1 class="error-code text-gradient">403</h1>
      <p class="error-desc">抱歉，您没有权限访问此页面</p>
      <div class="error-actions">
        <el-button class="btn-gradient" round @click="$router.push('/home')">
          <el-icon style="margin-right:4px"><HomeFilled /></el-icon> 返回首页
        </el-button>
        <el-button round @click="$router.back()">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.error-page {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}
.float-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
  opacity: 0.3;
  animation: float-orb 10s ease-in-out infinite;
}
.orb-1 { width: 260px; height: 260px; background: radial-gradient(circle, rgba(239,68,68,.5), transparent 70%); top: 10%; left: 10%; }
.orb-2 { width: 200px; height: 200px; background: radial-gradient(circle, rgba(99,102,241,.5), transparent 70%); bottom: 15%; right: 15%; animation-delay: -5s; }

.error-card {
  position: relative;
  z-index: 1;
  text-align: center;
  @include glass(20px, 0.85);
  border-radius: 24px;
  padding: 56px 64px;
  max-width: 90vw;
  animation: fade-in-up 0.5s ease-out both;

  .error-visual {
    .error-icon {
      width: 140px;
      height: 140px;
      border-radius: 32px;
      background: linear-gradient(135deg, #fef2f2, #fee2e2);
      color: #ef4444;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 24px;
    }
  }
  .error-code {
    font-size: 72px;
    font-weight: 800;
    letter-spacing: 4px;
    line-height: 1;
    margin-bottom: 16px;
  }
  .error-desc {
    color: $text-secondary;
    font-size: 16px;
    margin-bottom: 32px;
  }
  .error-actions {
    display: flex;
    justify-content: center;
    gap: 12px;
  }
}
</style>
