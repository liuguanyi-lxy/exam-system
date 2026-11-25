import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'


const routes = [
  // ========== 公共路由 ==========
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/RegisterView.vue'),
    meta: { title: '注册', requiresAuth: false },
  },

  // ========== 用户端路由 ==========
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'videos',
        name: 'VideoList',
        component: () => import('@/views/video/VideoList.vue'),
        meta: { title: '视频中心' },
      },
      {
        path: 'videos/submit',
        name: 'VideoSubmit',
        component: () => import('@/views/video/VideoSubmit.vue'),
        meta: { title: '视频投稿', requiresAuth: true },
      },
      {
        path: 'videos/:id',
        name: 'VideoDetail',
        component: () => import('@/views/video/VideoDetail.vue'),
        meta: { title: '视频详情' },
      },
      {
        path: 'exam/list',
        name: 'ExamList',
        component: () => import('@/views/exam/ExamList.vue'),
        meta: { title: '在线考试', requiresAuth: true },
      },
      {
        path: 'exam/start/:paperId',
        name: 'ExamStart',
        component: () => import('@/views/exam/ExamStart.vue'),
        meta: { title: '开始考试', requiresAuth: true },
      },
      {
        path: 'exam/taking/:recordId',
        name: 'ExamTaking',
        component: () => import('@/views/exam/ExamTaking.vue'),
        meta: { title: '考试中', requiresAuth: true },
      },
      {
        path: 'exam/result/:recordId',
        name: 'ExamResult',
        component: () => import('@/views/exam/ExamResult.vue'),
        meta: { title: '考试结果', requiresAuth: true },
      },
      {
        path: 'exam/history',
        name: 'ExamHistory',
        component: () => import('@/views/exam/ExamHistory.vue'),
        meta: { title: '我的考试', requiresAuth: true },
      },
      {
        path: 'wrong-questions',
        name: 'WrongQuestionBook',
        component: () => import('@/views/wrong-question/WrongQuestionBook.vue'),
        meta: { title: '错题本', requiresAuth: true, roles: ['STUDENT'] },
      },
      {
        path: 'favorites',
        name: 'MyFavorites',
        component: () => import('@/views/favorite/MyFavorites.vue'),
        meta: { title: '我的收藏', requiresAuth: true, roles: ['STUDENT'] },
      },
      {
        path: 'questions/:id',
        name: 'QuestionDetail',
        component: () => import('@/views/question/QuestionDetail.vue'),
        meta: { title: '题目详情', requiresAuth: true },
      },
      {
        path: 'ranking',
        name: 'ExamRanking',
        component: () => import('@/views/exam-record/ExamRanking.vue'),
        meta: { title: '成绩排行榜' },
      },
      {
        path: 'my/classes',
        name: 'MyClasses',
        component: () => import('@/views/my/MyClasses.vue'),
        meta: { title: '我的班级', requiresAuth: true, roles: ['STUDENT'] },
      },
      {
        path: 'my/classes/:id',
        name: 'MyClassDetail',
        component: () => import('@/views/my/MyClassDetail.vue'),
        meta: { title: '班级详情', requiresAuth: true, roles: ['STUDENT'] },
      },
      {
        path: 'my/courses',
        name: 'MyCourses',
        component: () => import('@/views/course/MyCourses.vue'),
        meta: { title: '我的课程', requiresAuth: true, roles: ['STUDENT'] },
      },
      {
        path: 'my/courses/:id',
        name: 'CourseLearning',
        component: () => import('@/views/course/CourseLearning.vue'),
        meta: { title: '课程学习', requiresAuth: true, roles: ['STUDENT'] },
      },
      {
        path: 'remediation',
        name: 'RemediationCenter',
        component: () => import('@/views/remediation/RemediationCenter.vue'),
        meta: { title: '补课中心', requiresAuth: true, roles: ['STUDENT'] },
      },
      {
        path: 'remediation/tasks/:id',
        name: 'RemediationTaskDetail',
        component: () => import('@/views/remediation/RemediationTaskDetail.vue'),
        meta: { title: '补课任务详情', requiresAuth: true, roles: ['STUDENT'] },
      },
    ],
  },

  // ========== 管理端路由 ==========
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { requiresAuth: true, roles: ['ADMIN', 'TEACHER'] },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'questions',
        name: 'QuestionList',
        component: () => import('@/views/question/QuestionList.vue'),
        meta: { title: '题目管理' },
      },
      {
        path: 'questions/create',
        name: 'QuestionCreate',
        component: () => import('@/views/question/QuestionEdit.vue'),
        meta: { title: '新增题目' },
      },
      {
        path: 'questions/edit/:id',
        name: 'QuestionEdit',
        component: () => import('@/views/question/QuestionEdit.vue'),
        meta: { title: '编辑题目' },
      },
      {
        path: 'questions/batch',
        name: 'QuestionBatch',
        component: () => import('@/views/question/QuestionBatch.vue'),
        meta: { title: '批量导入' },
      },
      {
        path: 'categories',
        name: 'CategoryManage',
        component: () => import('@/views/category/CategoryManage.vue'),
        meta: { title: '题目分类' },
      },
      {
        path: 'papers',
        name: 'PaperList',
        component: () => import('@/views/paper/PaperList.vue'),
        meta: { title: '试卷管理' },
      },
      {
        path: 'papers/create',
        name: 'PaperCreate',
        component: () => import('@/views/paper/PaperCreate.vue'),
        meta: { title: '手动组卷' },
      },
      {
        path: 'papers/ai-create',
        name: 'PaperAiCreate',
        component: () => import('@/views/paper/PaperAiCreate.vue'),
        meta: { title: 'AI组卷' },
      },
      {
        path: 'papers/:id/records',
        name: 'PaperRecords',
        component: () => import('@/views/paper/PaperRecords.vue'),
        meta: { title: '试卷成绩' },
      },
      {
        path: 'exam-assign',
        name: 'ExamAssign',
        component: () => import('@/views/exam/ExamAssign.vue'),
        meta: { title: '考试分配' },
      },
      {
        path: 'exam-records',
        name: 'ExamRecordList',
        component: () => import('@/views/exam-record/ExamRecordList.vue'),
        meta: { title: '考试记录' },
      },
      {
        path: 'exam-records/:id',
        name: 'ExamRecordDetail',
        component: () => import('@/views/exam-record/ExamRecordDetail.vue'),
        meta: { title: '记录详情' },
      },
      {
        path: 'banners',
        name: 'BannerManage',
        component: () => import('@/views/banner/BannerManage.vue'),
        meta: { title: '轮播图管理' },
      },
      {
        path: 'notices',
        name: 'NoticeManage',
        component: () => import('@/views/notice/NoticeManage.vue'),
        meta: { title: '公告管理' },
      },
      {
        path: 'videos',
        name: 'VideoManage',
        component: () => import('@/views/video/VideoManage.vue'),
        meta: { title: '视频管理' },
      },
      {
        path: 'video-categories',
        name: 'VideoCategoryManage',
        component: () => import('@/views/video-category/VideoCategoryManage.vue'),
        meta: { title: '视频分类' },
      },
      {
        path: 'classes',
        name: 'ClassList',
        component: () => import('@/views/class/ClassList.vue'),
        meta: { title: '班级列表' },
      },
      {
        path: 'classes/:id',
        name: 'ClassDetail',
        component: () => import('@/views/class/ClassDetail.vue'),
        meta: { title: '班级详情' },
      },
      {
        path: 'courses',
        name: 'CourseManage',
        component: () => import('@/views/course/CourseManage.vue'),
        meta: { title: '课程管理' },
      },
      {
        path: 'courses/:id',
        name: 'CourseChapters',
        component: () => import('@/views/course/CourseChapters.vue'),
        meta: { title: '课程内容' },
      },
      {
        path: 'knowledge-points',
        name: 'KnowledgePointManage',
        component: () => import('@/views/knowledge-point/KnowledgePointManage.vue'),
        meta: { title: '知识点管理' },
      },
      {
        path: 'remediation-overview',
        name: 'RemediationOverview',
        component: () => import('@/views/remediation/RemediationOverview.vue'),
        meta: { title: '补课概览' },
      },
    ],
  },

  // ========== 错误页 ==========
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403View.vue'),
    meta: { title: '无权限', requiresAuth: false },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404View.vue'),
    meta: { title: '页面不存在', requiresAuth: false },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  document.title = to.meta.title ? `${to.meta.title} - 智能考试系统` : '智能考试系统'

  // 初始化用户信息
  if (!userStore.userId && userStore.token) {
    userStore.loadFromStorage()
  }

  // 需要登录但未登录
  if (to.meta.requiresAuth !== false && !userStore.token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // 已登录但访问登录页
  if (to.name === 'Login' && userStore.token) {
    next(userStore.isAdmin ? '/admin/dashboard' : '/home')
    return
  }

  // 角色权限检查
  if (to.meta.roles && !to.meta.roles.includes(userStore.role)) {
    next('/403')
    return
  }

  next()
})

export default router
