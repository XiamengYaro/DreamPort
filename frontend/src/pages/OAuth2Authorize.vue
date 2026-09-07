<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-md mx-auto">
      <!-- 加载中 -->
      <div v-if="loading" class="card p-8 text-center text-stone-400">
        <svg class="animate-spin h-8 w-8 mx-auto mb-3 text-orange-400" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        正在校验授权请求...
      </div>

      <!-- 请求无效 -->
      <div v-else-if="!info.valid" class="card p-8 text-center">
        <AppIcon name="x-circle" class="w-12 h-12 mx-auto mb-3 text-rose-400" />
        <h1 class="text-lg font-semibold text-white mb-2">无法授权</h1>
        <p class="text-sm text-stone-400 mb-1">该授权请求无效：</p>
        <p class="text-sm text-rose-300">{{ info.reason || '参数缺失' }}</p>
      </div>

      <!-- 已取消 -->
      <div v-else-if="denied" class="card p-8 text-center">
        <AppIcon name="no-symbol" class="w-12 h-12 mx-auto mb-3 text-stone-400" />
        <h1 class="text-lg font-semibold text-white mb-2">已取消授权</h1>
        <p class="text-sm text-stone-400">你已拒绝本次授权请求，可以关闭此页面返回皮肤站。</p>
      </div>

      <!-- 确认授权 -->
      <div v-else class="card p-8">
        <div class="flex items-center justify-center gap-3 mb-6">
          <div class="w-14 h-14 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center">
            <img src="/Logo111.png" alt="DreamPort" class="w-9 h-9 object-contain" />
          </div>
          <AppIcon name="arrow-right" class="w-5 h-5 text-stone-500" />
          <div class="w-14 h-14 rounded-2xl bg-orange-500/15 border border-orange-500/30 flex items-center justify-center">
            <AppIcon name="user" class="w-7 h-7 text-orange-400" />
          </div>
        </div>
        <h1 class="text-lg font-semibold text-white text-center mb-1">授权请求</h1>
        <p class="text-sm text-stone-400 text-center mb-6">
          <span class="text-orange-400 font-medium">{{ info.clientName }}</span> 请求访问你的 DreamPort 账号
        </p>

        <div class="rounded-xl bg-stone-900/40 border border-stone-800 p-4 mb-6 text-sm space-y-2">
          <div class="flex justify-between">
            <span class="text-stone-500">登录身份</span>
            <span class="text-stone-200 font-mono">{{ username }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-stone-500">将共享的信息</span>
            <span class="text-stone-200">用户名、邮箱、游戏 ID</span>
          </div>
          <div class="flex justify-between">
            <span class="text-stone-500">回调地址</span>
            <span class="text-stone-300 font-mono text-xs max-w-[55%] break-all text-right">{{ redirectUri }}</span>
          </div>
        </div>

        <div v-if="error" class="mb-4 text-sm text-rose-400 text-center">{{ error }}</div>

        <div class="flex gap-3">
          <button class="btn-secondary flex-1 justify-center" :disabled="submitting" @click="deny">拒绝</button>
          <button class="btn-primary flex-1 justify-center" :disabled="submitting" @click="approve">
            {{ submitting ? '处理中...' : '允许授权' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'
import AppIcon from '@/components/AppIcon.vue'

const route = useRoute()
const loading = ref(true)
const denied = ref(false)
const submitting = ref(false)
const error = ref('')
const username = ref('')
const clientId = ref('')
const redirectUri = ref('')
const state = ref('')
const info = ref<{ clientName?: string; valid: boolean; reason?: string }>({ valid: false })

onMounted(async () => {
  clientId.value = String(route.query.client_id || '')
  redirectUri.value = String(route.query.redirect_uri || '')
  state.value = String(route.query.state || '')
  username.value = localStorage.getItem('username') || ''
  try {
    const r: any = await api.getOAuth2AuthorizeInfo(clientId.value, redirectUri.value)
    if (r.success) {
      info.value = r.data
    } else {
      info.value = { valid: false, reason: r.message }
    }
  } catch (e: any) {
    info.value = { valid: false, reason: e?.message || '网络错误' }
  }
  loading.value = false
})

const approve = async () => {
  submitting.value = true
  error.value = ''
  try {
    const r: any = await api.postOAuth2Authorize({
      clientId: clientId.value,
      redirectUri: redirectUri.value,
      state: state.value,
      approved: true
    })
    if (r.success && r.data?.redirectUrl) {
      window.location.href = r.data.redirectUrl
      return
    }
    error.value = r.message || '授权失败'
  } catch (e: any) {
    error.value = e?.message || '授权失败'
  }
  submitting.value = false
}

const deny = async () => {
  denied.value = true
  try {
    await api.postOAuth2Authorize({
      clientId: clientId.value,
      redirectUri: redirectUri.value,
      state: state.value,
      approved: false
    })
  } catch { /* 拒绝无需提示错误 */ }
}
</script>
