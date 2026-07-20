import { createRouter, createWebHistory } from 'vue-router'
import ChatLayout from '../layouts/ChatLayout.vue'
import ProjectLayout from '../layouts/ProjectLayout.vue'
import SettingsLayout from '../layouts/SettingsLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
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

export default router
