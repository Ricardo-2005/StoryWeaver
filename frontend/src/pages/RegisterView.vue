<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'

import { authApi } from '@/api/endpoints/auth'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import { establishSession } from '@/features/auth/session'
import AuthLayout from '@/layouts/AuthLayout.vue'

const router = useRouter()
const queryClient = useQueryClient()
const form = reactive({ username: '', email: '', password: '', confirmPassword: '' })
const localError = ref('')
const registerMutation = useMutation({ mutationFn: authApi.register })
const submitError = computed(() => localError.value || registerMutation.error.value)

async function submit(): Promise<void> {
  localError.value = ''
  if (form.password !== form.confirmPassword) {
    localError.value = '两次输入的密码不一致。'
    return
  }

  try {
    const response = await registerMutation.mutateAsync({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
    })
    establishSession(response, queryClient)
    await router.replace('/projects')
  } catch {
    // Normalized Problem Details is rendered above the form.
  }
}
</script>

<template>
  <AuthLayout>
    <p class="eyebrow">开始创作</p>
    <h1 tabindex="-1">创建文脉账户</h1>
    <p class="auth-intro">建立你的长篇项目，所有正典修改仍由你确认。</p>

    <div v-if="typeof submitError === 'string' && submitError" class="problem-alert" role="alert">
      {{ submitError }}
    </div>
    <ProblemAlert v-else-if="submitError" :error="submitError" />

    <form class="sw-form" @submit.prevent="submit">
      <label class="form-field">
        <span>用户名</span>
        <input v-model="form.username" name="username" autocomplete="username" minlength="3" maxlength="50" required />
        <small>3—50 个字符</small>
      </label>
      <label class="form-field">
        <span>邮箱</span>
        <input v-model="form.email" name="email" type="email" autocomplete="email" maxlength="320" required />
      </label>
      <label class="form-field">
        <span>密码</span>
        <input v-model="form.password" name="password" type="password" autocomplete="new-password" minlength="8" maxlength="72" required />
        <small>至少 8 个字符</small>
      </label>
      <label class="form-field">
        <span>确认密码</span>
        <input v-model="form.confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required />
      </label>
      <button class="sw-button sw-button--primary sw-button--block" type="submit" :disabled="registerMutation.isPending.value">
        {{ registerMutation.isPending.value ? '正在创建…' : '创建账户' }}
      </button>
    </form>

    <p class="auth-switch">已有账户？<RouterLink to="/login">直接登录</RouterLink></p>
  </AuthLayout>
</template>
