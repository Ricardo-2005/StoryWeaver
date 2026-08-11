<script setup lang="ts">
import { reactive } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'

import { authApi } from '@/api/endpoints/auth'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import { establishSession } from '@/features/auth/session'
import AuthLayout from '@/layouts/AuthLayout.vue'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const form = reactive({ identifier: '', password: '' })
const loginMutation = useMutation({ mutationFn: authApi.login })

function safeRedirect(): string {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : '/projects'
}

async function submit(): Promise<void> {
  try {
    const response = await loginMutation.mutateAsync({
      identifier: form.identifier.trim(),
      password: form.password,
    })
    establishSession(response, queryClient)
    await router.replace(safeRedirect())
  } catch {
    // Normalized Problem Details is rendered above the form.
  }
}
</script>

<template>
  <AuthLayout>
    <p class="eyebrow">欢迎回来</p>
    <h1 tabindex="-1">登录文脉</h1>
    <p class="auth-intro">继续维护你的项目、设定与正典资产。</p>

    <ProblemAlert v-if="loginMutation.isError.value" :error="loginMutation.error.value" />

    <form class="sw-form" @submit.prevent="submit">
      <label class="form-field">
        <span>邮箱或用户名</span>
        <input
          v-model="form.identifier"
          name="identifier"
          autocomplete="username"
          maxlength="320"
          required
          autofocus
        />
      </label>
      <label class="form-field">
        <span>密码</span>
        <input
          v-model="form.password"
          name="password"
          type="password"
          autocomplete="current-password"
          maxlength="72"
          required
        />
      </label>
      <button class="sw-button sw-button--primary sw-button--block" type="submit" :disabled="loginMutation.isPending.value">
        {{ loginMutation.isPending.value ? '正在登录…' : '登录' }}
      </button>
    </form>

    <p class="auth-switch">还没有账户？<RouterLink to="/register">创建账户</RouterLink></p>
  </AuthLayout>
</template>
