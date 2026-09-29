# 前端页面/布局优化 — 进行中交接文档

> 2026-09 由 ZCode 评审与实施。后端重构已完成并提交（exam-system-server 242d6dc）。
> **本轮已完成第一批优化并提交**，剩余项见文末，下次从这里继续。

## 已完成（本批提交）

1. **request.js 错误提示去重 + 文案优化**
   - 相同错误文案 2 秒窗口内只弹一个 toast（原先首页 6 个并发失败弹 6 个盖住导航）。
   - 优先展示后端返回的 `error.response.data.message`；无响应时按状态码映射中文文案（500→"服务器开小差了"、403/404 等）。

2. **UserLayout 移动端响应式（≤768px）**
   - 新增汉堡按钮 + 下拉导航面板（含我的考试/管理后台/退出登录），路由跳转自动收起。
   - 隐藏用户名文字只留头像；已浏览器实测 390px 宽度正常。

3. **首页默认 hero 欢迎区**
   - 无轮播图数据时显示渐变 hero（标题 + 功能简介 + "开始考试"/"查看排行榜"按钮 + 装饰光斑），有 banner 时仍优先轮播。
   - 768px 以下按钮纵向堆叠全宽。

4. **构建分包 + echarts 按需引入**
   - `vite.config.js` manualChunks：主包 index.js 从 **1253KB → 16KB**；vendor(vue/pinia/axios) 160KB；element-plus、echarts 独立 chunk。
   - DashboardView 改用 `echarts/core` 按需注册（只用饼图+柱状图）：echarts chunk **1036KB → 492KB**。

5. **AdminLayout 移动端适配**
   - <768px 自动收起侧边栏为图标模式；<560px 隐藏面包屑、用户名只留头像。
   - 设计决策：宽屏不强制展开侧边栏（保留用户手动状态）；管理端抽屉式侧边栏列为后续项。

## 剩余待办（下次继续）

1. **element-plus 按需引入**（chunk 仍有 1MB）：需要引入 `unplugin-auto-import` + `unplugin-vue-components`，改 main.js 的全量注册，回归测试所有页面组件。
2. **管理端抽屉式侧边栏**：手机上 64px 图标栏仍占宽度，理想是遮罩抽屉。
3. **答题页/结果页视觉评审**：需要启动后端 + 创建真实试卷和考试记录才能进入（`/exam/taking/:id`、`/exam/result/:id`）。
4. **管理端编辑页评审**：QuestionEdit、PaperCreate、PaperAiCreate、VideoManage、QuestionBatch 截图检查。
5. 表格窄屏横向滚动确认（el-table 自带滚动，未实测）。
6. `chunkSizeWarningLimit` 可按需调到 1200 消除构建警告（element-plus/echarts 已是独立可缓存 chunk，警告无害）。

## 已确认的优点（不需要动）

- 设计令牌体系完整：`variables.scss`（靛蓝/紫渐变主题、间距、圆角、阴影、EP 变量映射）
- 登录页玻璃态卡片；管理端仪表盘统计卡 + 图表区；题库管理筛选栏
- 空状态统一 el-empty；页面切换过渡已配置

## 复现/验证方式

```bash
cd E:\java_code\exam-system-web
npm run dev   # http://localhost:5173
# 后端在 exam-system-server（mvn spring-boot:run），不开后端可看布局但接口报错
# 移动端验证：浏览器 DevTools 设备模拟，或窗口缩到 768px 以下看汉堡菜单
```
