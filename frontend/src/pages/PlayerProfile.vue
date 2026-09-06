<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-4xl mx-auto">
      <!-- 加载状态 -->
      <div v-if="loading" class="text-center py-12 text-stone-500">
        <svg class="animate-spin h-8 w-8 mx-auto mb-2 text-orange-400" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        加载中...
      </div>

      <!-- 玩家不存在 -->
      <div v-else-if="!profile" class="text-center py-12 text-stone-500">
        <AppIcon name="user" class="w-12 h-12 mx-auto mb-2 text-stone-500" />
        <div>玩家不存在</div>
        <router-link to="/players" class="text-orange-400 hover:text-orange-300 mt-4 inline-block">返回玩家列表</router-link>
      </div>

      <!-- 玩家资料 -->
      <template v-else>
        <!-- 头部信息 -->
        <div class="card p-6 mb-6">
          <div class="flex items-start gap-6">
            <img :src="`https://crafthead.net/avatar/${profile.uuid || profile.username}/128`" 
              :alt="profile.username"
              class="w-32 h-32 rounded-2xl border-4 border-orange-500/30 shadow-xl"
              @error="handleAvatarError" />
            <div class="flex-1">
              <h1 class="text-3xl font-bold text-white mb-2">{{ profile.username }}
                <span class="ml-2 align-middle text-sm px-2.5 py-1 rounded-lg align-middle"
                  :class="profile.status === 'banned' ? 'bg-rose-500/15 text-rose-400' : 'bg-emerald-500/15 text-emerald-400'">
                  {{ getStatusText(profile.status) }}
                </span>
              </h1>
              <div class="space-y-1 text-sm text-stone-400">
                <p>游戏 ID: <span class="text-stone-300 font-mono">{{ profile.minecraftName || profile.username }}</span></p>
                <p>UUID: <span class="text-stone-300 font-mono">{{ profile.uuid || '未绑定' }}</span></p>
                <p>首次加入: <span class="text-stone-300">{{ formatDate(profile.regTime) }}</span></p>
                <p>已陪伴服务器 <span class="text-orange-400 font-bold">{{ profile.daysSinceReg || 0 }}</span> 天</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 统计卡片 -->
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div class="card p-4 text-center">
            <div class="text-2xl font-bold text-orange-400">{{ formatPlaytime(profile.timePlayed) }}</div>
            <div class="text-xs text-stone-400 mt-1">总游戏时长</div>
          </div>
          <div class="card p-4 text-center">
            <div class="text-2xl font-bold text-orange-400">{{ profile.activeDaysLast30 || 0 }} 天</div>
            <div class="text-xs text-stone-400 mt-1">近30天活跃</div>
          </div>
          <div class="card p-4 text-center">
            <div class="text-2xl font-bold text-orange-400">{{ formatBalance(profile.balance) }}</div>
            <div class="text-xs text-stone-400 mt-1">硬币</div>
          </div>
          <div class="card p-4 text-center">
            <div class="text-2xl font-bold text-orange-400">{{ profile.loginCount || '-' }}</div>
            <div class="text-xs text-stone-400 mt-1">登录次数</div>
          </div>
        </div>

        <!-- 封禁提示 -->
        <div v-if="profile.status === 'banned'" class="card p-6 border-2 border-rose-500/30 bg-rose-500/5">
          <div class="flex items-start gap-3">
            <AppIcon name="x-circle" class="w-6 h-6 text-rose-400 shrink-0 mt-0.5" />
            <div>
              <div class="text-rose-400 font-semibold mb-1">该玩家已被封禁</div>
              <div class="text-sm text-stone-400">封禁原因：{{ profile.banReason || '未填写' }}</div>
            </div>
          </div>
        </div>

        <!-- 基础信息 -->
        <div class="card p-6">
          <h2 class="text-lg font-semibold text-white mb-4">基础信息</h2>
          <div class="space-y-3">
            <div class="flex justify-between py-2 border-b border-stone-800">
              <span class="text-stone-400">UUID</span>
              <span class="text-white font-mono text-sm">{{ profile.uuid }}</span>
            </div>
            <div v-if="profile.qqNumber" class="flex justify-between py-2 border-b border-stone-800">
              <span class="text-stone-400">绑定QQ</span>
              <span class="text-white">{{ profile.qqNumber }}</span>
            </div>
            <div class="flex justify-between py-2 border-b border-stone-800">
              <span class="text-stone-400">最后登出</span>
              <span class="text-white">{{ profile.lastLogin ? formatDateTime(profile.lastLogin) : '从未' }}</span>
            </div>
            <div class="flex justify-between py-2 border-b border-stone-800">
              <span class="text-stone-400">在线时长</span>
              <span class="text-white">{{ formatPlaytime(profile.timePlayed) }}</span>
            </div>
            <div class="flex justify-between py-2">
              <span class="text-stone-400">硬币</span>
              <span class="text-orange-400 font-bold">{{ formatBalance(profile.balance) }}</span>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'
import { getStatusText } from '@/lib/status'
import AppIcon from '@/components/AppIcon.vue'

const route = useRoute()
const loading = ref(false)
const profile = ref<any>(null)

onMounted(async () => {
  await loadProfile()
})

const loadProfile = async () => {
  loading.value = true
  const username = route.params.username as string
  try {
    const r: any = await api.getPlayerProfile(username)
    if (r.success) {
      profile.value = r.data
    }
  } catch (e) {
    console.error('Failed to load player profile:', e)
  }
  loading.value = false
}

const formatDate = (ts: number) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleDateString('zh-CN')
}

const formatDateTime = (ts: number) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN')
}

const formatPlaytime = (ms: number) => {
  if (!ms) return '0h'
  const hours = Math.floor(ms / 3600000)
  const minutes = Math.floor((ms % 3600000) / 60000)
  return `${hours}h ${minutes}m`
}

const formatBalance = (b: number) => {
  return b ? `$${b.toLocaleString()}` : '$0'
}

const handleAvatarError = (e: Event) => {
  const img = e.target as HTMLImageElement
  img.src = '/Logo111.png'
}
</script>
