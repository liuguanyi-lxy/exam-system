<template>
  <div class="blank-layout">
    <!-- 漂浮装饰球 -->
    <div class="orb orb-1"></div>
    <div class="orb orb-2"></div>
    <div class="orb orb-3"></div>
    <!-- 网格点阵背景 -->
    <div class="dot-grid"></div>

    <div class="layout-content">
      <router-view />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@import '@/assets/styles/variables';
@import '@/assets/styles/mixins';

.blank-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #eef2ff 0%, #faf5ff 50%, #ecfeff 100%);
  background-size: 300% 300%;
  animation: gradient-shift 15s ease infinite;
}

// 漂浮装饰球
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
  opacity: 0.4;
  animation: float-orb 12s ease-in-out infinite;
}
.orb-1 {
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.5), transparent 70%);
  top: -80px;
  left: -60px;
}
.orb-2 {
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(124, 58, 237, 0.45), transparent 70%);
  bottom: -60px;
  right: -40px;
  animation-delay: -4s;
}
.orb-3 {
  width: 180px;
  height: 180px;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.4), transparent 70%);
  top: 60%;
  left: 12%;
  animation-delay: -8s;
}

// 网格点阵
.dot-grid {
  position: absolute;
  inset: 0;
  @include dotted-bg(rgba(99, 102, 241, 0.08), 28px);
  pointer-events: none;
}

.layout-content {
  position: relative;
  z-index: 1;
  width: 100%;
}
</style>
