<template>
  <div class="min-h-screen flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <img :src="logoUrl" alt="XMCraft" class="w-20 h-20 rounded-2xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
        <h1 class="text-3xl font-bold tracking-tight text-white">重置密码</h1>
        <p class="mt-1 text-stone-400">请输入你的新密码</p>
      </div>

      <div class="card p-8">
        <!-- 重置成功 -->
        <div v-if="resetSuccess" class="text-center">
          <svg class="w-16 h-16 text-emerald-400 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
          </svg>
          <h2 class="text-xl font-semibold text-white mb-2">密码重置成功</h2>
          <p class="text-stone-400 mb-6">你的密码已重置，请使用新密码登录。</p>
          <router-link to="/login" class="btn-primary inline-block">前往登录</router-link>
        </div>

        <!-- 重置表单 -->
        <form v-else @submit.prevent="handleSubmit">
          <div class="mb-4">
            <label class="block text-sm font-medium mb-2 text-stone-300">新密码</label>
            <div class="relative">
              <input :type="showPassword ? 'text' : 'password'" v-model="password" class="input pr-10" placeholder="至少8位" required />
              <button type="button" @click="showPassword = !showPassword" class="absolute right-3 top-1/2 -translate-y-1/2 text-stone-400 hover:text-white">
                <svg v-if="!showPassword" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                </svg>
              </button>
            </div>
          </div>
          <div class="mb-6">
            <label class="block text-sm font-medium mb-2 text-stone-300">确认新密码</label>
            <div class="relative">
              <input :type="showConfirmPassword ? 'text' : 'password'" v-model="confirmPassword" class="input pr-10" placeholder="请再次输入密码" required />
              <button type="button" @click="showConfirmPassword = !showConfirmPassword" class="absolute right-3 top-1/2 -translate-y-1/2 text-stone-400 hover:text-white">
                <svg v-if="!showConfirmPassword" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                </svg>
              </button>
            </div>
          </div>
          <button type="submit" class="btn-primary w-full" :disabled="loading">
            {{ loading ? '重置中...' : '重置密码' }}
          </button>
        </form>

        <div class="mt-6 text-center">
          <router-link to="/login" class="text-sm text-orange-500 hover:text-orange-400 transition-colors">返回登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'

const route = useRoute()
const notify = inject('notify') as any
const loading = ref(false)
const resetSuccess = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const password = ref('')
const confirmPassword = ref('')
const token = ref('')
const logoUrl = ref('/logo.png')

onMounted(async () => {
  token.value = (route.query.token as string) || ''
  
  try {
    const res = await fetch('/api/config')
    const data = await res.json()
    if (data.success && data.data.portal?.logo) {
      logoUrl.value = data.data.portal.logo
    }
  } catch (e) {}
})

const handleSubmit = async () => {
  if (password.value !== confirmPassword.value) {
    notify?.error('两次输入的密码不一致')
    return
  }
  if (password.value.length < 8) {
    notify?.error('密码长度至少8位')
    return
  }
  if (!token.value) {
    notify?.error('无效的重置链接')
    return
  }

  loading.value = true
  try {
    const res = await fetch('/api/auth/reset-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: token.value, password: password.value })
    })
    const data = await res.json()
    if (data.success) {
      resetSuccess.value = true
    } else {
      notify?.error(data.message || '重置失败')
    }
  } catch (e: any) {
    notify?.error(e.message || '重置失败')
  } finally {
    loading.value = false
  }
}
</script>
