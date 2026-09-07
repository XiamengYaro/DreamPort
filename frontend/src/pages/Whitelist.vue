<template>
  <div class="min-h-screen flex items-center justify-center p-4">
    <div class="w-full max-w-lg text-center">
      <div class="mb-6">
        <img :src="logoUrl" alt="Logo" class="w-24 h-24 rounded-3xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
      </div>

      <h1 class="text-4xl font-bold text-white mb-2 tracking-tight">
        {{ serverName }}
      </h1>
      <p class="text-xl text-stone-400 mb-6">白名单管理系统</p>

      <div class="card p-6 mb-6 text-left">
        <!-- Wiki 按钮 -->
        <div class="mb-5 text-center">
          <a @click.prevent="goToDocs" href="#"
            class="inline-flex items-center gap-2 px-5 py-3 rounded-xl bg-orange-500/15 border border-orange-500/30 text-orange-400 hover:bg-orange-500/25 transition-all duration-200 group cursor-pointer">
            <svg class="w-5 h-5 group-hover:scale-110 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
            </svg>
            <span class="font-medium">阅读服务器公约 Wiki</span>
          </a>
        </div>

        <!-- 说明文字 -->
        <div class="space-y-3 text-sm text-stone-400 leading-relaxed">
          <p>请前往服务器文档阅读服务器公约，阅读完后进行账号注册。</p>
          <p>请认真完成注册与审核流程，自觉遵守服务器各项规则。我们致力于维护有序、文明的游玩环境，禁止恶意破坏、违规作弊等行为。期待守规的你加入我们的大家庭。</p>
          <p class="text-orange-400/80">注册完成后将进入问卷入服审核流程，请认真作答，完成作答后，将由 AI 进行自动审核。</p>
        </div>
      </div>

      <!-- 邀请码输入弹窗 -->
      <div v-if="showInviteModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
        <div class="card p-6 max-w-md w-full">
          <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
            </svg>
            输入邀请码
          </h3>
          <p class="text-stone-400 text-sm mb-4">请输入服内玩家提供的邀请码，使用邀请码可免填写问卷。</p>
          <input 
            v-model="inviteCode" 
            type="text" 
            class="input w-full mb-4" 
            placeholder="请输入邀请码"
            @keyup.enter="submitInviteCode"
          />
          <div class="flex gap-3">
            <button @click="showInviteModal = false" class="btn-secondary flex-1">取消</button>
            <button @click="submitInviteCode" class="btn-primary flex-1" :disabled="!inviteCode || submitting">
              {{ submitting ? '提交中...' : '提交' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 状态1：已过审 - 显示已过审信息 -->
      <div v-if="userStatus === 'approved'" class="card p-8
">
        <div class="text-center">
          <AppIcon name="check-circle" class="w-14 h-14 mx-auto mb-4 text-green-400" />
          <h2 class="text-2xl font-bold text-emerald-400 mb-2">白名单已通过</h2>
          <p class="text-stone-400">你已经拥有白名单权限，可以直接登录服务器游戏。</p>
          <router-link to="/dashboard" class="btn-primary mt-6 inline-block">进入控制台</router-link>
        </div>
      </div>

      <!-- 状态2：未过审 - 选择入服方式 -->
      <div v-else-if="userStatus === 'pending'" class="card p-8
">
        <div class="text-center mb-6">
          <div class="text-5xl mb-4">
            <svg class="w-12 h-12 text-amber-400 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h2 class="text-2xl font-bold text-white mb-2">选择入服方式</h2>
          <p class="text-stone-400">请选择一种方式完成白名单申请</p>
        </div>
        
        <div class="grid grid-cols-1 gap-4">
          <!-- 方式1：服内玩家邀请 -->
          <button @click="showInviteModal = true" class="p-6 rounded-xl bg-gradient-to-br from-blue-500/10 to-cyan-500/10 border border-blue-500/30 hover:border-blue-500/50 transition-all text-left group">
            <div class="flex items-center gap-4">
              <div class="w-12 h-12 rounded-xl bg-blue-500/20 flex items-center justify-center group-hover:scale-110 transition-transform">
                <svg class="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                </svg>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-white">服内玩家邀请</h3>
                <p class="text-sm text-stone-400">已有服内玩家为你提供邀请码，可免填写问卷</p>
              </div>
            </div>
          </button>
          
          <!-- 方式2：问卷入服申请 -->
          <button @click="goToQuestionnaire" class="p-6 rounded-xl bg-gradient-to-br from-orange-500/10 to-amber-500/10 border border-orange-500/30 hover:border-orange-500/50 transition-all text-left group">
            <div class="flex items-center gap-4">
              <div class="w-12 h-12 rounded-xl bg-orange-500/20 flex items-center justify-center group-hover:scale-110 transition-transform">
                <svg class="w-6 h-6 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-white">问卷入服申请</h3>
                <p class="text-sm text-stone-400">完成入服审核问卷，AI 将自动审核你的回答</p>
              </div>
            </div>
          </button>
        </div>
      </div>

      <!-- 状态3：等待审核 -->
      <div v-else-if="userStatus === 'pending_review'" class="card p-8
">
        <div class="text-center">
          <div class="text-5xl mb-4">
            <svg class="w-12 h-12 text-amber-400 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h2 class="text-2xl font-bold text-amber-400 mb-2">审核中</h2>
          <p class="text-stone-400">你的申请正在审核中，请耐心等待。</p>
          <router-link to="/dashboard" class="btn-primary mt-6 inline-block">返回控制台</router-link>
        </div>
      </div>

      <!-- 状态4：邀请待确认 -->
      <div v-else-if="userStatus === 'invited_pending'" class="card p-8
">
        <div class="text-center">
          <div class="text-5xl mb-4">
            <svg class="w-12 h-12 text-blue-400 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
          </div>
          <h2 class="text-2xl font-bold text-blue-400 mb-2">等待邀请人确认</h2>
          <p class="text-stone-400">你已通过邀请码申请，请等待邀请人确认。</p>
          <router-link to="/dashboard" class="btn-primary mt-6 inline-block">返回控制台</router-link>
        </div>
      </div>

      <!-- 状态5：待验证 - 可选择问卷或 ID 验证 -->
      <div v-else-if="userStatus === 'pending_verify'" class="card p-8">
        <div class="text-center">
          <div class="text-5xl mb-4">
            <svg class="w-12 h-12 text-cyan-400 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
          </div>
          <h2 class="text-2xl font-bold text-cyan-400 mb-2">完成入服流程</h2>
          <p class="text-stone-400 mb-6">你可以先完成问卷，再进行 ID 验证。</p>
          <div class="flex flex-col sm:flex-row gap-3 justify-center">
            <router-link to="/questionnaire" class="btn-primary">开始问卷</router-link>
            <router-link to="/verify" class="btn-secondary">先去 ID 验证</router-link>
          </div>
        </div>
      </div>

      <!-- 状态6：被拒绝 - 显示重新答题 -->
      <div v-else-if="userStatus === 'rejected'" class="card p-8
">
        <div class="text-center">
          <div class="text-5xl mb-4">
            <svg class="w-12 h-12 text-rose-400 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          </div>
          <h2 class="text-2xl font-bold text-rose-400 mb-2">问卷未通过</h2>
          <p class="text-stone-400">你的问卷得分 {{ userScore }} 分，未达到通过标准。</p>
          <div class="flex gap-4 justify-center mt-6">
            <button @click="goToQuestionnaire" class="btn-primary">重新答题</button>
            <router-link to="/dashboard" class="btn-secondary">返回控制台</router-link>
          </div>

          <!-- 申诉区 -->
          <div class="mt-8 pt-6 border-t border-stone-700 text-left">
            <h3 class="text-white font-semibold mb-3">申诉</h3>
            <div v-if="appeal && appeal.status === 'pending'" class="p-4 bg-amber-500/10 border border-amber-500/30 rounded-xl">
              <p class="text-amber-400 text-sm font-medium">申诉处理中</p>
              <p class="text-stone-400 text-xs mt-1">你于 {{ formatAppealDate(appeal.createdAt) }} 提交的申诉正在等待管理员处理</p>
            </div>
            <div v-else-if="appeal && appeal.status === 'approved'" class="p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-xl">
              <p class="text-emerald-400 text-sm font-medium">申诉已通过,账号进入人工复核队列</p>
            </div>
            <div v-else-if="appeal && appeal.status === 'rejected'" class="p-4 bg-rose-500/10 border border-rose-500/30 rounded-xl">
              <p class="text-rose-400 text-sm font-medium">申诉未通过</p>
              <p v-if="appeal.adminReply" class="text-stone-300 text-sm mt-1">管理员回复:{{ appeal.adminReply }}</p>
            </div>
            <div v-else-if="isLoggedIn" class="space-y-3">
              <p class="text-stone-400 text-sm">对评分结果有异议?可以提交申诉,管理员会人工复核。</p>
              <textarea v-model="appealReason" class="input w-full" rows="3" maxlength="500"
                placeholder="填写申诉理由(至少 10 字)"></textarea>
              <div class="flex justify-end">
                <button @click="submitMyAppeal" class="btn-secondary text-sm"
                  :disabled="appealReason.trim().length < 10 || appealPosting">
                  {{ appealPosting ? '提交中...' : '提交申诉' }}
                </button>
              </div>
            </div>
            <p v-else class="text-stone-500 text-xs mt-2">
              <router-link to="/login" class="text-orange-400 hover:text-orange-300">登录</router-link> 后可以提交申诉
            </p>
          </div>
        </div>
      </div>

      <!-- 状态7：封禁中 -->
      <div v-else-if="userStatus === 'banned'" class="card p-8">
        <div class="text-center">
          <AppIcon name="no-symbol" class="w-14 h-14 mx-auto mb-4 text-rose-400" />
          <h2 class="text-2xl font-bold text-rose-400 mb-2">账号处于封禁状态</h2>
          <p class="text-stone-400">临时封禁到期后会自动解封并通知你；具体可在封禁名单页查看。</p>
          <div class="flex gap-4 justify-center mt-6">
            <router-link to="/bans" class="btn-secondary">查看封禁名单</router-link>
            <router-link to="/dashboard" class="btn-primary">返回控制台</router-link>
          </div>
        </div>
      </div>

      <!-- 状态8：未登录 - 显示注册/登录 -->
      <div v-else class="flex gap-4 justify-center">
        <router-link to="/register" class="btn-primary">注册 {{ brand.short }} 账号</router-link>
        <router-link to="/login" class="btn-secondary">已有账号？登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/services/api'
import AppIcon from '@/components/AppIcon.vue'
import { useBrand } from '@/lib/brand'
const brand = useBrand()

const router = useRouter()
const config = inject('config') as any
const notify = inject('notify') as any

const logoUrl = ref('/Logo111.png')
const serverName = ref('夏日小镇')
const wikiUrl = ref('https://wiki.xmcraft.cn')

const isLoggedIn = computed(() => !!localStorage.getItem('token'))
const username = ref('')
const userStatus = ref('')
const userScore = ref(0)

// 邀请码弹窗相关
const showInviteModal = ref(false)
const inviteCode = ref('')
const submitting = ref(false)

onMounted(async () => {
  await loadMyAppeal()
  // 加载配置
  try {
    const res = await fetch('/api/config')
    const data = await res.json()
    if (data.success && data.data.portal) {
      serverName.value = data.data.portal.server_name || '夏日小镇'
      wikiUrl.value = data.data.portal.social?.wiki || 'https://wiki.xmcraft.cn'
    }
    if (data.success && data.data.portal?.logo) {
      logoUrl.value = data.data.portal.logo
    }
  } catch (e) {}

  // 检查登录状态
  if (isLoggedIn.value) {
    username.value = localStorage.getItem('username') || ''
    try {
      const data: any = await api.getUserStatus()
      if (data.success) {
        userStatus.value = data.data.status || ''
      }
    } catch (e) {
      console.error('Failed to check status:', e)
    }
  }
})

const goToQuestionnaire = () => {
  localStorage.setItem('pendingUsername', username.value)
  router.push('/questionnaire')
}

const submitInviteCode = async () => {
  if (!inviteCode.value || submitting.value) return
  
  submitting.value = true
  try {
    const res: any = await fetch('/api/invite/apply', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify({ inviteCode: inviteCode.value })
    })
    const data = await res.json()
    
    if (data.success) {
      notify?.success('邀请码提交成功，已通知邀请人确认')
      showInviteModal.value = false
      userStatus.value = 'invited_pending'
    } else {
      notify?.error(data.message || '邀请码无效')
    }
  } catch (error: any) {
    notify?.error(error.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

const goToDocs = () => {
  router.push('/docs')
}

// 申诉(批次 A 补全:模板引用但脚本缺失,曾导致 onMounted 抛错、状态加载中断)
const appeal = ref<any>(null)
const appealReason = ref('')
const appealPosting = ref(false)

const loadMyAppeal = async () => {
  if (!isLoggedIn.value) return
  try {
    const r: any = await api.getMyAppeal()
    if (r.success && r.data?.found) appeal.value = r.data
  } catch (e) { console.error('Failed to load appeal:', e) }
}

const submitMyAppeal = async () => {
  if (appealReason.value.trim().length < 10 || appealPosting.value) return
  appealPosting.value = true
  try {
    const r: any = await api.submitAppeal(appealReason.value.trim())
    if (r.success) {
      notify?.success('申诉已提交，等待管理员处理')
      appealReason.value = ''
      await loadMyAppeal()
    } else {
      notify?.error(r.message || r.msg || '提交失败')
    }
  } catch (e: any) {
    notify?.error(e.message || '提交失败')
  } finally {
    appealPosting.value = false
  }
}

const formatAppealDate = (ts: number) => (ts ? new Date(ts).toLocaleString('zh-CN') : '-')
</script>
