<template>
  <div class="min-h-screen flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <img :src="logoUrl" alt="XMCraft" class="w-20 h-20 rounded-2xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
        <h1 class="text-3xl font-bold tracking-tight text-white">连接至 XMCraft</h1>
        <p class="mt-1 text-stone-400">XMCraft 用户账户系统</p>
      </div>

      <div v-if="!pendingAction" class="card p-8
">
        <form @submit.prevent="handleLogin">
          <div class="mb-4">
            <label class="block text-sm font-medium mb-2 text-stone-300">用户名</label>
            <input v-model="form.username" type="text" class="input" placeholder="请输入用户名" required />
          </div>
          <div class="mb-6">
            <label class="block text-sm font-medium mb-2 text-stone-300">密码</label>
            <div class="relative">
              <input :type="showPassword ? 'text' : 'password'" v-model="form.password" class="input pr-10" placeholder="请输入密码" required />
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
          <button type="submit" class="btn-primary w-full" :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button>
        </form>
        <div class="mt-4 text-center">
          <router-link to="/forgot-password" class="text-sm text-stone-400 hover:text-orange-400 transition-colors">忘记密码？</router-link>
        </div>
        <div class="mt-4 text-center">
          <router-link to="/register" class="text-sm text-orange-500 hover:text-orange-400 transition-colors">没有 XMCraft 账号？立即注册</router-link>
        </div>
      </div>

      <!-- 问卷未通过 -->
      <div v-else-if="pendingStatus === 'rejected'" class="card p-8
">
        <div class="text-center mb-6">
          <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-orange-900/20 mb-4">
            <svg class="w-8 h-8 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          </div>
          <h2 class="text-xl font-semibold mb-2 text-white">入服审核问卷未过审</h2>
          <p class="text-sm text-stone-400">你的入服审核问卷得分 {{ questionnaireScore }} 分，未达到通过标准。</p>
        </div>
        <div class="space-y-3">
          <button @click="continueQuestionnaire" class="btn-primary w-full">返回答题</button>
          <button @click="exitLogin" class="btn-secondary w-full">退出登录</button>
        </div>
      </div>

      <!-- 等待管理员审核 -->
      <div v-else-if="pendingStatus === 'pending_review'" class="card p-8
">
        <div class="text-center mb-6">
          <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-amber-900/20 mb-4">
            <svg class="w-8 h-8 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h2 class="text-xl font-semibold mb-2 text-white">等待管理员审核</h2>
          <p class="text-sm text-stone-400">你的问卷已通过审核，请等待管理员确认白名单申请。</p>
        </div>
        <button @click="pendingAction = null; form.username = ''; form.password = ''" class="btn-secondary w-full">返回登录</button>
      </div>

      <!-- 等待邀请人确认 -->
      <div v-else-if="pendingStatus === 'invited_pending'" class="card p-8
">
        <div class="text-center mb-6">
          <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-blue-900/20 mb-4">
            <svg class="w-8 h-8 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
          </div>
          <h2 class="text-xl font-semibold mb-2 text-white">等待邀请人确认</h2>
          <p class="text-sm text-stone-400">你已通过邀请码注册，请等待邀请人确认你的身份。</p>
        </div>
        <button @click="pendingAction = null; pendingStatus = ''; form.username = ''; form.password = ''" class="btn-secondary w-full">返回登录</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '@/services/api'

const router = useRouter()
const route = useRoute()
const notify = inject('notify') as any
const logoUrl = ref('/logo.png')

onMounted(async () => {
  try { const res = await fetch('/api/config'); const data = await res.json(); if (data.success && data.data.portal?.logo) logoUrl.value = data.data.portal.logo } catch (e) {}
})

const loading = ref(false)
const showPassword = ref(false)
const form = ref({ username: '', password: '' })
const pendingAction = ref<string | null>(null)
const pendingStatus = ref('')
const questionnaireScore = ref(0)

const handleLogin = async () => {
  loading.value = true
  try {
    const response: any = await api.login(form.value.username, form.value.password)
    if (response.success) {
      const data = response.data
      const status = data.status || 'approved'

      if (status === 'approved') {
        api.setToken(data.token)
        localStorage.setItem('username', data.username)
        if (data.isAdmin) localStorage.setItem('isAdmin', 'true')
        else localStorage.removeItem('isAdmin')
        notify?.success('登录成功')
        // 安全重定向：验证路径以防止开放重定向攻击
        const redirectPath = route.query.redirect as string
        if (redirectPath && redirectPath.startsWith('/') && !redirectPath.startsWith('//')) {
          router.push(redirectPath)
        } else {
          router.push('/dashboard')
        }
      } else if (status === 'needs_questionnaire') {
        api.setToken(data.token)
        localStorage.setItem('username', data.username)
        localStorage.setItem('pendingUsername', data.username)
        notify?.info('请前往白名单申请页面选择入服方式')
        router.push('/whitelist')
      } else if (status === 'rejected') {
        api.setToken(data.token)
        pendingStatus.value = 'rejected'
        pendingAction.value = 'rejected'
        questionnaireScore.value = data.questionnaireScore || 0
        localStorage.setItem('pendingUsername', data.username)
      } else if (status === 'pending_review') {
        pendingStatus.value = 'pending_review'
        pendingAction.value = 'pending_review'
      } else if (status === 'invited_pending') {
        api.setToken(data.token)
        localStorage.setItem('username', data.username)
        pendingStatus.value = 'invited_pending'
        pendingAction.value = 'invited_pending'
      } else if (status === 'pending_verify') {
        api.setToken(data.token)
        localStorage.setItem('username', data.username)
        localStorage.setItem('pendingUsername', data.username)
        notify?.info('请前往白名单申请页面完成问卷或 ID 验证')
        router.push('/whitelist')
      } else {
        notify?.error(data.message || '登录失败')
      }
    }
  } catch (error: any) {
    notify?.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const continueQuestionnaire = () => {
  router.push('/questionnaire')
}

const exitLogin = () => {
  pendingAction.value = null
  pendingStatus.value = ''
  form.value.username = ''
  form.value.password = ''
  localStorage.removeItem('pendingToken')
  localStorage.removeItem('pendingUsername')
  notify?.info('已退出登录')
}
</script>
