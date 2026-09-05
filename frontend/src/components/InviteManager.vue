<template>
  <div class="card p-6">
    <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
      <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
      </svg>
      邀请管理
    </h3>

    <!-- 标签页 -->
    <div class="flex gap-2 mb-4 border-b border-stone-700 pb-2">
      <button
        @click="activeTab = 'generate'"
        class="px-3 py-1.5 text-sm rounded-lg transition-colors"
        :class="activeTab === 'generate' ? 'bg-orange-500/20 text-orange-400' : 'text-stone-400 hover:text-white'"
      >
        生成邀请码
      </button>
      <button
        @click="activeTab = 'codes'"
        class="px-3 py-1.5 text-sm rounded-lg transition-colors"
        :class="activeTab === 'codes' ? 'bg-orange-500/20 text-orange-400' : 'text-stone-400 hover:text-white'"
      >
        我的邀请码
      </button>
      <button
        @click="activeTab = 'pending'"
        class="px-3 py-1.5 text-sm rounded-lg transition-colors relative"
        :class="activeTab === 'pending' ? 'bg-orange-500/20 text-orange-400' : 'text-stone-400 hover:text-white'"
      >
        待确认邀请
        <span v-if="pendingCount > 0" class="absolute -top-1 -right-1 w-5 h-5 bg-red-500 rounded-full text-xs flex items-center justify-center">
          {{ pendingCount }}
        </span>
      </button>
    </div>

    <!-- 生成邀请码确认弹窗 -->
    <div v-if="showConfirmModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="card p-6 max-w-md w-full">
        <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          免审核邀请须知
        </h3>
        <div class="text-sm text-stone-300 space-y-3 mb-6">
          <p>老玩家一对一邀请可跳过问卷白名单审核，若受邀人出现服务器违规，邀请人可能需承担连带责任。</p>
          <p>请提前核验受邀人合规背景，仅账号被盗及时报备可免责。</p>
        </div>
        <div class="flex gap-3">
          <button @click="showConfirmModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="confirmGenerate" class="btn-primary flex-1" :disabled="generating">
            {{ generating ? '生成中...' : '确认生成' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 生成邀请码 -->
    <div v-if="activeTab === 'generate'">
      <p class="text-stone-400 text-sm mb-4">生成邀请码分享给新玩家，使用邀请码注册无需填写问卷。</p>
      <button @click="showConfirmModal = true" class="btn-primary" :disabled="generating">
        {{ generating ? '生成中...' : '生成邀请码' }}
      </button>
      <div v-if="newCode" class="mt-4 p-4 bg-stone-800/50 rounded-xl">
        <p class="text-stone-400 text-sm mb-2">你的邀请码：</p>
        <div class="flex items-center gap-2">
          <code class="text-xl font-mono text-orange-400">{{ newCode }}</code>
          <button @click="copyCode" class="text-stone-400 hover:text-white transition-colors">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
          </button>
        </div>
        <p class="text-stone-500 text-xs mt-2">分享链接: {{ inviteUrl }}</p>
      </div>
    </div>

    <!-- 我的邀请码列表 -->
    <div v-if="activeTab === 'codes'">
      <div v-if="codes.length === 0" class="text-center py-8 text-stone-500">
        暂无邀请码
      </div>
      <div v-else class="space-y-2">
        <div v-for="code in codes" :key="code.code" class="p-3 bg-stone-800/50 rounded-xl flex items-center justify-between">
          <div>
            <code class="font-mono text-orange-400">{{ code.code }}</code>
            <span class="ml-2 text-xs px-2 py-0.5 rounded-full" :class="getStatusClass(code.status)">
              {{ getStatusText(code.status) }}
            </span>
            <span v-if="code.inviteeUsername" class="ml-2 text-stone-500 text-xs">
              被邀请人: {{ code.inviteeUsername }}
            </span>
          </div>
          <span class="text-stone-500 text-xs">{{ formatDate(code.createdAt) }}</span>
        </div>
      </div>
    </div>

    <!-- 待确认邀请 -->
    <div v-if="activeTab === 'pending'">
      <div v-if="pendingInvitations.length === 0" class="text-center py-8 text-stone-500">
        暂无待确认的邀请
      </div>
      <div v-else class="space-y-3">
        <div v-for="invite in pendingInvitations" :key="invite.inviteeUsername" class="p-4 bg-stone-800/50 rounded-xl">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-white font-medium">{{ invite.inviteeUsername }}</p>
              <p class="text-stone-500 text-xs">使用你的邀请码注册</p>
            </div>
            <div class="flex gap-2">
              <button @click="confirmInvite(invite.inviteeUsername)" class="btn-primary text-sm px-3 py-1">
                确认
              </button>
              <button @click="rejectInvite(invite.inviteeUsername)" class="btn-secondary text-sm px-3 py-1">
                拒绝
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import api from '@/services/api'

const emit = defineEmits<{
  notify: [type: string, message: string]
}>()

const activeTab = ref<'generate' | 'codes' | 'pending'>('generate')
const generating = ref(false)
const showConfirmModal = ref(false)
const newCode = ref('')
const codes = ref<any[]>([])
const pendingInvitations = ref<any[]>([])

const pendingCount = computed(() => pendingInvitations.value.length)

const inviteUrl = computed(() => {
  if (!newCode.value) return ''
  return `${window.location.origin}/register?code=${newCode.value}`
})

const confirmGenerate = async () => {
  showConfirmModal.value = false
  await generateCode()
}

const generateCode = async () => {
  generating.value = true
  try {
    const res: any = await api.generateInviteCode()
    if (res.success) {
      newCode.value = res.data.code
      await loadCodes()
    }
  } catch (error: any) {
    emit('notify', 'error', error.message || '生成失败')
  } finally {
    generating.value = false
  }
}

const loadCodes = async () => {
  try {
    const res: any = await api.getMyInviteCodes()
    if (res.success) {
      codes.value = res.data || []
    }
  } catch (e) {}
}

const loadPending = async () => {
  try {
    const res: any = await api.getPendingInvitations()
    if (res.success) {
      pendingInvitations.value = res.data || []
    }
  } catch (e) {}
}

const confirmInvite = async (username: string) => {
  try {
    const res: any = await api.confirmInvitation(username)
    if (res.success) {
      emit('notify', 'success', '已确认邀请')
      await loadPending()
    }
  } catch (error: any) {
    emit('notify', 'error', error.message || '确认失败')
  }
}

const rejectInvite = async (username: string) => {
  try {
    const res: any = await api.rejectInvitation(username)
    if (res.success) {
      emit('notify', 'success', '已拒绝邀请')
      await loadPending()
    }
  } catch (error: any) {
    emit('notify', 'error', error.message || '拒绝失败')
  }
}

const copyCode = () => {
  navigator.clipboard.writeText(inviteUrl.value)
  emit('notify', 'success', '邀请链接已复制')
}

const getStatusClass = (status: string) => {
  const classes: Record<string, string> = {
    active: 'bg-emerald-500/20 text-emerald-400',
    used: 'bg-blue-500/20 text-blue-400',
    expired: 'bg-stone-500/20 text-stone-400'
  }
  return classes[status] || 'bg-stone-500/20 text-stone-400'
}

const getStatusText = (status: string) => {
  const texts: Record<string, string> = {
    active: '有效',
    used: '已使用',
    expired: '已过期'
  }
  return texts[status] || status
}

const formatDate = (timestamp: number) => {
  return new Date(timestamp).toLocaleDateString('zh-CN')
}

onMounted(async () => {
  await Promise.all([loadCodes(), loadPending()])
})
</script>
