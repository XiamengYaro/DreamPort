<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-6xl mx-auto">
      <!-- 标题卡片 -->
      <div class="card p-6 mb-6">
        <h1 class="text-2xl font-bold text-white">控制台</h1>
        <p class="text-stone-400">欢迎回来，{{ username }}</p>
      </div>

      <!-- 公告 -->
      <div v-if="announcement" class="card p-4 mb-6 border border-orange-500/30">
        <div class="flex items-center gap-3">
          <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5.882V19.24a1.76 1.76 0 01-3.417.592l-2.147-6.15M18 13a3 3 0 11-6 0 3 3 0 016 0zm6 2.5a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z" />
          </svg>
          <span class="text-stone-300">{{ announcement }}</span>
        </div>
      </div>

      <!-- 个人游戏数据 -->
      <div v-if="cmiEnabled" class="card p-6 mb-6">
        <h2 class="text-lg font-semibold text-white mb-4">我的游戏数据</h2>
        <div v-if="!playerData.name" class="text-stone-500 text-sm py-4 text-center">
          暂无服务器数据 —— 进入服务器后系统会自动采集(余额/时长/最近登录)
        </div>
        <template v-else>
        <!-- 在线状态(修复审计:原恒显"在线",改为按服务器心跳玩家列表判定) -->
        <div class="flex items-center gap-2 mb-4">
          <span class="w-2.5 h-2.5 rounded-full" :class="isOnline ? 'bg-green-400 animate-pulse' : 'bg-stone-500'"></span>
          <span class="text-sm font-medium" :class="isOnline ? 'text-green-400' : 'text-stone-400'">{{ isOnline ? '在线' : '离线' }}</span>
        </div>
        <div class="flex items-start gap-6">
          <!-- 左侧：头像 -->
          <div class="flex-shrink-0">
            <img :src="avatarUrl" alt="头像" class="w-24 h-24 rounded-2xl shadow-xl border-2 border-orange-500/30" @error="handleAvatarError" />
          </div>
          <!-- 右侧：信息 -->
          <div class="flex-1 min-w-0">
            <!-- 玩家名 -->
            <div class="text-2xl font-bold text-white mb-1">{{ username }}</div>
            <div class="h-px bg-gradient-to-r from-orange-500/50 to-transparent mb-4"></div>
            <!-- 数据行 -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
              <div class="flex items-center gap-3 p-3 rounded-xl bg-gradient-to-br from-orange-500/10 to-amber-500/10 border border-orange-500/20">
                <AppIcon name="banknote" class="w-6 h-6" />
                <div>
                  <div class="text-xl font-bold text-orange-400">{{ formatBalance(playerData.balance) }}</div>
                  <div class="text-xs text-stone-400">硬币</div>
                </div>
              </div>
              <div class="flex items-center gap-3 p-3 rounded-xl bg-gradient-to-br from-orange-500/10 to-amber-500/10 border border-orange-500/20">
                <AppIcon name="clock" class="w-6 h-6" />
                <div>
                  <div class="text-xl font-bold text-orange-400">{{ formatPlaytime(playerData.timePlayed) }}</div>
                  <div class="text-xs text-stone-400">在线时长</div>
                </div>
              </div>
            </div>
            <!-- 上次登录 -->
            <div class="flex items-center gap-2 text-stone-400 text-sm">
              <AppIcon name="calendar" class="w-4 h-4" />
              <span>上次登录：{{ formatLastLogin(playerData.lastLogin) }}</span>
            </div>
          </div>
        </div>
        </template>
      </div>

      <!-- 概览行：服务器状态 + 在线趋势 -->
      <div class="mb-6 grid gap-6 md:grid-cols-2">
        <div class="card p-6">
          <h2 class="text-lg font-semibold text-white mb-4">服务器状态</h2>
          <div class="grid grid-cols-2 gap-3">
            <StatCard :value="serverStatus.onlinePlayers || 0" label="在线玩家" />
            <StatCard :value="serverStatus.maxPlayers || 0" label="最大人数" />
            <StatCard :value="serverStatus.tps?.toFixed(1) || '0.0'" label="TPS" />
            <StatCard :value="serverStatus.minecraftVersion || '-'" label="版本" />
          </div>
        </div>
        <!-- 在线人数趋势图 -->
        <PlayerChart :days="7" />
      </div>


      <!-- 账号安全 -->
      <div class="card p-6 mb-6">
        <h2 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <AppIcon name="shield-check" class="w-5 h-5 text-emerald-400" />
          账号安全
        </h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-3 mb-3">
          <input v-model="pwdForm.oldPassword" type="password" class="input" placeholder="当前密码" autocomplete="current-password" />
          <input v-model="pwdForm.newPassword" type="password" class="input" placeholder="新密码(至少 8 位)" autocomplete="new-password" />
          <input v-model="pwdForm.confirm" type="password" class="input" placeholder="确认新密码" autocomplete="new-password" />
        </div>
        <div v-if="pwdMessage" class="p-3 rounded-xl mb-3 text-sm"
          :class="pwdSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'">
          {{ pwdMessage }}
        </div>
        <button @click="changeMyPassword" class="btn-primary text-sm"
          :disabled="!pwdForm.oldPassword || pwdForm.newPassword.length < 8 || pwdForm.newPassword !== pwdForm.confirm || pwdLoading">
          {{ pwdLoading ? '修改中...' : '修改密码' }}
        </button>
      </div>

      <!-- 账户绑定区（桌面双栏） -->
      <div class="mb-6 grid gap-6 lg:grid-cols-2">
        <!-- 基岩版 ID 管理 -->
      <div class="card p-6">
        <h2 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
          </svg>
          基岩版 ID 管理
        </h2>

        <!-- 已验证状态 -->
        <div v-if="bedrockStatus.verified" class="p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-xl">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <svg class="w-8 h-8 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              <div>
                <p class="text-emerald-400 font-medium">基岩版 ID 已验证</p>
                <p class="text-stone-400 text-sm">{{ bedrockStatus.name }} ({{ bedrockStatus.uuid }})</p>
              </div>
            </div>
            <button @click="cancelBedrock" class="btn-secondary text-sm" :disabled="bedrockLoading">
              取消验证
            </button>
          </div>
        </div>

        <!-- 待验证状态 -->
        <div v-else-if="bedrockStatus.name" class="space-y-4">
          <div class="p-4 bg-amber-500/10 border border-amber-500/30 rounded-xl">
            <p class="text-amber-400 font-medium">等待验证</p>
            <p class="text-stone-400 text-sm">基岩版 ID: {{ bedrockStatus.name }}</p>
            <p class="text-stone-500 text-xs mt-2">请使用基岩版登录服务器，然后点击下方验证按钮</p>
          </div>
          <div v-if="bedrockVerifyMessage" class="p-3 rounded-xl" :class="bedrockVerifySuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400'">
            {{ bedrockVerifyMessage }}
          </div>
          <div class="flex gap-2">
            <button @click="verifyBedrock" class="btn-primary" :disabled="bedrockLoading">
              {{ bedrockLoading ? '验证中...' : '验证基岩版 ID' }}
            </button>
            <button @click="cancelBedrock" class="btn-secondary" :disabled="bedrockLoading">
              取消验证
            </button>
          </div>
        </div>

        <!-- 未设置状态 -->
        <div v-else class="space-y-4">
          <p class="text-stone-400 text-sm">设置基岩版 ID 后，你可以使用基岩版登录服务器。</p>
          <div class="flex flex-wrap gap-2">
            <input v-model="bedrockNameInput" type="text" class="input flex-1" placeholder="输入基岩版用户名（不含前缀）" />
            <button @click="setBedrockId" class="btn-primary" :disabled="!bedrockNameInput || bedrockLoading">
              {{ bedrockLoading ? '设置中...' : '设置' }}
            </button>
          </div>
          <p class="text-stone-500 text-xs">系统会自动添加 "{{ bedrockPrefix }}" 前缀</p>
          <div v-if="bedrockMessage" class="p-3 rounded-xl" :class="bedrockSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'">
            {{ bedrockMessage }}
          </div>
        </div>
      </div>

      <!-- Minecraft ID 管理 -->
      <div class="card p-6 mb-6">
        <h2 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
          Minecraft 账户信息
        </h2>

        <!-- 已验证状态 -->
        <div v-if="minecraftStatus.verified" class="p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-xl">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-emerald-400 font-medium">Java 版 ID 已验证</p>
              <p class="text-stone-400 text-sm">{{ minecraftStatus.name }}</p>
              <p v-if="minecraftStatus.uuid" class="text-stone-500 text-xs font-mono mt-1">{{ minecraftStatus.uuid }}</p>
              <p class="text-stone-500 text-xs mt-1">验证时间: {{ formatTime(minecraftStatus.verifiedAt) }}</p>
            </div>
            <div class="flex flex-col gap-2">
              <button @click="showChangeIdModal = true" class="btn-secondary text-sm">修改 ID</button>
              <button v-if="minecraftStatus.uuid" @click="syncIdByUuid" class="btn-secondary text-sm" :disabled="syncLoading">
                {{ syncLoading ? '同步中...' : '按 UUID 同步新名' }}
              </button>
            </div>
          </div>
          <p v-if="syncMessage" class="mt-3 text-sm" :class="syncSuccess ? 'text-emerald-400' : 'text-amber-400'">{{ syncMessage }}</p>
        </div>

        <!-- 未设置/待验证状态 -->
        <div v-else class="space-y-4">
          <div v-if="minecraftStatus.name" class="p-4 bg-amber-500/10 border border-amber-500/30 rounded-xl">
            <p class="text-amber-400 font-medium">等待验证</p>
            <p class="text-stone-400 text-sm">Minecraft ID: {{ minecraftStatus.name }}</p>
            <p class="text-stone-500 text-xs mt-2">请登录服务器，然后点击验证按钮（验证有效期3分钟）</p>
          </div>
          <div v-else class="p-4 bg-stone-800/50 rounded-xl">
            <p class="text-stone-400 text-sm">未设置 Minecraft ID</p>
          </div>
          
          <div v-if="minecraftMessage" class="p-3 rounded-xl" :class="minecraftSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400'">
            {{ minecraftMessage }}
          </div>
          
          <div class="flex gap-2">
            <button @click="verifyMinecraft" class="btn-primary" :disabled="minecraftLoading">
              {{ minecraftLoading ? '验证中...' : '验证 ID' }}
            </button>
            <button @click="showChangeIdModal = true" class="btn-secondary">
              {{ minecraftStatus.name ? '修改 ID' : '设置 ID' }}
            </button>
          </div>
        </div>
      </div>

      <!-- QQ 绑定 -->
      <div class="card p-6">
        <h2 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-sky-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
          </svg>
          QQ 绑定
        </h2>

        <div v-if="qqStatus.bound" class="p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-xl">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-emerald-400 font-medium">已绑定 QQ：{{ qqStatus.qq }}</p>
              <p class="text-stone-500 text-xs mt-1">绑定时间: {{ formatTime(qqStatus.boundAt) }}</p>
            </div>
            <button @click="unbindQq" class="btn-secondary text-sm" :disabled="qqLoading">
              {{ qqLoading ? '处理中...' : '解绑' }}
            </button>
          </div>
        </div>

        <div v-else class="space-y-3">
          <p class="text-stone-400 text-sm">
            在 QQ 群内向机器人发送 <code class="text-orange-400">/dp绑定</code>，验证码将私聊发送给你（5 分钟内有效）；
            也可以在游戏内执行 <code class="text-orange-400">/xmw qq bind &lt;验证码&gt;</code> 完成绑定。
          </p>
          <div class="flex flex-wrap gap-2">
            <input v-model="qqCode" type="text" class="input flex-1" maxlength="6" placeholder="输入 6 位验证码" />
            <button @click="bindQq" class="btn-primary" :disabled="qqCode.trim().length !== 6 || qqLoading">
              {{ qqLoading ? '绑定中...' : '绑定' }}
            </button>
          </div>
          <div v-if="qqMessage" class="p-3 rounded-xl"
            :class="qqSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'">
            {{ qqMessage }}
          </div>
        </div>
      </div>

      <!-- 个人资料编辑 -->
      <div class="card p-6">
        <h2 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
          个人资料
        </h2>

        <div class="flex items-start gap-6">
          <!-- 头像 -->
          <div class="flex flex-col items-center gap-2">
            <div class="w-20 h-20 rounded-xl overflow-hidden">
              <AppAvatar :name="playerData?.name || username" size-class="w-20 h-20" alt="头像" />
            </div>
          </div>

          <!-- 信息 -->
          <div class="flex-1 space-y-4">
            <div>
              <label class="block text-sm text-stone-400 mb-1">用户名</label>
              <p class="text-white">{{ username }}</p>
            </div>
            <div>
              <label class="block text-sm text-stone-400 mb-1">邮箱</label>
              <div class="flex items-center gap-2">
                <p class="text-white">{{ userProfile.email }}</p>
                <button @click="showEmailModal = true" class="text-xs text-orange-400 hover:text-orange-300">修改</button>
              </div>
            </div>
          </div>
        </div>
      </div>
      </div>

      <!-- 邀请管理 -->
      <div class="mb-6">
        <InviteManager @notify="(type: string, msg: string) => notify?.[type](msg)" />
      </div>

      <!-- 修改 Minecraft ID 弹窗 -->
      <AppModal :open="showChangeIdModal" title="修改 Minecraft ID" @close="showChangeIdModal = false">
        <div class="space-y-4">
          <div v-if="minecraftStatus.name">
            <p class="text-sm text-stone-400">当前 ID: <span class="text-white">{{ minecraftStatus.name }}</span></p>
          </div>
          <div>
            <label class="block text-sm text-stone-300 mb-1">新的 Minecraft 用户名</label>
            <input v-model="newMinecraftName" type="text" class="input w-full" placeholder="输入新的 Minecraft 用户名" />
            <p class="mt-1 text-xs text-stone-500">修改后需要重新登录服务器验证（3分钟内有效）</p>
          </div>
          <div v-if="changeIdMessage" class="p-3 rounded-xl" :class="changeIdSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'">
            {{ changeIdMessage }}
          </div>
        </div>
        <template #footer>
          <button class="btn-secondary" @click="showChangeIdModal = false">取消</button>
          <button class="btn-primary" :disabled="!newMinecraftName || minecraftLoading" @click="setMinecraftId">
            {{ minecraftLoading ? '设置中...' : '确认修改' }}
          </button>
        </template>
      </AppModal>

      <!-- 修改邮箱弹窗 -->
      <AppModal :open="showEmailModal" title="修改邮箱" @close="showEmailModal = false">        <div class="space-y-4">
          <div>
            <label class="block text-sm text-stone-300 mb-1">新邮箱</label>
            <input v-model="newEmail" type="email" class="input w-full" placeholder="输入新邮箱" />
          </div>
          <div>
            <label class="block text-sm text-stone-300 mb-1">验证码</label>
            <div class="flex gap-2">
              <input v-model="emailVerifyCode" type="text" class="input flex-1" placeholder="输入验证码" />
              <button class="btn-secondary text-sm whitespace-nowrap" :disabled="emailCooldown > 0" @click="sendEmailVerifyCode">
                {{ emailCooldown > 0 ? `${emailCooldown}s` : '发送验证码' }}
              </button>
            </div>
          </div>
          <div v-if="emailMessage" class="rounded-xl p-3 text-sm" :class="emailSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'">
            {{ emailMessage }}
          </div>
        </div>
        <template #footer>
          <button class="btn-secondary" @click="showEmailModal = false">取消</button>
          <button class="btn-primary" :disabled="!newEmail || !emailVerifyCode || emailLoading" @click="updateEmail">
            {{ emailLoading ? '修改中...' : '确认修改' }}
          </button>
        </template>
      </AppModal>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, inject } from 'vue'
import api from '@/services/api'
import AppIcon from '@/components/AppIcon.vue'
import InviteManager from '@/components/InviteManager.vue'
import PlayerChart from '@/components/PlayerChart.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import StatCard from '@/components/ui/StatCard.vue'

const notify = inject('notify') as any

const username = ref(localStorage.getItem('username') || '')
const isAdmin = computed(() => localStorage.getItem('isAdmin') === 'true')
const announcement = ref('')
const cmiEnabled = ref(false)
const bedrockEnabled = ref(false)
const bedrockPrefix = ref('.')

const serverStatus = ref<any>({})
const userProfile = ref<any>({})
const playerData = ref<any>({})

// 按服务器心跳玩家列表判定当前账号是否在线(修复审计:原来有数据即显示"在线")
const isOnline = computed(() => {
  const name = String(playerData.value?.name || username.value || '').toLowerCase()
  if (!name) return false
  return (serverStatus.value?.servers || []).some((s: any) =>
    Array.isArray(s.players) && s.players.some((p: string) => String(p).toLowerCase() === name))
})
const bedrockStatus = ref<any>({ name: '', uuid: '', verified: false })
const bedrockNameInput = ref('')
const bedrockLoading = ref(false)
const bedrockMessage = ref('')
const bedrockSuccess = ref(false)
const bedrockVerifyMessage = ref('')
const bedrockVerifySuccess = ref(false)

// Minecraft ID 相关
const showChangeIdModal = ref(false)
const minecraftStatus = ref<any>({ name: '', uuid: '', verified: false, verifiedAt: 0 })
const newMinecraftName = ref('')
const minecraftLoading = ref(false)
const minecraftMessage = ref('')
const minecraftSuccess = ref(false)
const changeIdMessage = ref('')
const changeIdSuccess = ref(false)

// 按 UUID 同步新名(正版改名:UUID 恒定,向 Mojang 查询当前绑定名)
const syncLoading = ref(false)
const syncMessage = ref('')
const syncSuccess = ref(false)
const syncIdByUuid = async () => {
  syncLoading.value = true
  syncMessage.value = ''
  try {
    const r: any = await api.syncMinecraftByUuid()
    if (r.success) {
      const d = r.data || {}
      syncSuccess.value = true
      syncMessage.value = d.unchanged ? (r.message || 'ID 已是最新') : `已同步：${d.oldName || '旧名'} → ${d.newName}`
      if (!d.unchanged) {
        minecraftStatus.value.name = d.newName
        await loadMinecraftStatus()
      }
    } else {
      syncSuccess.value = false
      syncMessage.value = r.message || r.msg || '同步失败'
    }
  } catch (e: any) {
    syncSuccess.value = false
    syncMessage.value = e.message || '同步失败'
  } finally {
    syncLoading.value = false
  }
}

// 账号安全(修改密码)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })
const pwdLoading = ref(false)
const pwdMessage = ref('')
const pwdSuccess = ref(false)

const changeMyPassword = async () => {
  pwdLoading.value = true
  pwdMessage.value = ''
  try {
    const r: any = await api.changePassword({ oldPassword: pwdForm.value.oldPassword, newPassword: pwdForm.value.newPassword })
    if (r.success) {
      pwdSuccess.value = true
      pwdMessage.value = r.message || '密码已修改'
      pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' }
    } else {
      pwdSuccess.value = false
      pwdMessage.value = r.message || r.msg || '修改失败'
    }
  } catch (e: any) {
    pwdSuccess.value = false
    pwdMessage.value = e.message || '修改失败'
  }
  pwdLoading.value = false
}

// QQ 绑定
const qqStatus = ref<any>({ bound: false, qq: '', boundAt: 0 })
const qqCode = ref('')
const qqLoading = ref(false)
const qqMessage = ref('')
const qqSuccess = ref(false)

// 邮箱修改相关
const showEmailModal = ref(false)
const newEmail = ref('')
const emailVerifyCode = ref('')
const emailCooldown = ref(0)
const emailLoading = ref(false)
const emailMessage = ref('')
const emailSuccess = ref(false)

onMounted(async () => {
  await Promise.all([
    loadServerStatus(),
    loadUserProfile(),
    loadAnnouncement(),
    loadPlayerData(),
    loadBedrockStatus(),
    loadMinecraftStatus(),
    loadQqStatus()
  ])
})

const loadQqStatus = async () => {
  try {
    const r: any = await api.getQqBindStatus()
    if (r.success) qqStatus.value = r.data
  } catch (e) { console.error(e) }
}

const bindQq = async () => {
  qqLoading.value = true
  qqMessage.value = ''
  try {
    const r: any = await api.qqBind(qqCode.value.trim())
    if (r.success) {
      qqSuccess.value = true
      qqMessage.value = '绑定成功'
      qqCode.value = ''
      await loadQqStatus()
    } else {
      qqSuccess.value = false
      qqMessage.value = r.message || r.msg || '绑定失败'
    }
  } catch (e: any) {
    qqSuccess.value = false
    qqMessage.value = e.message || '绑定失败'
  } finally {
    qqLoading.value = false
  }
}

const unbindQq = async () => {
  qqLoading.value = true
  try {
    const r: any = await api.qqUnbind()
    if (r.success) {
      notify?.success('已解绑')
      await loadQqStatus()
    } else {
      notify?.error(r.message || r.msg || '解绑失败')
    }
  } catch (e: any) {
    notify?.error(e.message || '解绑失败')
  } finally {
    qqLoading.value = false
  }
}

const loadServerStatus = async () => {
  try {
    const r: any = await api.getServerStatus()
    if (r.success) serverStatus.value = r.data
  } catch (e) { console.error(e) }
}

const loadUserProfile = async () => {
  try {
    const r: any = await api.getUserProfile()
    if (r.success) userProfile.value = r.data
  } catch (e) { console.error(e) }
}

const loadAnnouncement = async () => {
  try {
    const r: any = await api.getConfig()
    if (r.success) announcement.value = r.data.announcement || ''
  } catch (e) { console.error(e) }
}

const loadPlayerData = async () => {
  // 先检查 CMI 是否启用
  try {
    const r: any = await api.getCmiStats()
    if (r.success || (r.data && r.data._diagnostics)) {
      cmiEnabled.value = true
    }
  } catch (e) {
    cmiEnabled.value = false
  }

  if (!cmiEnabled.value || !username.value) return

  // 加载玩家数据
  try {
    const r: any = await api.getCmiPlayer(username.value)
    if (r.success && r.data) {
      playerData.value = r.data
    }
  } catch (e) {
    console.error('Failed to load player data:', e)
  }
}

const formatPlaytime = (ms: number) => {
  if (!ms) return '0h'
  const hours = Math.floor(ms / 3600000)
  const minutes = Math.floor((ms % 3600000) / 60000)
  return `${hours}h ${minutes}m`
}
const formatBalance = (b: number) => b ? `$${b.toLocaleString()}` : '$0'
const formatLastLogin = (ts: number) => {
  if (!ts || ts === 0) return '从未'
  return new Date(ts).toLocaleString('zh-CN')
}
const formatTime = (timestamp: number) => {
  if (!timestamp || timestamp === 0) return '未知'
  return new Date(timestamp).toLocaleString('zh-CN')
}

// MC 头像
const avatarUrl = computed(() => {
  if (playerData.value?.minecraftUuid) {
    return `/api/avatar/${encodeURIComponent(playerData.value.name || username.value)}?size=100`
  }
  if (username.value) {
    return `/api/avatar/${encodeURIComponent(username.value)}?size=100`
  }
  return '/Logo111.png'
})
const handleAvatarError = (e: Event) => {
  (e.target as HTMLImageElement).src = '/Logo111.png'
}

// Minecraft ID 相关
const loadMinecraftStatus = async () => {
  try {
    const r: any = await api.getMinecraftStatus()
    if (r.success) {
      minecraftStatus.value = {
        name: r.data.minecraftName || '',
        uuid: r.data.minecraftUuid || '',
        verified: r.data.verified || false,
        verifiedAt: r.data.verifiedAt || 0
      }
    }
  } catch (e) {}
}

const setMinecraftId = async () => {
  if (!newMinecraftName.value) return
  minecraftLoading.value = true
  changeIdMessage.value = ''
  try {
    const r: any = await api.setMinecraftId(newMinecraftName.value)
    if (r.success) {
      const msg = r.data.message || 'Minecraft ID 已更新，请登录服务器验证'
      changeIdMessage.value = msg
      changeIdSuccess.value = true
      showChangeIdModal.value = false
      newMinecraftName.value = ''
      await loadMinecraftStatus()
    }
  } catch (e: any) {
    changeIdMessage.value = e.message || '设置失败'
    changeIdSuccess.value = false
  } finally {
    minecraftLoading.value = false
  }
}

const verifyMinecraft = async () => {
  minecraftLoading.value = true
  minecraftMessage.value = ''
  try {
    const r: any = await api.verifyMinecraft()
    if (r.success) {
      if (r.data.verified) {
        minecraftMessage.value = '验证成功！'
        minecraftSuccess.value = true
        await loadMinecraftStatus()
      } else {
        minecraftMessage.value = r.data.message || '未找到登录记录'
        minecraftSuccess.value = false
      }
    }
  } catch (e: any) {
    minecraftMessage.value = e.message || '验证失败'
    minecraftSuccess.value = false
  } finally {
    minecraftLoading.value = false
  }
}

// 基岩版相关
const loadBedrockStatus = async () => {
  try {
    const r: any = await api.getBedrockStatus()
    if (r.success) {
      bedrockEnabled.value = r.data.bedrockEnabled || false
      bedrockPrefix.value = r.data.bedrockPrefix || '.'
      bedrockStatus.value = {
        name: r.data.bedrockName || '',
        uuid: r.data.bedrockUuid || '',
        verified: r.data.bedrockVerified || false
      }
    }
  } catch (e) {}
}

const setBedrockId = async () => {
  if (!bedrockNameInput.value) return
  bedrockLoading.value = true
  bedrockMessage.value = ''
  try {
    const r: any = await api.setMyBedrockId(bedrockNameInput.value)
    if (r.success) {
      bedrockMessage.value = r.data.message || '基岩版 ID 已设置'
      bedrockSuccess.value = true
      await loadBedrockStatus()
      bedrockNameInput.value = ''
    }
  } catch (e: any) {
    bedrockMessage.value = e.message || '设置失败'
    bedrockSuccess.value = false
  } finally {
    bedrockLoading.value = false
  }
}

const verifyBedrock = async () => {
  bedrockLoading.value = true
  bedrockVerifyMessage.value = ''
  try {
    const r: any = await api.verifyMyBedrock()
    if (r.success) {
      if (r.data.verified) {
        bedrockVerifyMessage.value = '验证成功！'
        bedrockVerifySuccess.value = true
        await loadBedrockStatus()
      } else {
        bedrockVerifyMessage.value = r.data.message || '未找到登录记录'
        bedrockVerifySuccess.value = false
      }
    }
  } catch (e: any) {
    bedrockVerifyMessage.value = e.message || '验证失败'
    bedrockVerifySuccess.value = false
  } finally {
    bedrockLoading.value = false
  }
}

const cancelBedrock = async () => {
  if (!confirm('确定要取消基岩版验证吗？这将移除基岩版白名单。')) return
  bedrockLoading.value = true
  bedrockMessage.value = ''
  try {
    const r: any = await api.cancelMyBedrock()
    if (r.success) {
      bedrockMessage.value = '已取消验证'
      bedrockSuccess.value = true
      await loadBedrockStatus()
    }
  } catch (e: any) {
    bedrockMessage.value = e.message || '取消失败'
    bedrockSuccess.value = false
  } finally {
    bedrockLoading.value = false
  }
}

// 邮箱修改
const sendEmailVerifyCode = async () => {
  if (!newEmail.value) { notify?.error('请输入新邮箱'); return }
  try {
    await api.sendVerifyCode(newEmail.value)
    notify?.success('验证码已发送')
    emailCooldown.value = 60
    const timer = setInterval(() => {
      emailCooldown.value--
      if (emailCooldown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e: any) {
    notify?.error(e.message || '发送失败')
  }
}

const updateEmail = async () => {
  emailLoading.value = true
  emailMessage.value = ''
  try {
    const r: any = await api.updateEmail(newEmail.value, emailVerifyCode.value)
    if (r.success) {
      emailMessage.value = '邮箱修改成功'
      emailSuccess.value = true
      userProfile.value.email = newEmail.value
      showEmailModal.value = false
      newEmail.value = ''
      emailVerifyCode.value = ''
    }
  } catch (e: any) {
    emailMessage.value = e.message || '修改失败'
    emailSuccess.value = false
  } finally {
    emailLoading.value = false
  }
}
</script>
