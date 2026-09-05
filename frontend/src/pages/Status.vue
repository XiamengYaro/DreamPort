<template>
  <div class="min-h-screen flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <div class="inline-flex items-center justify-center w-20 h-20 rounded-2xl bg-gradient-to-br from-primary-500 to-accent-500 mb-4">
          <svg class="w-10 h-10 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
          </svg>
        </div>
        <h1 class="text-3xl font-bold text-white mb-2">申请状态查询</h1>
      </div>

      <div class="card p-8
">
        <form @submit.prevent="queryStatus">
          <div class="mb-6">
            <label class="block text-sm font-medium text-white/70 mb-2">用户名</label>
            <input
              v-model="username"
              type="text"
              class="input"
              placeholder="请输入用户名"
              required
            />
          </div>

          <button type="submit" class="btn-primary w-full" :disabled="loading">
            {{ loading ? '查询中...' : '查询状态' }}
          </button>
        </form>

        <div v-if="result" class="mt-6 p-4 rounded-xl bg-white/5">
          <div class="text-center">
            <div class="text-white/60 text-sm mb-2">用户名</div>
            <div class="text-white font-semibold mb-4">{{ result.username }}</div>

            <div class="text-white/60 text-sm mb-2">状态</div>
            <span :class="getStatusClass(result.status)">{{ getStatusText(result.status) }}</span>
          </div>
        </div>
      </div>

      <div class="text-center mt-6">
        <router-link to="/" class="text-white/50 hover:text-white/70 text-sm">
          返回首页
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, inject } from 'vue'
import api from '@/services/api'

const notify = inject('notify') as any

const loading = ref(false)
const username = ref('')
const result = ref<any>(null)

const queryStatus = async () => {
  loading.value = true
  result.value = null
  try {
    const response: any = await fetch(`/api/review/status?username=${encodeURIComponent(username.value)}`)
    const data = await response.json()
    if (data.success) {
      result.value = data.data
    } else {
      notify?.error(data.message || '查询失败')
    }
  } catch (error: any) {
    notify?.error(error.message || '查询失败')
  } finally {
    loading.value = false
  }
}

const getStatusClass = (status: string) => {
  const classes: Record<string, string> = {
    pending: 'badge-warning',
    approved: 'badge-success',
    rejected: 'badge-danger',
    banned: 'badge-danger'
  }
  return classes[status] || 'badge-info'
}

const getStatusText = (status: string) => {
  const texts: Record<string, string> = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已拒绝',
    banned: '已封禁'
  }
  return texts[status] || status
}
</script>
