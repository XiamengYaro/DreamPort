<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-6xl mx-auto">
      <SectionHeader kicker="LEADERBOARD" title="排行榜" subtitle="服务器财富榜、在线时间榜和活跃天数榜" />

      <!-- 统计卡片 -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        <StatCard :value="extendedStats.activePlayers || 0" label="活跃玩家" tone="orange" />
        <StatCard :value="extendedStats.chatCount || 0" label="全服聊天统计" tone="orange" />
      </div>

      <!-- Tab 按钮 -->
      <div class="flex gap-2 mb-6 flex-wrap">
        <button @click="activeTab = 'wealth'" class="tab-btn" :class="{ active: activeTab === 'wealth' }">
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          财富榜
        </button>
        <button @click="activeTab = 'playtime'" class="tab-btn" :class="{ active: activeTab === 'playtime' }">
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          在线时间榜
        </button>
        <button @click="activeTab = 'activedays'" class="tab-btn" :class="{ active: activeTab === 'activedays' }">
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
          活跃天数榜
        </button>
        <button v-if="isAdmin" @click="activeTab = 'banned'" class="tab-btn" :class="{ active: activeTab === 'banned' }">
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
          </svg>
          封禁榜
        </button>
      </div>

      <!-- 财富榜 -->
      <div v-if="activeTab === 'wealth'" class="card p-6">
        <h2 class="text-lg font-semibold mb-4 text-white flex items-center gap-2">
          <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          财富排行榜
        </h2>
        <AppSkeleton v-if="loading" variant="table" :rows="6" />
        <div v-else-if="error" class="text-center py-8 text-red-400">{{ error }}</div>
        <div v-else-if="wealthLeaderboard.length === 0" class="text-center py-8">
          <p class="text-stone-500 mb-4">暂无数据</p>
          <div v-if="diagnostics" class="text-xs text-stone-600 bg-stone-800/50 p-3 rounded-lg inline-block text-left">
            <p class="mb-1"><span class="text-stone-400">EssentialsX:</span> {{ diagnostics.essentialsEnabled ? '已启用' : '未启用' }}</p>
            <p class="mb-1"><span class="text-stone-400">Vault:</span> {{ diagnostics.vaultEnabled ? '已启用 (' + diagnostics.vaultProvider + ')' : '未启用' }}</p>
            <p><span class="text-stone-400">数据目录:</span> {{ diagnostics.essentialsDataDir }}</p>
          </div>
          <p v-if="diagnostics && !diagnostics.vaultEnabled && !diagnostics.essentialsEnabled" class="text-orange-400 text-sm mt-3">
            请在 config.yml 中设置 economy.enabled: true 或 essentials.enabled: true
          </p>
        </div>
        <div v-else class="space-y-2">
          <div v-for="(player, index) in wealthLeaderboard" :key="player.name"
            class="flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors">
            <div class="flex items-center gap-4">
              <span class="w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold"
                :class="index === 0 ? 'bg-yellow-400 text-yellow-900' : index === 1 ? 'bg-stone-300 text-stone-700' : index === 2 ? 'bg-amber-600 text-amber-100' : 'bg-stone-200 text-stone-600'">
                {{ index + 1 }}
              </span>
              <img :src="`/api/avatar/${encodeURIComponent(player.name)}?size=36`" :alt="player.name"
                class="w-9 h-9 rounded-xl object-cover shrink-0 bg-stone-800" loading="lazy" />
              <span class="text-white font-medium">{{ player.name }}</span>
            </div>
            <span class="text-orange-400 font-semibold text-lg">${{ formatNumber(player.balance) }}</span>
          </div>
        </div>
      </div>

      <!-- 在线时间榜 -->
      <div v-if="activeTab === 'playtime'" class="card p-6">
        <h2 class="text-lg font-semibold mb-4 text-white flex items-center gap-2">
          <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          在线时间排行榜
        </h2>
        <AppSkeleton v-if="loading" variant="table" :rows="6" />
        <div v-else-if="error" class="text-center py-8 text-red-400">{{ error }}</div>
        <div v-else-if="playtimeLeaderboard.length === 0" class="text-center py-8">
          <p class="text-stone-500 mb-4">暂无数据</p>
          <div v-if="diagnostics" class="text-xs text-stone-600 bg-stone-800/50 p-3 rounded-lg inline-block text-left">
            <p class="mb-1"><span class="text-stone-400">EssentialsX:</span> {{ diagnostics.essentialsEnabled ? '已启用' : '未启用' }}</p>
            <p><span class="text-stone-400">数据目录:</span> {{ diagnostics.essentialsDataDir }}</p>
          </div>
          <p v-if="diagnostics && diagnostics.essentialsDataDir === '不存在'" class="text-orange-400 text-sm mt-3">
            未找到 EssentialsX 数据目录，请确认 EssentialsX 已正确安装
          </p>
        </div>
        <div v-else class="space-y-2">
          <div v-for="(player, index) in playtimeLeaderboard" :key="player.name"
            class="flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors">
            <div class="flex items-center gap-4">
              <span class="w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold"
                :class="index === 0 ? 'bg-yellow-400 text-yellow-900' : index === 1 ? 'bg-stone-300 text-stone-700' : index === 2 ? 'bg-amber-600 text-amber-100' : 'bg-stone-200 text-stone-600'">
                {{ index + 1 }}
              </span>
              <img :src="`/api/avatar/${encodeURIComponent(player.name)}?size=36`" :alt="player.name"
                class="w-9 h-9 rounded-xl object-cover shrink-0 bg-stone-800" loading="lazy" />
              <span class="text-white font-medium">{{ player.name }}</span>
              <span v-if="onlinePlayers.includes(player.name)" class="w-2 h-2 bg-green-400 rounded-full" title="在线"></span>
              <span v-else class="w-2 h-2 bg-stone-500 rounded-full" title="离线"></span>
            </div>
            <span class="text-orange-400 font-semibold text-lg">{{ player.timePlayed }}h</span>
          </div>
        </div>
      </div>

      <!-- 活跃天数榜 -->
      <div v-if="activeTab === 'activedays'" class="card p-6">
        <h2 class="text-lg font-semibold mb-4 text-white flex items-center gap-2">
          <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
          活跃天数排行榜
        </h2>
        <AppSkeleton v-if="loading" variant="table" :rows="6" />
        <div v-else-if="activeDaysLeaderboard.length === 0" class="text-center py-8 text-stone-500">暂无数据</div>
        <div v-else class="space-y-2">
          <div v-for="(player, index) in activeDaysLeaderboard" :key="player.name"
            class="flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors">
            <div class="flex items-center gap-4">
              <span class="w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold"
                :class="index === 0 ? 'bg-yellow-400 text-yellow-900' : index === 1 ? 'bg-stone-300 text-stone-700' : index === 2 ? 'bg-amber-600 text-amber-100' : 'bg-stone-200 text-stone-600'">
                {{ index + 1 }}
              </span>
              <img :src="`/api/avatar/${encodeURIComponent(player.name)}?size=36`" :alt="player.name"
                class="w-9 h-9 rounded-xl object-cover shrink-0 bg-stone-800" loading="lazy" />
              <span class="text-white font-medium">{{ player.name }}</span>
            </div>
            <span class="text-orange-400 font-semibold text-lg">{{ player.activeDays }}天</span>
          </div>
        </div>
      </div>

      <!-- 封禁榜 -->
      <div v-if="activeTab === 'banned' && isAdmin" class="card p-6">
        <h2 class="text-lg font-semibold mb-4 text-white flex items-center gap-2">
          <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
          </svg>
          封禁玩家列表
        </h2>
        <AppSkeleton v-if="loading" variant="pulse" label="统计数据加载中..." />
        <div v-else-if="bannedPlayers.length === 0" class="text-center py-8 text-stone-500">暂无封禁玩家</div>
        <div v-else class="space-y-2">
          <div v-for="player in bannedPlayers" :key="player.name"
            class="flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors">
            <div class="flex items-center gap-3">
              <img :src="`/api/avatar/${encodeURIComponent(player.name)}?size=36`" :alt="player.name"
                class="w-9 h-9 rounded-xl object-cover shrink-0 bg-stone-800" loading="lazy" />
              <span class="text-white font-medium">{{ player.name }}</span>
            </div>
            <span class="text-stone-400 text-sm">{{ player.banReason || '无原因' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, inject } from 'vue'
import api from '@/services/api'
import StatCard from '@/components/ui/StatCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import SectionHeader from '@/components/SectionHeader.vue'

const notify = inject('notify') as any

const activeTab = ref('wealth')
const isAdmin = computed(() => localStorage.getItem('isAdmin') === 'true')

const wealthLeaderboard = ref<any[]>([])
const playtimeLeaderboard = ref<any[]>([])
const activeDaysLeaderboard = ref<any[]>([])
const bannedPlayers = ref<any[]>([])
const onlinePlayers = ref<string[]>([])

const loading = ref(false)
const error = ref<string | null>(null)
const diagnostics = ref<any>(null)
const extendedStats = ref<any>({})

onMounted(async () => {
  await loadLeaderboards()
  await loadOnlinePlayers()
})

const loadLeaderboards = async () => {
  loading.value = true
  error.value = null
  diagnostics.value = null
  
  // 获取扩展统计信息
  try {
    const extendedR: any = await api.getCmiExtendedStats()
    if (extendedR.success) {
      extendedStats.value = extendedR.data || {}
    }
  } catch (e) {
    console.error('Failed to load extended stats:', e)
  }
  
  // 先获取诊断信息
  try {
    const statsR: any = await api.getCmiStats()
    if (statsR.success && statsR.data._diagnostics) {
      diagnostics.value = statsR.data._diagnostics
    }
  } catch (e) {
    console.error('Failed to load diagnostics:', e)
  }
  
  try {
    const wealthR: any = await api.getCmiWealth(20)
    if (wealthR.success) {
      wealthLeaderboard.value = wealthR.data.list || []
    } else {
      error.value = wealthR.message || '无法加载财富排行榜数据'
    }
  } catch (e: any) {
    console.error('Failed to load wealth leaderboard:', e)
    error.value = e.message || '加载财富排行榜失败'
  }

  try {
    const playtimeR: any = await api.getCmiPlaytime(20)
    if (playtimeR.success) {
      playtimeLeaderboard.value = playtimeR.data.list || []
    } else {
      error.value = playtimeR.message || '无法加载在线时间排行榜数据'
    }
  } catch (e: any) {
    console.error('Failed to load playtime leaderboard:', e)
    error.value = e.message || '加载在线时间排行榜失败'
  }

  try {
    const activeDaysR: any = await api.getCmiActiveDays(20)
    if (activeDaysR.success) {
      activeDaysLeaderboard.value = activeDaysR.data.list || []
    }
  } catch (e) {
    console.error('Failed to load active days leaderboard:', e)
  }

  if (isAdmin.value) {
    try {
      const bannedR: any = await api.getCmiBanned()
      if (bannedR.success) {
        bannedPlayers.value = bannedR.data.list || []
      }
    } catch (e) {
      console.error('Failed to load banned players:', e)
    }
  }
  
  loading.value = false
}

const loadOnlinePlayers = async () => {
  try {
    const onlineR: any = await api.getCmiOnlinePlayers()
    if (onlineR.success) {
      onlinePlayers.value = onlineR.data.list || []
    }
  } catch (e) {
    console.error('Failed to load online players:', e)
  }
}

const formatNumber = (n: number) => n ? n.toLocaleString() : '0'
</script>
