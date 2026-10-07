import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken, getCachedUser } from '@/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', blank: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { title: '注册', blank: true }
  },

  /* ================= 主布局 ================= */
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '统计看板', icon: 'Odometer' }
      },

      /* --- 植物库 --- */
      {
        path: 'species',
        name: 'SpeciesList',
        component: () => import('@/views/species/index.vue'),
        meta: { title: '植物库' }
      },
      {
        path: 'species/:id',
        name: 'SpeciesDetail',
        component: () => import('@/views/species/detail.vue'),
        meta: { title: '植物详情', hidden: true, activeMenu: '/species' }
      },

      /* --- 我的植物 --- */
      {
        path: 'plants',
        name: 'PlantList',
        component: () => import('@/views/plants/index.vue'),
        meta: { title: '我的植物' }
      },
      {
        path: 'plants/new',
        name: 'PlantNew',
        component: () => import('@/views/plants/new.vue'),
        meta: { title: '添加植物', activeMenu: '/plants' }
      },
      {
        path: 'plants/:id',
        name: 'PlantDetail',
        component: () => import('@/views/plants/detail.vue'),
        meta: { title: '植物详情', hidden: true, activeMenu: '/plants' }
      },

      /* --- AI 能力 --- */
      {
        path: 'recognize',
        name: 'Recognize',
        component: () => import('@/views/recognize/index.vue'),
        meta: { title: '拍照识别' }
      },
      {
        // 智能诊断：拍照识别 → 病害识别 → 症状诊断 三步向导
        path: 'diagnose',
        name: 'Diagnose',
        component: () => import('@/views/diagnose/index.vue'),
        meta: { title: '智能诊断' }
      },
      // 兼容旧链接：原「病害识别」「症状诊断」独立页已合并进向导
      {
        path: 'diagnose/disease',
        redirect: { path: '/diagnose', query: { step: '2' } }
      },
      {
        path: 'diagnose/symptom',
        redirect: { path: '/diagnose', query: { step: '3' } }
      },
      {
        path: 'diagnose/records',
        name: 'DiagnoseRecords',
        component: () => import('@/views/diagnose/records.vue'),
        meta: { title: '诊断记录', hidden: true, activeMenu: '/diagnose' }
      },
      {
        path: 'qa',
        name: 'Qa',
        component: () => import('@/views/qa/index.vue'),
        meta: { title: 'AI 问答' }
      },

      /* --- 养护 --- */
      {
        path: 'care/calendar',
        name: 'CareCalendar',
        component: () => import('@/views/care/calendar.vue'),
        meta: { title: '养护日历' }
      },
      {
        path: 'care/plans',
        name: 'CarePlans',
        component: () => import('@/views/care/plans.vue'),
        meta: { title: '养护计划' }
      },
      {
        path: 'care/logs',
        name: 'CareLogs',
        component: () => import('@/views/care/logs.vue'),
        meta: { title: '养护日志' }
      },

      /* --- 我的 --- */
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'profile/environment',
        name: 'ProfileEnvironment',
        component: () => import('@/views/profile/environment.vue'),
        meta: { title: '环境设置', activeMenu: '/profile' }
      }
    ]
  },

  /* ================= 管理端 ================= */
  {
    path: '/admin',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/admin/species',
    meta: { requiresAdmin: true },
    children: [
      {
        path: 'species',
        name: 'AdminSpecies',
        component: () => import('@/views/admin/species.vue'),
        meta: { title: '植物库维护', requiresAdmin: true }
      },
      {
        path: 'diseases',
        name: 'AdminDiseases',
        component: () => import('@/views/admin/diseases.vue'),
        meta: { title: '病害信息', requiresAdmin: true }
      },
      {
        path: 'symptom-rules',
        name: 'AdminSymptomRules',
        component: () => import('@/views/admin/symptomRules.vue'),
        meta: { title: '症状规则', requiresAdmin: true }
      },
      {
        path: 'knowledge',
        name: 'AdminKnowledge',
        component: () => import('@/views/admin/knowledge.vue'),
        meta: { title: '知识库', requiresAdmin: true }
      },
      {
        path: 'stats',
        name: 'AdminStats',
        component: () => import('@/views/admin/stats.vue'),
        meta: { title: 'AI 用量统计', requiresAdmin: true }
      }
    ]
  },

  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', blank: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

const WHITE_LIST = ['/login', '/register']

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 植语助手` : '植语助手 GreenThumb AI'

  const token = getToken()

  if (!token) {
    if (WHITE_LIST.includes(to.path)) return true
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录时访问登录页 → 回首页
  if (WHITE_LIST.includes(to.path)) return { path: '/dashboard' }

  // 管理端权限
  if (to.meta.requiresAdmin) {
    const user = getCachedUser()
    if (user?.role !== 'ADMIN') {
      ElMessage.warning('需要管理员权限')
      return { path: '/dashboard' }
    }
  }

  return true
})

export default router
