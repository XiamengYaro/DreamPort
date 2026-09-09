<template>
  <div class="min-h-screen flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <img :src="logoUrl" :alt="brand.short" class="w-20 h-20 rounded-2xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
        <h1 class="text-3xl font-bold tracking-tight text-white">连接至 {{ brand.short }}</h1>
        <p class="mt-1 text-stone-400">{{ brand.tagline }}</p>
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
          <router-link to="/register" class="text-sm text-orange-500 hover:text-orange-400 transition-colors">没有 {{ brand.short }} 账号？立即注册</router-link>
        </div>
      </div>

      <!-- 两步验证 -->
      <div v-else-if="pendingStatus === 'needs_2fa'" class="card p-8
">
        <div class="text-center mb-6">
          <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-orange-900/20 mb-4">
            <AppIcon name="shield-check" class="w-8 h-8 text-orange-400" />
          </div>
          <h2 class="text-xl font-semibold mb-2 text-white">两步验证</h2>
          <p class="text-sm text-stone-400">请输入验证器 App 中的 6 位验证码(也可使用恢复码)</p>
        </div>
        <input v-model="twoFaCode" type="text" inputmode="numeric" class="input text-center text-lg tracking-widest mb-3"
          placeholder="000000" maxlength="10" @keyup.enter="verifyTwoFa" />
        <button class="btn-primary w-full" :disabled="!twoFaCode.trim() || loading" @click="verifyTwoFa">
          {{ loading ? '验证中…' : '验证并登录' }}
        </button>
        <div class="mt-4 text-center">
          <button class="text-sm text-stone-400 hover:text-orange-400 transition-colors" @click="emailCode">
            无法使用验证器？发送邮箱验证码
          </button>
        </div>
        <div class="mt-2 text-center">
          <button class="text-sm text-stone-500 hover:text-white transition-colors"
            @click="pendingAction = null; pendingStatus = ''">返回登录</button>
        </div>
      </div>

      <!-- 管理员强制绑定 2FA -->
      <div v-else-if="pendingStatus === 'needs_2fa_setup'" class="card p-8
">
        <div class="text-center mb-4">
          <AppIcon name="shield-check" class="w-10 h-10 text-orange-400 mx-auto mb-2" />
          <h2 class="text-xl font-semibold mb-1 text-white">需要绑定两步验证</h2>
          <p class="text-sm text-stone-400">管理员已开启强制两步验证,请用验证器 App 扫码完成绑定后继续登录</p>
        </div>
        <div class="flex justify-center mb-3">
          <img v-if="twoFaQr" :src="twoFaQr" alt="TOTP 二维码" class="w-48 h-48 rounded-xl bg-white p-2" />
        </div>
        <p class="text-xs text-stone-500 text-center mb-3">
          密钥(手动添加用)：<code class="text-orange-300 select-all">{{ twoFaSecret }}</code>
        </p>
        <input v-model="twoFaCode" type="text" inputmode="numeric" class="input text-center text-lg tracking-widest mb-3"
          placeholder="输入验证码确认" maxlength="6" @keyup.enter="enableTwoFaChallenge" />
        <button class="btn-primary w-full" :disabled="!twoFaCode.trim() || loading" @click="enableTwoFaChallenge">
          {{ loading ? '验证中…' : '确认绑定并登录' }}
        </button>
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
import { useBrand } from '@/lib/brand'
const brand = useBrand()
import { ref, inject, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import QRCode from 'qrcode'
import AppIcon from '@/components/AppIcon.vue'
import api from '@/services/api'

const router = useRouter()
const route = useRoute()
const notify = inject('notify') as any
const logoUrl = ref('/Logo111.png')

const setupRequired = ref(false)
const checkSetup = async () => {
  try {
    const res: any = await fetch('/api/setup/status').then(r => r.json())
    setupRequired.value = !!res.setupRequired
  } catch { /* ignore */ }
}

onMounted(async () => {
  checkSetup()
  try { const res = await fetch('/api/config'); const data = await res.json(); if (data.success && data.data.portal?.logo) logoUrl.value = data.data.portal.logo } catch (e) {}
})

const loading = ref(false)
const showPassword = ref(false)
const form = ref({ username: '', password: '' })
const pendingAction = ref<string | null>(null)
const pendingStatus = ref('')
const questionnaireScore = ref(0)

// 2FA 中间态
const twoFaChallengeId = ref('')
const twoFaIsAdmin = ref(false)
const twoFaCode = ref('')
const twoFaQr = ref('')
const twoFaSecret = ref('')

const finishLogin = (data: any) => {
  api.setToken(data.token)
  localStorage.setItem('username', data.username)
  if (data.isAdmin) localStorage.setItem('isAdmin', 'true')
  else localStorage.removeItem('isAdmin')
  notify?.success('登录成功')
  const redirectPath = route.query.redirect as string
  if (redirectPath && redirectPath.startsWith('/') && !redirectPath.startsWith('//')) {
    router.push(redirectPath)
  } else {
    router.push('/dashboard')
  }
}

const handleLogin = async () => {
  loading.value = true
  try {
    const response: any = await api.login(form.value.username, form.value.password)
    if (response.success) {
      const data = response.data
      const status = data.status || 'approved'

      if (status === 'approved') {
        finishLogin(data)
      } else if (status === 'needs_2fa') {
        pendingStatus.value = 'needs_2fa'
        pendingAction.value = 'needs_2fa'
        twoFaChallengeId.value = data.challengeId || ''
        twoFaIsAdmin.value = !!data.isAdmin
        twoFaCode.value = ''
      } else if (status === 'needs_2fa_setup') {
        // 管理员强制绑定:拉取二维码信息后进入绑定卡片
        pendingStatus.value = 'needs_2fa_setup'
        pendingAction.value = 'needs_2fa_setup'
        twoFaChallengeId.value = data.challengeId || ''
        twoFaIsAdmin.value = !!data.isAdmin
        try {
          const setup: any = await api.setup2faChallenge(data.challengeId)
          if (setup.success) {
            twoFaSecret.value = setup.data.secret
            twoFaQr.value = await QRCode.toDataURL(setup.data.otpauth, { width: 240, margin: 1 })
          }
        } catch (e: any) {
          notify?.error(e.message || '初始化绑定失败')
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

const verifyTwoFa = async () => {
  loading.value = true
  try {
    const r: any = await api.verify2fa(twoFaChallengeId.value, twoFaCode.value.trim())
    if (r.success) {
      finishLogin(r.data)
    } else {
      notify?.error(r.message || '验证码错误')
    }
  } catch (error: any) {
    notify?.error(error.message || '验证失败')
  } finally {
    loading.value = false
  }
}

const emailCode = async () => {
  try {
    const r: any = await api.send2faEmailCode(twoFaChallengeId.value)
    if (r.success) notify?.success(r.message || '验证码已发送')
    else notify?.error(r.message || '发送失败')
  } catch (error: any) {
    notify?.error(error.message || '发送失败')
  }
}

const enableTwoFaChallenge = async () => {
  loading.value = true
  try {
    const r: any = await api.enable2faChallenge(twoFaChallengeId.value, twoFaCode.value.trim())
    if (r.success) {
      if (r.data?.recoveryCodes?.length) {
        // 强制绑定场景:恢复码仅此一次,先展示再进入(用阻塞 alert 保证玩家看到)
        alert('请立即保存一次性恢复码(关闭后无法再查看):\n\n' + r.data.recoveryCodes.join('\n'))
      }
      finishLogin(r.data)
    } else {
      notify?.error(r.message || '验证码错误')
    }
  } catch (error: any) {
    notify?.error(error.message || '验证失败')
  } finally {
    loading.value = false
  }
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
