<template>
  <div class="error-page">
    <div class="error-card">
      <div class="error-code">404</div>
      <p class="error-desc">抱歉，您访问的页面不存在或已被移除</p>
      <div class="error-actions">
        <el-button type="primary" class="btn-gradient" @click="$router.push('/home')">
          <el-icon style="margin-right:4px"><HomeFilled /></el-icon> 返回首页
        </el-button>
        <el-button @click="$router.back()">返回上一页</el-button>
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
  padding: 24px;
}

.error-card {
  text-align: center;
  background: $surface;
  border: 1px solid $border;
  border-radius: $radius-xl;
  padding: 48px 56px;
  max-width: 90vw;

  .error-code {
    font-size: 56px;
    font-weight: 600;
    letter-spacing: 2px;
    line-height: 1;
    color: $text-primary;
    margin-bottom: 14px;
  }
  .error-desc {
    color: $text-secondary;
    font-size: 14px;
    margin-bottom: 28px;
  }
  .error-actions {
    display: flex;
    justify-content: center;
    gap: 10px;
  }
}
</style>
