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
          <div class="flex flex-col sm:flex-row items-start sm:items-start gap-4 sm:gap-6">
            <img :src="`/api/avatar/${encodeURIComponent(profile.username || profile.uuid)}?size=128`" 
              :alt="profile.username"
              class="w-32 h-32 rounded-2xl border-4 border-orange-500/30 shadow-xl"
              @error="handleAvatarError" />
            <div class="flex-1">
              <h1 class="text-3xl font-bold text-white mb-2">{{ profile.username }}
                <span class="ml-2 align-middle text-sm px-2.5 py-1 rounded-lg align-middle"
                  :class="profile.status === 'banned' ? 'bg-rose-500/15 text-rose-400' : 'bg-emerald-500/15 text-emerald-400'">
                  {{ getStatusText(profile.status) }}
                </span>
                <span v-if="profile.title" class="ml-2 align-middle text-sm px-2.5 py-1 rounded-lg font-semibold"
                  :style="{ color: profile.title.color, background: hexToRgba(profile.title.color, 0.12), border: '1px solid ' + hexToRgba(profile.title.color, 0.35) }">
                  {{ profile.title.name }}
                </span>
              </h1>
              <div class="space-y-1 text-sm text-stone-400">
                <p>游戏 ID: <span class="text-stone-300 font-mono">{{ profile.minecraftName || profile.username }}</span></p>
                <p>UUID: <span class="text-stone-300 font-mono break-all">{{ profile.uuid || '未绑定' }}</span></p>
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

        <!-- 称号与成就(仅本人可见;网页端佩戴,游戏内 PAPI 周期同步) -->
        <div ref="titlesSection" v-if="isSelf" class="card p-6 mb-6">
          <h2 class="text-lg font-semibold text-white mb-4">我的称号与成就</h2>
          <div v-if="titlesLoading" class="text-sm text-stone-500">加载中...</div>
          <template v-else>
            <div v-if="myTitles.owned.length" class="mb-5">
              <h3 class="text-xs text-stone-500 mb-2">已拥有 · 点击佩戴/脱下(游戏内约 1 分钟内同步)</h3>
              <div class="flex flex-wrap gap-2">
                <button v-for="t in myTitles.owned" :key="t.code" @click="toggleEquip(t.code)"
                  class="px-3 py-1.5 rounded-xl text-sm border transition"
                  :class="t.code === myTitles.equipped
                    ? 'border-orange-500 bg-orange-500/15 font-semibold'
                    : 'border-stone-700 bg-stone-800/50 hover:border-orange-400/60'">
                  <span :style="{ color: t.color }">{{ t.name }}</span>
                  <span v-if="t.code === myTitles.equipped" class="ml-1 text-orange-300">✓ 佩戴中</span>
                </button>
              </div>
            </div>
            <div v-if="myTitles.locked.length" class="mb-5">
              <h3 class="text-xs text-stone-500 mb-2">未解锁</h3>
              <div class="flex flex-wrap gap-2">
                <span v-for="t in myTitles.locked" :key="t.code"
                  class="px-3 py-1.5 rounded-xl text-sm border border-stone-800 bg-stone-900/40 text-stone-600 flex items-center gap-1.5">
                  <AppIcon name="no-symbol" class="w-3.5 h-3.5" />
                  <span :style="{ color: t.color, opacity: 0.55 }">{{ t.name }}</span>
                </span>
              </div>
            </div>
            <div v-if="myTitles.achievements.length">
              <h3 class="text-xs text-stone-500 mb-2">成就进度(达标自动授予)</h3>
              <div class="space-y-3">
                <div v-for="a in myTitles.achievements" :key="a.id">
                  <div class="flex justify-between text-sm mb-1">
                    <span class="text-stone-300">{{ a.name }}
                      <span v-if="a.rewardOwned" class="text-emerald-400 ml-1 text-xs">已获得</span>
                    </span>
                    <span class="text-stone-500">{{ a.completed ? '已完成 ✓' : `${a.progress} / ${a.target}` }}</span>
                  </div>
                  <div class="h-1.5 rounded-full bg-stone-800 overflow-hidden">
                    <div class="h-full rounded-full transition-all"
                      :class="a.completed ? 'bg-emerald-500' : 'bg-orange-400'"
                      :style="{ width: Math.min(100, Math.round((a.progress / a.target) * 100)) + '%' }"></div>
                  </div>
                </div>
              </div>
            </div>
            <p v-if="!myTitles.owned.length && !myTitles.locked.length && !myTitles.achievements.length" class="text-sm text-stone-500">
              暂无称号与成就,达成成就或由管理员授予后即可在这里佩戴。
            </p>
          </template>
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
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'
import { getStatusText } from '@/lib/status'
import AppIcon from '@/components/AppIcon.vue'

const route = useRoute()
const loading = ref(false)
const profile = ref<any>(null)
const isSelf = ref(false)
const titlesLoading = ref(false)
const titlesSection = ref<HTMLElement | null>(null)
const myTitles = ref<any>({ owned: [], locked: [], equipped: null, achievements: [] })

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
      const me = localStorage.getItem('username')
      isSelf.value = !!me && me.toLowerCase() === String(r.data.username).toLowerCase()
      if (isSelf.value) {
        await loadMyTitles()
        // 导航「我的称号」入口带 ?titles=1,直达称号面板
        if (route.query.titles) {
          await nextTick()
          titlesSection.value?.scrollIntoView({ behavior: 'smooth' })
        }
      }
    }
  } catch (e) {
    console.error('Failed to load player profile:', e)
  }
  loading.value = false
}

const loadMyTitles = async () => {
  titlesLoading.value = true
  try {
    const r: any = await api.getMyTitles()
    if (r.success) myTitles.value = r.data
  } catch (e) {
    console.error('Failed to load titles:', e)
  }
  titlesLoading.value = false
}

const toggleEquip = async (code: string) => {
  try {
    const r: any = myTitles.value.equipped === code
      ? await api.unequipTitle()
      : await api.equipTitle(code)
    if (r.success) await loadMyTitles()
  } catch (e: any) {
    console.error('Failed to toggle title:', e)
  }
}

const hexToRgba = (hex: string, alpha: number) => {
  if (!/^#[0-9a-fA-F]{6}$/.test(hex)) return undefined
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
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
