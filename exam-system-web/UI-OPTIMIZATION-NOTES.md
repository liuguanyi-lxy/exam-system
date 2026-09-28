# 前端页面/布局优化 — 进行中交接文档

> 2026-09 由 ZCode 评审时记录。后端重构已完成并提交（exam-system-server 242d6dc）。
> 本文档记录已用浏览器实测发现的 UI/布局问题与后续优化计划，下次继续开发从这里接手。

## 已实测确认的问题

1. **错误提示无去重（最影响观感）**
   - 位置：`src/utils/request.js` 响应拦截器。
   - 现象：后端不可用时首页 6 个并发请求弹出 6 个 "Request failed with status code 500"，堆叠盖住顶部导航。
   - 修法：相同 message 在短时间窗口内只弹一次；优先展示后端返回的 `error.response.data.message`，没有才用兜底文案（如"网络异常，请稍后重试"）。

2. **UserLayout 窄屏无响应式**
   - 位置：`src/layouts/UserLayout.vue`。
   - 现象：768px 下 logo + 4 个导航项 + 用户区挤在一行，开始重叠。
   - 修法：加媒体查询（≤768px 隐藏 `.logo-text`、缩小 nav 间距），并增加汉堡按钮 + 下拉/抽屉菜单收纳导航。

3. **首页无 banner/公告时首屏偏平**
   - 位置：`src/views/home/HomeView.vue`（banner 与公告都是 `v-if`，空时页面直接从"在线考试"卡片开始）。
   - 修法：加一个默认 hero 欢迎区（渐变背景 + 欢迎语 + 快捷入口按钮），banner 有数据时仍优先轮播。

4. **构建产物过大**
   - `npm run build` 警告：主 chunk 1.25MB（element-plus 全量 + echarts 打进主包）。
   - 修法：`vite.config.js` 配 `build.rollupOptions.output.manualChunks`，把 `element-plus`、`echarts` 拆成独立 chunk。

## 待继续评审（尚未看完）

- `AdminLayout.vue`（315 行，含侧边栏折叠逻辑）细节与移动端表现
- `ExamTaking.vue` / `ExamResult.vue` 答题页视觉（需要启动后端 + 真实考试数据才能进入）
- 管理端各编辑页（QuestionEdit、PaperCreate、PaperAiCreate、VideoManage）
- 表格在窄屏的横向溢出处理（el-table 需要外层滚动容器或 `fit` 配置）

## 已确认的优点（不需要动）

- 设计令牌体系完整：`variables.scss`（靛蓝/紫渐变主题、间距、圆角、阴影、EP 变量映射）
- 登录页玻璃态卡片效果不错；管理端仪表盘统计卡 + 图表区布局成型；题库管理筛选栏规范
- 空状态统一用 el-empty；页面切换过渡 `page` 已配置

## 复现/验证方式

```bash
cd E:\java_code\exam-system-web
npm run dev   # http://localhost:5173
# 后端在 exam-system-server（mvn spring-boot:run），不开后端可看布局但接口报错
```
