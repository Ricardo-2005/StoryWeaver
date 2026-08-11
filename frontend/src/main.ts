import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { setUnauthorizedHandler } from './api/client'
import { clearSession } from './features/auth/session'
import { queryClient } from './queries/queryClient'
import router from './router'
import 'element-plus/dist/index.css'
import './styles/index.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(VueQueryPlugin, { queryClient })

setUnauthorizedHandler(() => {
  clearSession(queryClient)
  const redirect = router.currentRoute.value.meta.requiresAuth
    ? router.currentRoute.value.fullPath
    : undefined
  void router.replace({
    name: 'login',
    query: redirect ? { redirect } : {},
  })
})

app.mount('#app')
