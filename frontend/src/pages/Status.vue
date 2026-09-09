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
          <div class="text-center mb-4">
            <div class="text-white/60 text-sm mb-2">用户名</div>
            <div class="text-white font-semibold mb-4">{{ result.username }}</div>
            <span :class="getStatusClass(result.status)">{{ getStatusText(result.status) }}</span>
            <p v-if="result.status === 'rejected' && result.banReason" class="text-stone-400 text-xs mt-3">
              原因：{{ result.banReason }}
            </p>
          </div>
          <!-- 申请进度时间线 -->
          <div class="mt-4 pt-4 border-t border-white/10">
            <div class="text-xs text-stone-500 mb-3">申请进度</div>
            <div v-for="(step, i) in timelineSteps" :key="step.label" class="flex gap-3">
              <div class="flex flex-col items-center">
                <div class="w-3 h-3 rounded-full shrink-0"
                  :class="step.done ? 'bg-emerald-400' : (i < timelineSteps.length - 1 ? 'bg-stone-600' : 'bg-stone-600')"></div>
                <div v-if="i < timelineSteps.length - 1" class="w-px flex-1 min-h-[24px]"
                  :class="step.done ? 'bg-emerald-400/50' : 'bg-stone-700'"></div>
              </div>
              <div class="pb-3">
                <div class="text-sm" :class="step.done ? 'text-white' : 'text-stone-500'">{{ step.label }}</div>
                <div v-if="step.time" class="text-xs text-stone-500">{{ formatStepTime(step.time) }}</div>
              </div>
            </div>
          </div>
        </div>
        <div v-else-if="searched" class="mt-6 p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 text-center">
          <span class="text-amber-400 text-sm">未找到该用户名的申请记录</span>
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
import { ref, computed, inject } from 'vue'
import api from '@/services/api'

const notify = inject('notify') as any

const loading = ref(false)
const username = ref('')
const result = ref<any>(null)
const searched = ref(false)

const queryStatus = async () => {
  loading.value = true
  result.value = null
  searched.value = false
  try {
    // 后端返回裸结构 {found, username, status, ...}（ReviewStatusController 契约）
    const data: any = await api.getReviewStatus(username.value.trim())
    if (data.found) {
      result.value = data
    } else {
      searched.value = true
    }
  } catch (error: any) {
    notify?.error(error.message || '查询失败')
  } finally {
    loading.value = false
  }
}

const timelineSteps = computed(() => {
  if (!result.value) return []
  const r = result.value
  const steps = [
    { label: '注册账号', time: r.createdAt || r.regTime, done: true },
    { label: '完成问卷', time: r.questionnaireScoredAt, done: !!r.questionnaireScoredAt },
    // 修复审计：不再用 Date.now() 冒充审核时间；封禁用户展示封禁时间,其余无真实时间戳则不伪造
    { label: '审核结果', time: r.status === 'banned' ? r.banTime : (r.status === 'pending' ? null : (r.questionnaireScoredAt || null)), done: ['approved', 'rejected', 'banned'].includes(r.status) },
    { label: 'ID 验证', time: r.verifiedAt, done: !!r.verifiedAt },
  ]
  return steps
})

const formatStepTime = (ts: number) => {
  if (!ts) return ''
  return new Date(ts).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
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
