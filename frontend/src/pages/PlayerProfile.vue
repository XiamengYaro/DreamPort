<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-4xl mx-auto">
      <!-- 加载状态 -->
      <AppSkeleton v-if="loading" variant="cards" :rows="2" card-height="160" grid-class="grid-cols-1 md:grid-cols-2" />

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
            <img :src="`/api/avatar/${encodeURIComponent(profile.minecraftName || profile.username || profile.uuid)}?size=128`"
              :alt="profile.minecraftName || profile.username"
              class="w-32 h-32 rounded-2xl border-4 border-orange-500/30 shadow-xl"
              @error="handleAvatarError" />
            <div class="flex-1">
              <h1 class="text-3xl font-bold text-white mb-2">{{ profile.username }}
                <button v-if="friendState === 'none' && isLoggedIn" @click="addFriend"
                  class="ml-2 align-middle text-xs px-3 py-1.5 rounded-lg bg-orange-500/15 text-orange-400 border border-orange-500/30 hover:bg-orange-500/25 transition-colors">+ 好友</button>
                <span v-else-if="friendState === 'pending'" class="ml-2 align-middle text-xs px-3 py-1.5 rounded-lg bg-stone-600/40 text-stone-300">申请已发送</span>
                <span v-else-if="friendState === 'friends'" class="ml-2 align-middle text-xs px-3 py-1.5 rounded-lg bg-emerald-500/15 text-emerald-400">好友</span>
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
        <div class="grid grid-cols-2 md:grid-cols-5 gap-4 mb-6">
          <StatCard :value="formatPlaytime(profile.timePlayed)" label="总游戏时长" tone="orange" />
          <StatCard :value="(profile.activeDaysLast30 || 0) + ' 天'" label="近30天活跃" tone="orange" />
          <StatCard :value="formatBalance(profile.balance)" label="硬币" tone="orange" />
          <StatCard :value="profile.loginCount || '-'" label="登录次数" tone="orange" />
          <StatCard v-if="myScore != null" :value="myScore.toFixed(1)" label="综合评分" tone="emerald" />
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
import { ref, computed, onMounted, nextTick, inject } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'
import { getStatusText } from '@/lib/status'
import AppIcon from '@/components/AppIcon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'

const route = useRoute()
const notify = inject('notify') as any
const loading = ref(false)
const profile = ref<any>(null)
const isLoggedIn = computed(() => !!localStorage.getItem('token'))
const isSelf = ref(false)

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
      if (profile.value?.profileCustom) {
        profileCustom.value = profile.value.profileCustom
      }
      await loadScore()
      await loadFriendState()
      if (isSelf.value) {
      }
    }
  } catch (e) {
    console.error('Failed to load player profile:', e)
  }
  loading.value = false
}

const myScore = ref<number | null>(null)
const friendState = ref('')
const profileCustom = ref<any>({ bio: '', banner: 'amber', socialLinks: [] as any[] })
const customEdit = ref(false)
const customForm = ref<any>({ bio: '', banner: 'amber', socialLinks: [] as any[] })
const customSaving = ref(false)

const loadScore = async () => {
  try {
    if (isSelf.value) {
      const r: any = await api.getMyScore()
      if (r.success) myScore.value = r.data?.score ?? null
    } else {
      const r: any = await api.getScoreLeaderboard()
      if (r.success) {
        const row = (r.data?.list || []).find((x: any) => x.username === profile.value?.username)
        myScore.value = row ? row.score : null
      }
    }
  } catch { /* 未公开/未登录:不显示 */ }
}

const startCustomEdit = () => {
  customForm.value = {
    bio: profileCustom.value.bio || '',
    banner: profileCustom.value.banner || 'amber',
    socialLinks: (profileCustom.value.socialLinks || []).map((x: any) => ({ ...x }))
  }
  customEdit.value = true
}

const saveCustom = async () => {
  customSaving.value = true
  try {
    const links = (customForm.value.socialLinks || []).filter((x: any) => (x.url || '').trim())
    const r: any = await api.saveProfileCustom({
      bio: customForm.value.bio || '',
      banner: customForm.value.banner || 'amber',
      socialLinks: links.map((x: any) => ({ label: x.label || '链接', url: x.url.trim() }))
    })
    if (r.success) {
      notify?.success(r.message || '已保存')
      customEdit.value = false
      profileCustom.value = { bio: customForm.value.bio, banner: customForm.value.banner, socialLinks: links }
    } else notify?.error(r.message || '保存失败')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
  customSaving.value = false
}

const addLinkRow = () => {
  if ((customForm.value.socialLinks || []).length >= 5) return
  customForm.value.socialLinks.push({ label: '', url: '' })
}

const loadFriendState = async () => {
  if (!isLoggedIn.value || !profile.value?.username) return
  try {
    const r: any = await api.getFriendStatus(profile.value.username)
    if (r.success) friendState.value = r.data?.state || 'none'
  } catch { /* ignore */ }
}

const addFriend = async () => {
  try {
    const r: any = await api.requestFriend(profile.value.username)
    if (r.success) { notify?.success(r.message || '申请已发送'); friendState.value = 'pending' } else notify?.error(r.message || '发送失败')
  } catch (e: any) { notify?.error(e.message || '发送失败') }
}


const BANNERS: Record<string, string> = {
  amber: 'from-amber-500/30 to-orange-600/20',
  rose: 'from-rose-500/30 to-pink-600/20',
  sky: 'from-sky-500/30 to-blue-600/20',
  emerald: 'from-emerald-500/30 to-teal-600/20',
  violet: 'from-violet-500/30 to-purple-600/20',
  stone: 'from-stone-500/30 to-stone-700/20'
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
