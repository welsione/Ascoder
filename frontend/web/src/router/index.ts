import { createRouter, createWebHistory } from 'vue-router'
import ChatLayout from '../layouts/ChatLayout.vue'
import ProjectLayout from '../layouts/ProjectLayout.vue'
import SettingsLayout from '../layouts/SettingsLayout.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('../views/ChangePasswordView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      redirect: '/projects',
    },
    {
      path: '/projects',
      component: ProjectLayout,
      children: [
        {
          path: '',
          name: 'projects',
          component: () => import('../views/SpacesView.vue'),
        },
        {
          path: 'config',
          name: 'project-config',
          component: () => import('../views/ProjectConfigView.vue'),
        },
        {
          path: ':projectId/analysis-spaces/new',
          name: 'analysis-space-create',
          component: () => import('../views/ProjectConfigView.vue'),
          props: true,
        },
        {
          path: ':projectSpaceId/config',
          name: 'project-space-config',
          component: () => import('../views/ProjectConfigView.vue'),
          props: true,
        },
        {
          path: ':projectId',
          name: 'project-detail',
          component: () => import('../views/SpaceDetailView.vue'),
          props: true,
        },
      ],
    },
    {
      path: '/project-spaces/:projectSpaceId/self-learning',
      component: ProjectLayout,
      children: [
        {
          path: '',
          name: 'project-space-self-learning',
          component: () => import('../views/SelfLearningView.vue'),
          props: true,
        },
      ],
    },
    {
      path: '/chat',
      component: ChatLayout,
      children: [
        {
          path: '',
          name: 'chat',
          component: () => import('../views/ChatView.vue'),
        },
        {
          path: ':questionId',
          name: 'chat-question',
          component: () => import('../views/ChatView.vue'),
          props: true,
        },
      ],
    },
    {
      path: '/settings',
      component: SettingsLayout,
      children: [
        {
          path: '',
          name: 'settings',
          component: () => import('../views/SettingsView.vue'),
        },
        {
          path: ':section',
          name: 'settings-section',
          component: () => import('../views/SettingsView.vue'),
          props: true,
        },
      ],
    },
  ],
})

const WHITE_LIST = ['/login', '/register', '/change-password']

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 白名单路由直接放行
  if (WHITE_LIST.includes(to.path)) {
    if (auth.isAuthenticated && to.path === '/login') {
      return { path: '/' }
    }
    return true
  }

  // 未认证 -> 尝试用 refreshToken 恢复 -> 失败跳转登录
  if (!auth.isAuthenticated) {
    if (auth.refreshToken) {
      try {
        await auth.refresh()
        await auth.fetchMe()
      } catch {
        auth.clearTokens()
        return { name: 'login', query: { redirect: to.fullPath } }
      }
    } else {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }

  // 已认证但无用户信息（页面刷新后）
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      auth.clearTokens()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }

  // 强制改密：默认管理员首次登录必须先改密
  if (auth.mustChangePassword && to.path !== '/change-password') {
    return { name: 'change-password' }
  }

  return true
})

export default router
