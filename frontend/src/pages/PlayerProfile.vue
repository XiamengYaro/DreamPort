<template>
  <div id="pc-root" class="p-6 pt-24 pb-20" :style="bgImageStyle">
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
        <div class="card overflow-hidden mb-6">
          <div v-if="displayBannerImage" class="h-28 w-full bg-cover bg-center"
            :style="{ backgroundImage: `url('${displayBannerImage}')` }"></div>
          <div v-else class="h-20 w-full bg-gradient-to-r" :class="bannerGradient"></div>
          <div class="p-6">
          <div class="flex flex-col sm:flex-row items-start sm:items-start gap-4 sm:gap-6">
            <img :src="`/api/avatar/${encodeURIComponent(profile.minecraftName || profile.username || profile.uuid)}?size=128`"
              :alt="profile.minecraftName || profile.username"
              class="w-32 h-32 rounded-2xl border-4 border-orange-500/30 shadow-xl"
              @error="handleAvatarError" />
            <div class="flex-1">
              <h1 class="text-3xl font-bold text-white mb-2">{{ profile.username }}
                <button v-if="isSelf" @click="customOpen = true"
                  class="ml-2 align-middle text-xs px-3 py-1.5 rounded-lg bg-orange-500/15 text-orange-400 border border-orange-500/30 hover:bg-orange-500/25 transition-colors">⚙ 自定义主页</button>
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
        </div>

        <!-- 统计卡片 -->
        <div class="grid grid-cols-2 md:grid-cols-5 gap-4 mb-6">
          <StatCard :value="formatPlaytime(profile.timePlayed)" label="总游戏时长" tone="orange" />
          <StatCard :value="(profile.activeDaysLast30 || 0) + ' 天'" label="近30天活跃" tone="orange" />
          <StatCard :value="formatBalance(profile.balance)" label="硬币" tone="orange" />
          <StatCard :value="profile.loginCount || '-'" label="登录次数" tone="orange" />
          <StatCard v-if="myScore != null" :value="myScore.toFixed(1)" label="综合评分" tone="emerald" />
        </div>

        <!-- 关于我(个人主页自定义展示) -->
        <div v-if="profileCustom.bio || (profileCustom.socialLinks || []).length" class="card p-6 mb-6">
          <h2 class="text-lg font-semibold text-white mb-3">关于我</h2>
          <p v-if="profileCustom.bio" class="text-stone-300 text-sm leading-relaxed whitespace-pre-wrap">{{ profileCustom.bio }}</p>
          <div v-if="(profileCustom.socialLinks || []).length" class="flex flex-wrap gap-2 mt-3">
            <a v-for="(l, i) in profileCustom.socialLinks" :key="i" :href="l.url" target="_blank" rel="noopener noreferrer nofollow"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-stone-800/60 border border-stone-700 text-xs text-stone-300 hover:border-orange-500/40 hover:text-white transition-colors">
              <AppIcon name="link" class="w-3.5 h-3.5 text-orange-400" />{{ l.label }}
            </a>
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

    <!-- 主页装修编辑器(本人) -->
    <AppModal :open="customOpen" title="自定义主页" size="lg" @close="customOpen = false">
      <div class="space-y-5">
        <!-- tab 切换 -->
        <div class="flex gap-2 flex-wrap">
          <button v-for="t in customTabs" :key="t.key" @click="customTab = t.key"
            class="tab-btn text-sm" :class="{ active: customTab === t.key }">{{ t.label }}</button>
        </div>

        <!-- 简介 -->
        <div v-if="customTab === 'bio'">
          <label class="block text-xs text-stone-500 mb-1">个人简介(纯文本,≤500 字)</label>
          <textarea v-model="customForm.bio" maxlength="500" rows="4" class="input text-sm"
            placeholder="介绍一下你自己…"></textarea>
        </div>

        <!-- 横幅与背景 -->
        <div v-else-if="customTab === 'banner'">
          <label class="block text-xs text-stone-500 mb-1">横幅图(填写图片地址,留空用主题色渐变)</label>
          <input v-model="customForm.bannerImage" class="input text-sm mb-2" placeholder="/uploads/xxx.png 或 https://…" />
          <label class="block text-xs text-stone-500 mb-1">主题色(无横幅图时的渐变色)</label>
          <div class="flex gap-2 flex-wrap mb-2">
            <button v-for="(grad, key) in BANNERS" :key="key" @click="customForm.banner = key"
              class="w-12 h-9 rounded-lg bg-gradient-to-r transition-all"
              :class="[grad, customForm.banner === key ? 'ring-2 ring-orange-400' : 'ring-1 ring-white/10']"
              :title="key"></button>
          </div>
          <label class="block text-xs text-stone-500 mb-1">页面背景图(个人主页专属背景)</label>
          <div class="flex gap-2">
            <input v-model="customForm.bgImage" class="input flex-1 text-sm" placeholder="背景图地址(留空用全站背景)" />
            <button class="btn-secondary text-sm" @click="pickBgImage">上传</button>
          </div>
          <input ref="bgInput" type="file" accept="image/jpeg,image/png,image/webp" class="hidden" @change="uploadBg" />
        </div>

        <!-- 社交链接 -->
        <div v-else-if="customTab === 'links'">
          <div v-for="(l, i) in customForm.socialLinks" :key="i" class="flex gap-2 mb-2">
            <input v-model="l.label" class="input w-36 text-sm" placeholder="名称(如 B站)" maxlength="20" />
            <input v-model="l.url" class="input flex-1 text-sm" placeholder="https://…" />
            <button class="text-rose-400 hover:text-rose-300 text-sm px-1" @click="customForm.socialLinks.splice(i, 1)">×</button>
          </div>
          <button v-if="(customForm.socialLinks || []).length < 5" class="btn-secondary text-xs py-1.5 px-3"
            @click="customForm.socialLinks.push({ label: '', url: '' })">+ 添加链接</button>
        </div>

        <!-- 自定义 CSS -->
        <div v-else-if="customTab === 'css'">
          <p class="text-xs text-stone-500 mb-2">
            高级:自定义个人主页样式。所有选择器自动限定在 <code class="text-orange-400">#pc-root</code> 内,
            自动剔除 @import/javascript:/position:fixed 等危险内容,≤8000 字符。
          </p>
          <textarea v-model="customForm.css" rows="8" class="input text-xs font-mono"
            placeholder=".card { border-radius: 16px; }&#10;h1 { color: #fb923c; }"></textarea>
          <p class="text-xs text-stone-600 mt-1">当前 {{ (customForm.css || '').length }} / 8000 字符</p>
        </div>
      </div>
      <template #footer>
        <button class="btn-secondary" @click="customOpen = false">取消</button>
        <button class="btn-primary" :disabled="customSaving" @click="saveCustom">
          {{ customSaving ? '保存中…' : '保存' }}
        </button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, inject, watch } from 'vue'
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
  if (route.query.customize && isSelf.value) {
    startCustomEdit()
  }
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
const customOpen = ref(false)
const customTab = ref('bio')
const customForm = ref<any>({
  bio: '', banner: 'amber', socialLinks: [] as any[],
  bgImage: '', bannerImage: '', accent: '', css: ''
})
const customSaving = ref(false)
const bgInput = ref<HTMLInputElement | null>(null)

const customTabs = [
  { key: 'bio', label: '简介' },
  { key: 'banner', label: '横幅与背景' },
  { key: 'links', label: '社交链接' },
  { key: 'css', label: '自定义 CSS' }
]

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
    socialLinks: (profileCustom.value.socialLinks || []).map((x: any) => ({ ...x })),
    bgImage: profileCustom.value.bgImage || '',
    bannerImage: profileCustom.value.bannerImage || '',
    accent: profileCustom.value.accent || '',
    css: profileCustom.value.css || ''
  }
  customOpen.value = true
}

const pickBgImage = () => bgInput.value?.click()

const uploadBg = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) { notify?.error('图片不能超过 5MB'); return }
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await fetch('/api/upload/image', {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
      body: fd
    })
    const data = await res.json()
    if (data.success) {
      customForm.value.bgImage = data.data.url
      notify?.success('背景图已上传,保存后生效')
    } else notify?.error(data.message || '上传失败')
  } catch (e: any) { notify?.error(e.message || '上传失败') }
}

const saveCustom = async () => {
  customSaving.value = true
  try {
    const links = (customForm.value.socialLinks || []).filter((x: any) => (x.url || '').trim())
    const r: any = await api.saveProfileCustom({
      bio: customForm.value.bio || '',
      banner: customForm.value.banner || 'amber',
      socialLinks: links.map((x: any) => ({ label: x.label || '链接', url: x.url.trim() })),
      bgImage: customForm.value.bgImage || '',
      bannerImage: customForm.value.bannerImage || '',
      accent: customForm.value.accent || '',
      css: customForm.value.css || ''
    })
    if (r.success) {
      notify?.success(r.message || '已保存')
      customOpen.value = false
      profileCustom.value = {
        bio: customForm.value.bio, banner: customForm.value.banner, socialLinks: links,
        bgImage: customForm.value.bgImage, bannerImage: customForm.value.bannerImage,
        accent: customForm.value.accent, css: customForm.value.css
      }
    } else notify?.error(r.message || '保存失败')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
  customSaving.value = false
}

const addLinkRow = () => {
  if ((customForm.value.socialLinks || []).length >= 5) return
  customForm.value.socialLinks.push({ label: '', url: '' })
}

const bgImageStyle = computed(() => {
  const img = (customOpen.value ? customForm.value.bgImage : '') || profileCustom.value.bgImage
  if (!img) return {}
  return {
    backgroundImage: `linear-gradient(rgba(28,25,23,0.88), rgba(28,25,23,0.92)), url('${img}')`,
    backgroundSize: 'cover',
    backgroundPosition: 'center'
  }
})

const displayBannerImage = computed(() =>
  customOpen.value ? customForm.value.bannerImage : (profileCustom.value.bannerImage || ''))
const bannerGradient = computed(() => {
  const gradient = customOpen.value ? customForm.value.banner : (profileCustom.value.banner || 'amber')
  return BANNERS[gradient] || BANNERS.amber
})

// 自定义 CSS 注入(服务端已消毒+作用域前缀,此处仅插入)
const cssStyleEl = ref<HTMLStyleElement | null>(null)
const applyCss = (css: string) => {
  let el = document.getElementById('pc-style') as HTMLStyleElement | null
  if (!css) {
    el?.remove()
    return
  }
  if (!el) {
    el = document.createElement('style')
    el.id = 'pc-style'
    document.head.appendChild(el)
  }
  el.textContent = css
}
// 装修编辑时实时预览(编辑中的 customForm 直接驱动展示)
watch(customForm, (f) => {
  if (!customOpen.value) return
  profileCustom.value = {
    ...profileCustom.value,
    bio: f.bio, banner: f.banner, bgImage: f.bgImage,
    bannerImage: f.bannerImage, accent: f.accent
  }
  applyCss(f.css || '')
}, { deep: true })

// 保存后以服务端消毒结果覆盖
watch(() => profileCustom.value?.css, (v) => applyCss(v || ''))

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
