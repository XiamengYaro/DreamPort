<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-6xl mx-auto">
      <!-- 标题 -->
      <div class="card p-6 mb-6">
        <h1 class="text-2xl font-bold text-white">玩家目录</h1>
        <p class="text-stone-400">浏览服务器所有注册玩家</p>
      </div>

      <!-- 搜索框 -->
      <div class="mb-6">
        <div class="relative">
          <input v-model="searchQuery" type="text" class="input w-full pl-10" placeholder="搜索玩家名..." />
          <svg class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-stone-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="text-center py-12 text-stone-500">
        <svg class="animate-spin h-8 w-8 mx-auto mb-2 text-orange-400" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        加载中...
      </div>

      <!-- 空状态 -->
      <div v-else-if="filteredPlayers.length === 0" class="text-center py-12 text-stone-500">
        <AppIcon name="users" class="w-12 h-12 mx-auto mb-2 text-stone-500" />
        <div>{{ searchQuery ? '未找到匹配的玩家' : '暂无玩家数据' }}</div>
      </div>

      <!-- 玩家网格 -->
      <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <router-link v-for="player in filteredPlayers" :key="player.username"
          :to="`/player/${player.username}`"
          class="card p-4 text-center hover:bg-white/10 transition-colors duration-200 cursor-pointer group">
          <img :src="`/api/avatar/${encodeURIComponent(player.username || player.uuid)}?size=100`" 
            :alt="player.username"
            class="w-16 h-16 rounded-xl mx-auto mb-3 border-2 border-stone-700 group-hover:border-orange-500/50 transition-colors"
            @error="handleAvatarError" />
          <div class="text-white font-medium text-sm truncate">{{ player.username }}</div>
          <div class="text-xs text-stone-500 mt-1">
            <span :class="statusColorClass(player.status)">{{ getStatusText(player.status) }}</span>
          </div>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/services/api'
import { getStatusText } from '@/lib/status'

const statusColorClass = (status: string) => ({
  approved: 'text-green-400',
  pending: 'text-yellow-400',
  pending_review: 'text-sky-400',
  rejected: 'text-rose-400',
  banned: 'text-rose-400 font-medium'
}[status] || 'text-stone-500')
import AppIcon from '@/components/AppIcon.vue'

const loading = ref(false)
const players = ref<any[]>([])
const searchQuery = ref('')

const filteredPlayers = computed(() => {
  if (!searchQuery.value) return players.value
  const query = searchQuery.value.toLowerCase()
  return players.value.filter(p => 
    p.username.toLowerCase().includes(query)
  )
})

onMounted(async () => {
  await loadPlayers()
})

const loadPlayers = async () => {
  loading.value = true
  try {
    const r: any = await api.getPlayerList()
    if (r.success) {
      players.value = r.data.list || []
    }
  } catch (e) {
    console.error('Failed to load players:', e)
  }
  loading.value = false
}

const handleAvatarError = (e: Event) => {
  const img = e.target as HTMLImageElement
  img.src = '/Logo111.png'
}
</script>
