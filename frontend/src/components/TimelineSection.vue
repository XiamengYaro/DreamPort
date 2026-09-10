<template>
  <section class="py-16 px-4">
    <div class="max-w-6xl mx-auto">
      <!-- 刊头:衬线斜体水印 + 杂志式标题 -->
      <div class="relative text-center mb-10">
        <span class="hidden md:block absolute left-1/2 -translate-x-1/2 -top-10 text-[7rem] leading-none font-serif italic text-white/5 select-none pointer-events-none" aria-hidden="true">Moments</span>
        <h2 class="relative text-3xl font-bold text-white mb-3">时光照片墙 · <span class="font-serif italic">Moments</span></h2>
        <p class="relative text-stone-400 text-sm tracking-wide">记录服务器的点点滴滴 · 点击照片可放大查看与留言</p>
      </div>

      <!-- 分类筛选(杂志 kicker 风格:下划线指示器) -->
      <div class="flex gap-6 justify-center mb-10 flex-wrap">
        <button
          v-for="type in timelineTypes"
          :key="type.value"
          @click="currentType = type.value"
          class="pb-2 text-sm tracking-widest uppercase transition-colors border-b-2"
          :class="currentType === type.value
            ? 'border-orange-400 text-orange-300'
            : 'border-transparent text-stone-500 hover:text-stone-300'"
        >
          {{ type.label }}<sup v-if="getCount(type.value) > 0" class="ml-0.5 text-[10px] text-stone-500">{{ getCount(type.value) }}</sup>
        </button>
      </div>

      <!-- 杂志瀑布流:保留原图比例,错落排布 -->
      <div class="columns-1 sm:columns-2 lg:columns-3 gap-6">
        <button v-for="event in filteredTimeline" :key="photoKey(event)" @click="openPhoto(event)"
          class="group block w-full text-left mb-6 break-inside-avoid cursor-pointer">
          <!-- 照片:原比例 + 悬停微缩放 + 年份衬线水印 -->
          <div class="relative overflow-hidden rounded-xl ring-1 ring-white/10 group-hover:ring-orange-400/40 transition-all duration-300 group-hover:shadow-[0_24px_48px_-16px_rgba(0,0,0,0.7)]">
            <img v-if="event.image" :src="event.image" :alt="event.title"
              class="w-full transition-transform duration-500 group-hover:scale-[1.04]"
              loading="lazy" decoding="async" @error="handleImageError($event)" />
            <div v-else class="aspect-[4/3] w-full flex items-center justify-center bg-stone-900/40">
              <AppIcon name="document-text" class="w-10 h-10 text-stone-700" />
            </div>
            <span v-if="yearOf(event.date)"
              class="absolute bottom-1 right-3 text-5xl font-serif italic text-white/15 group-hover:text-white/30 transition-colors duration-300 select-none pointer-events-none drop-shadow-[0_2px_6px_rgba(0,0,0,0.8)]"
              aria-hidden="true">{{ yearOf(event.date) }}</span>
          </div>

          <!-- 图注:分类/日期 kicker + 标题动效下划线 + 摘要 -->
          <div class="pt-3 px-1">
            <div class="flex items-center gap-2 mb-1.5 text-[11px] tracking-[0.18em] uppercase">
              <span class="inline-block w-1.5 h-1.5 rounded-full bg-orange-400"></span>
              <span class="text-orange-300/90">{{ getTypeName(event.type) }}</span>
              <span v-if="event.date" class="text-stone-500">{{ event.date }}</span>
              <span class="ml-auto inline-flex items-center gap-1 text-stone-500 tracking-normal">
                <AppIcon name="chat-bubble" class="w-3.5 h-3.5" />{{ commentCounts[photoKey(event)] || 0 }}
              </span>
            </div>
            <h3 class="relative inline-block font-semibold text-white group-hover:text-orange-300 transition-colors">
              {{ event.title }}
              <span class="absolute left-0 -bottom-0.5 h-0.5 w-full bg-orange-400 origin-left scale-x-0 group-hover:scale-x-100 transition-transform duration-300" aria-hidden="true"></span>
            </h3>
            <p class="text-stone-400 text-sm mt-1.5 line-clamp-2">{{ event.description }}</p>
          </div>
        </button>
      </div>
      <EmptyState v-if="filteredTimeline.length === 0" icon="document-text" text="暂无照片 —— 请在管理后台 → 门户管理 → 时光照片墙 中添加" />
    </div>

    <!-- 灯箱:大图 + 详情 + 留言 -->
    <AppModal :open="!!selected" size="xl" :title="(selected?.title ?? '') + (selected?.date ? ' · ' + selected.date : '')" @close="selected = null">
      <template v-if="selected">
        <img v-if="selected.image" :src="selected.image" :alt="selected.title"
          class="w-full max-h-[55vh] object-contain rounded-xl bg-stone-900/60" @error="handleImageError($event)" />
        <div class="flex items-center gap-2 mt-4 mb-2">
          <span class="px-2 py-0.5 rounded text-xs bg-orange-500/15 text-orange-400">{{ getTypeName(selected.type) }}</span>
          <span class="text-stone-500 text-sm">{{ selected.date }}</span>
        </div>
        <p class="text-stone-300 text-sm leading-relaxed">{{ selected.description }}</p>

        <!-- 留言区 -->
        <div class="mt-6 pt-4 border-t border-white/10">
          <h4 class="font-medium text-white mb-3">留言 ({{ comments.length }})</h4>
          <div v-if="comments.length === 0" class="text-stone-500 text-sm mb-3">还没有留言,来抢沙发~</div>
          <div v-else class="space-y-3 max-h-60 overflow-y-auto mb-3 pr-1">
            <div v-for="c in comments" :key="c.id" class="flex gap-3">
              <AppAvatar :name="c.username" size-class="w-8 h-8" />
              <div class="flex-1 min-w-0">
                <div class="text-sm text-white font-medium">
                  {{ c.username }}
                  <span class="text-stone-500 text-xs ml-2">{{ formatTime(c.created_at) }}</span>
                </div>
                <p class="text-stone-300 text-sm break-words">{{ c.content }}</p>
              </div>
              <button v-if="isAdmin" class="text-stone-500 hover:text-rose-400 text-xs shrink-0" @click="removeComment(c.id)">删除</button>
            </div>
          </div>
          <div v-if="loggedIn" class="flex gap-2">
            <input v-model="commentDraft" class="input flex-1" maxlength="500" placeholder="写下你的留言..." @keyup.enter="submitComment" />
            <button class="btn-primary" :disabled="!commentDraft.trim() || posting" @click="submitComment">
              {{ posting ? '发送中...' : '发送' }}
            </button>
          </div>
          <p v-else class="text-stone-500 text-sm">
            <router-link to="/login" class="text-orange-400 hover:text-orange-300">登录</router-link> 后可以留言
          </p>
        </div>
      </template>
    </AppModal>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/services/api'
import EmptyState from './ui/EmptyState.vue'
import AppAvatar from './ui/AppAvatar.vue'
import AppModal from './ui/AppModal.vue'
import AppIcon from './AppIcon.vue'

interface TimelineEntry {
  id?: string
  date: string
  title: string
  description: string
  image?: string
  type?: string
}

const props = withDefaults(defineProps<{
  timeline: TimelineEntry[]
  /** 照片分类(后台「时光照片墙」可添加),缺省用内置三类 */
  types?: string[]
}>(), { types: () => [] })

const currentType = ref('all')
const selected = ref<TimelineEntry | null>(null)
const comments = ref<any[]>([])
const commentCounts = ref<Record<string, number>>({})
const commentDraft = ref('')
const posting = ref(false)
const loggedIn = ref(!!localStorage.getItem('token'))
const isAdmin = ref(localStorage.getItem('isAdmin') === 'true')

const KNOWN_TYPES = ['announcement', 'event', 'milestone']
const typeNames: Record<string, string> = {
  announcement: '公告更新',
  event: '活动赛事',
  milestone: '成就纪念'
}

const timelineTypes = computed(() => [
  { label: '全部', value: 'all' },
  ...(props.types?.length ? props.types : KNOWN_TYPES).map(t => ({
    label: typeNames[t] || t,
    value: t
  }))
])

const getTypeName = (type?: string) => (type && typeNames[type]) || '其他'

/** 杂志年份水印:从自由格式日期中提取 4 位年份 */
const yearOf = (date?: string) => (date?.match(/\d{4}/) || [''])[0]

/** 稳定键:条目 id 优先,缺省回退 日期|标题 哈希替代(与后台编辑器自动补 id 配套) */
const photoKey = (event: TimelineEntry) =>
  event.id || encodeURIComponent(`${event.date}|${event.title}`)

const filteredTimeline = computed(() => {
  if (currentType.value === 'all') return props.timeline
  return props.timeline.filter(item => item.type === currentType.value)
})

const getCount = (type: string) => {
  if (type === 'all') return props.timeline.length
  return props.timeline.filter(item => item.type === type).length
}

const loadCounts = async () => {
  try {
    const r: any = await api.getPhotoCommentCounts()
    if (r.success) commentCounts.value = r.data.counts || {}
  } catch (e) { console.error(e) }
}

const openPhoto = async (event: TimelineEntry) => {
  selected.value = event
  comments.value = []
  try {
    const r: any = await api.getPhotoComments(photoKey(event))
    if (r.success) comments.value = r.data.comments || []
  } catch (e) { console.error(e) }
}

const submitComment = async () => {
  if (!selected.value || !commentDraft.value.trim()) return
  posting.value = true
  try {
    const key = photoKey(selected.value)
    const r: any = await api.postPhotoComment(key, commentDraft.value.trim())
    if (r.success) {
      comments.value.unshift(r.data.comment ?? { username: localStorage.getItem('username') || '', content: commentDraft.value.trim(), created_at: Date.now() })
      commentCounts.value[key] = (commentCounts.value[key] || 0) + 1
      commentDraft.value = ''
    } else {
      alert(r.message || '留言失败')
    }
  } catch (e: any) {
    alert(e.message || '留言失败')
  }
  posting.value = false
}

const removeComment = async (id: number) => {
  try {
    const r: any = await api.deletePhotoComment(id)
    if (r.success) {
      comments.value = comments.value.filter(c => c.id !== id)
      const key = selected.value ? photoKey(selected.value) : ''
      if (key) commentCounts.value[key] = Math.max(0, (commentCounts.value[key] || 1) - 1)
    }
  } catch (e: any) { alert(e.message || '删除失败') }
}

const formatTime = (ts: number) => {
  if (!ts) return ''
  const d = new Date(ts)
  return `${d.getMonth() + 1}-${d.getDate()} ${d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
}

const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
}

onMounted(loadCounts)
</script>
