import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Portal',
    component: () => import('./pages/Portal.vue')
  },
  {
    path: '/docs',
    name: 'Docs',
    component: () => import('./pages/Docs.vue')
  },
  {
    path: '/docs/:slug',
    name: 'DocDetail',
    component: () => import('./pages/DocDetail.vue')
  },
  {
    path: '/whitelist',
    name: 'Whitelist',
    component: () => import('./pages/Whitelist.vue')
  },
  {
    path: '/status',
    name: 'Status',
    component: () => import('./pages/Status.vue')
  },
  {
    path: '/setup',
    name: 'Setup',
    component: () => import('./pages/Setup.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('./pages/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('./pages/Register.vue')
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('./pages/ForgotPassword.vue')
  },
  {
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('./pages/ResetPassword.vue')
  },
  {
    path: '/verify',
    name: 'Verify',
    component: () => import('./pages/Verify.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/questionnaire',
    name: 'Questionnaire',
    component: () => import('./pages/Questionnaire.vue')
  },
  {
    path: '/questionnaire-result',
    name: 'QuestionnaireResult',
    component: () => import('./pages/QuestionnaireResult.vue')
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('./pages/Dashboard.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/tasks',
    name: 'Tasks',
    component: () => import('./pages/Tasks.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/tasks',
    name: 'Tasks',
    component: () => import('./pages/Tasks.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/leaderboard',
    name: 'Leaderboard',
    component: () => import('./pages/Leaderboard.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/village',
    name: 'Village',
    component: () => import('./pages/Village.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/players',
    name: 'Players',
    component: () => import('./pages/Players.vue')
  },
  {
    path: '/player/:username',
    name: 'PlayerProfile',
    component: () => import('./pages/PlayerProfile.vue')
  },
  {
    path: '/announcements',
    name: 'Announcements',
    component: () => import('./pages/Announcements.vue')
  },
  {
    path: '/bans',
    name: 'Bans',
    component: () => import('./pages/Bans.vue')
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('./pages/Chat.vue')
  },
  {
    path: '/machines',
    name: 'PublicMachines',
    component: () => import('./pages/PublicMachines.vue')
  },
  {
    path: '/map',
    name: 'Map',
    component: () => import('./pages/Map.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('./pages/Admin.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('./pages/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const isAdmin = localStorage.getItem('isAdmin') === 'true'

  if (to.meta.requiresAuth && !token) {
    window.dispatchEvent(new CustomEvent('app-notify', { detail: { type: 'info', message: '请登录后使用此功能' } }))
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.meta.requiresAdmin && !isAdmin) {
    next({ name: 'Dashboard' })
  } else {
    next()
  }
})

export default router
