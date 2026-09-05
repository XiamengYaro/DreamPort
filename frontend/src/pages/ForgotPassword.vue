<template>
  <div class="min-h-screen flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <img :src="logoUrl" alt="XMCraft" class="w-20 h-20 rounded-2xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
        <h1 class="text-3xl font-bold tracking-tight text-white">忘记密码</h1>
        <p class="mt-1 text-stone-400">输入你的邮箱地址，我们将发送重置链接</p>
      </div>

      <div class="card p-8">
        <!-- 发送成功 -->
        <div v-if="sent" class="text-center">
          <svg class="w-16 h-16 text-emerald-400 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
          <h2 class="text-xl font-semibold text-white mb-2">邮件已发送</h2>
          <p class="text-stone-400 mb-6">请查看你的邮箱，点击重置链接修改密码。链接有效期为1小时。</p>
          <router-link to="/login" class="btn-primary inline-block">返回登录</router-link>
        </div>

        <!-- 发送表单 -->
        <form v-else @submit.prevent="handleSubmit">
          <div class="mb-6">
            <label class="block text-sm font-medium mb-2 text-stone-300">邮箱地址</label>
            <input v-model="email" type="email" class="input" placeholder="请输入注册时的邮箱" required />
          </div>
          <button type="submit" class="btn-primary w-full" :disabled="loading">
            {{ loading ? '发送中...' : '发送重置链接' }}
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
import api from '@/services/api'

const notify = inject('notify') as any
const loading = ref(false)
const sent = ref(false)
const email = ref('')
const logoUrl = ref('/logo.png')

onMounted(async () => {
  try {
    const res = await fetch('/api/config')
    const data = await res.json()
    if (data.success && data.data.portal?.logo) {
      logoUrl.value = data.data.portal.logo
    }
  } catch (e) {}
})

const handleSubmit = async () => {
  loading.value = true
  try {
    const res: any = await fetch('/api/auth/forgot-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.value })
    })
    const data = await res.json()
    if (data.success) {
      sent.value = true
    } else {
      notify?.error(data.message || '发送失败')
    }
  } catch (e: any) {
    notify?.error(e.message || '发送失败')
  } finally {
    loading.value = false
  }
}
</script>
