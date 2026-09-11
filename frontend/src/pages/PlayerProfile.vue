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
                    <div class="h-full rounded-full transition-[width]"
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

        <!-- 个人主页自定义(简介/横幅/链接) -->
        <div v-if="isSelf || (profileCustom.bio || (profileCustom.socialLinks || []).length)"
          class="card overflow-hidden mb-6">
          <div class="h-14 bg-gradient-to-r" :class="BANNERS[profileCustom.banner] || BANNERS.amber"></div>
          <div class="p-6">
            <div class="flex items-center justify-between mb-3">
              <h2 class="text-lg font-semibold text-white">关于我</h2>
              <button v-if="isSelf && !customEdit" class="btn-secondary text-xs px-3 py-1.5" @click="startCustomEdit">编辑</button>
            </div>

            <!-- 展示 -->
            <template v-if="!customEdit">
              <p v-if="profileCustom.bio" class="text-stone-300 text-sm leading-relaxed whitespace-pre-wrap">{{ profileCustom.bio }}</p>
              <p v-else class="text-stone-500 text-sm">这位玩家还没有填写简介</p>
              <div v-if="(profileCustom.socialLinks || []).length" class="flex flex-wrap gap-2 mt-3">
                <a v-for="(l, i) in profileCustom.socialLinks" :key="i" :href="l.url" target="_blank" rel="noopener noreferrer nofollow"
                  class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-stone-800/60 border border-stone-700 text-xs text-stone-300 hover:border-orange-500/40 hover:text-white transition-colors">
                  <AppIcon name="link" class="w-3.5 h-3.5 text-orange-400" />{{ l.label }}
                </a>
              </div>
            </template>

            <!-- 编辑表单(本人) -->
            <template v-else>
              <label class="block text-xs text-stone-500 mb-1">个人简介(纯文本,≤500 字)</label>
              <textarea v-model="customForm.bio" maxlength="500" rows="3" class="input text-sm mb-3"
                placeholder="介绍一下你自己…"></textarea>
              <label class="block text-xs text-stone-500 mb-1">横幅主题色</label>
              <div class="flex gap-2 mb-3">
                <button v-for="(grad, key) in BANNERS" :key="key" @click="customForm.banner = key"
                  class="w-10 h-8 rounded-lg bg-gradient-to-r transition-all"
                  :class="[grad, customForm.banner === key ? 'ring-2 ring-orange-400' : 'ring-1 ring-white/10']"
                  :title="key"></button>
              </div>
              <label class="block text-xs text-stone-500 mb-1">社交链接(≤5 条,仅 http/https)</label>
              <div v-for="(l, i) in customForm.socialLinks" :key="i" class="flex gap-2 mb-2">
                <input v-model="l.label" class="input w-32 text-sm" placeholder="名称" maxlength="20" />
                <input v-model="l.url" class="input flex-1 text-sm" placeholder="https://…" />
                <button class="text-rose-400 hover:text-rose-300 text-sm px-1" @click="customForm.socialLinks.splice(i, 1)">×</button>
              </div>
              <button class="btn-secondary text-xs py-1.5 px-3 mb-3" @click="addLinkRow">+ 添加链接</button>
              <div class="flex gap-2">
                <button class="btn-primary text-sm" :disabled="customSaving" @click="saveCustom">
                  {{ customSaving ? '保存中…' : '保存' }}
                </button>
                <button class="btn-secondary text-sm" @click="customEdit = false">取消</button>
              </div>
            </template>
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
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'
import { getStatusText } from '@/lib/status'
import AppIcon from '@/components/AppIcon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'

const route = useRoute()
const loading = ref(false)
const profile = ref<any>(null)
const isLoggedIn = computed(() => !!localStorage.getItem('token'))
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
      if (profile.value?.profileCustom) {
        profileCustom.value = profile.value.profileCustom
      }
      await loadScore()
      await loadFriendState()
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
