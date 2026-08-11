import { createRouter, createWebHistory } from 'vue-router'

import { getAccessToken } from '@/api/tokenMemory'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/projects',
      meta: { title: '文脉' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/pages/LoginView.vue'),
      meta: {
        title: '登录',
        guestOnly: true,
      },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/pages/RegisterView.vue'),
      meta: {
        title: '注册',
        guestOnly: true,
      },
    },
    {
      path: '/projects',
      component: () => import('@/layouts/AppShell.vue'),
      meta: {
        title: '项目',
        requiresAuth: true,
      },
      children: [
        {
          path: '',
          name: 'projects',
          component: () => import('@/pages/ProjectsView.vue'),
          meta: { title: '项目', requiresAuth: true },
        },
        {
          path: 'new',
          name: 'project-new',
          component: () => import('@/pages/NewProjectView.vue'),
          meta: { title: '新建项目', requiresAuth: true },
        },
        {
          path: 'import/txt',
          name: 'txt-book-import',
          component: () => import('@/pages/TxtBookImportView.vue'),
          meta: { title: '导入 TXT 书籍', requiresAuth: true },
        },
        {
          path: 'import/txt/:importId',
          name: 'txt-book-import-preview',
          component: () => import('@/pages/TxtBookImportView.vue'),
          meta: { title: 'TXT 导入预览', requiresAuth: true },
        },
        {
          path: 'archived',
          name: 'archived-projects',
          component: () => import('@/pages/ArchivedProjectsView.vue'),
          meta: { title: '归档项目', requiresAuth: true },
        },
        {
          path: ':projectId',
          name: 'project-detail',
          component: () => import('@/pages/ProjectDetailView.vue'),
          meta: { title: '项目详情', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/characters', name: 'characters', component: () => import('@/pages/CharactersView.vue'),
          meta: { title: '人物', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/workspace', name: 'workspace', component: () => import('@/pages/WorkspaceView.vue'),
          meta: { title: '创作工作台', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/worldbook', name: 'worldbook', component: () => import('@/pages/WorldbookView.vue'),
          meta: { title: '世界书', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/outlines', name: 'outlines', component: () => import('@/pages/OutlinesView.vue'),
          meta: { title: '大纲', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/chapters', name: 'chapters', component: () => import('@/pages/ChaptersView.vue'),
          meta: { title: '章节', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/imports', name: 'imports', component: () => import('@/pages/ImportsView.vue'),
          meta: { title: '导入与迁移', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/rolling-outline', name: 'rolling-outline', component: () => import('@/pages/RollingOutlineView.vue'),
          meta: { title: '滚动大纲', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/production', name: 'production', component: () => import('@/pages/ProductionView.vue'),
          meta: { title: '连续写作', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/foreshadows', name: 'foreshadows', component: () => import('@/pages/ForeshadowsView.vue'),
          meta: { title: '伏笔台账', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/chapters/:chapterId', name: 'chapter-editor', component: () => import('@/pages/ChapterEditorView.vue'),
          meta: { title: '章节编辑器', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/chapters/:chapterId/workflows/:runId', name: 'workflow-status', component: () => import('@/pages/WorkflowStatusView.vue'),
          meta: { title: '工作流状态', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/skills', name: 'skills', component: () => import('@/pages/SkillsView.vue'),
          meta: { title: 'Skill', requiresAuth: true, requiresProject: true },
        },
        {
          path: ':projectId/observability', name: 'observability', component: () => import('@/pages/ObservabilityView.vue'),
          meta: { title: '模型与费用', requiresAuth: true, requiresProject: true },
        },
      ],
    },
    {
      path: '/skills',
      component: () => import('@/layouts/AppShell.vue'),
      meta: { title: 'Skill 工坊', requiresAuth: true },
      children: [
        { path: '', name: 'global-skills', component: () => import('@/pages/GlobalSkillsView.vue'), meta: { title: 'Skill 工坊', requiresAuth: true } },
        { path: 'new', name: 'global-skill-new', component: () => import('@/pages/SkillForgeView.vue'), meta: { title: '新建 Skill', requiresAuth: true } },
        { path: 'import', redirect: '/skills/forge' },
        { path: 'forge', name: 'skill-forge', component: () => import('@/pages/SkillForgeView.vue'), meta: { title: 'Skill 熔炉', requiresAuth: true } },
        { path: ':skillId', name: 'global-skill-detail', component: () => import('@/pages/GlobalSkillDetailView.vue'), meta: { title: 'Skill 契约', requiresAuth: true } },
        { path: ':skillId/edit', redirect: (to) => `/skills/${String(to.params.skillId)}` },
        { path: ':skillId/versions', redirect: (to) => `/skills/${String(to.params.skillId)}` },
        { path: ':skillId/tests', redirect: (to) => `/skills/${String(to.params.skillId)}` },
      ],
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/pages/StatusView.vue'),
      props: { status: 403 },
      meta: { title: '没有权限' },
    },
    {
      path: '/error',
      name: 'error',
      component: () => import('@/pages/StatusView.vue'),
      props: { status: 500 },
      meta: { title: '服务异常' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/pages/StatusView.vue'),
      props: { status: 404 },
      meta: { title: '页面不存在' },
    },
  ],
})

let initialNavigationCompleted = false
let headingObserver: MutationObserver | undefined
let headingFocusTimeout: number | undefined

function focusRouteHeading(): void {
  headingObserver?.disconnect()
  if (headingFocusTimeout !== undefined) window.clearTimeout(headingFocusTimeout)

  const focusHeading = (): boolean => {
    const heading = document.querySelector<HTMLElement>('main h1')
    if (!heading) return false
    heading.focus()
    return true
  }

  window.requestAnimationFrame(() => {
    if (focusHeading()) return

    const root = document.querySelector('#main-content') ?? document.body
    headingObserver = new MutationObserver(() => {
      if (focusHeading()) headingObserver?.disconnect()
    })
    headingObserver.observe(root, { childList: true, subtree: true })
    headingFocusTimeout = window.setTimeout(() => headingObserver?.disconnect(), 5_000)
  })
}

router.beforeEach((to) => {
  const authenticated = Boolean(getAccessToken())

  if (to.meta.requiresAuth && !authenticated) {
    return {
      name: 'login',
      query: to.fullPath === '/projects' ? {} : { redirect: to.fullPath },
    }
  }

  if (to.meta.guestOnly && authenticated) {
    return { name: 'projects' }
  }

  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : undefined
  document.title = title ? `${title} · 文脉` : '文脉 · StoryWeaver'

  if (initialNavigationCompleted) {
    focusRouteHeading()
  }
  initialNavigationCompleted = true
})

export default router
