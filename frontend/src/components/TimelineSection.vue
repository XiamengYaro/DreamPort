<template>
  <section class="py-16 px-4">
    <div class="max-w-6xl mx-auto">
      <h2 class="text-3xl font-bold text-white text-center mb-3">时光照片墙 · Moments</h2>
      <p class="text-center text-stone-400 mb-6">记录服务器的点点滴滴 · 点击照片可放大查看与留言</p>

      <!-- 分类筛选 -->
      <div class="flex gap-2 justify-center mb-8 flex-wrap">
        <button
          v-for="type in timelineTypes"
          :key="type.value"
          @click="currentType = type.value"
          :class="currentType === type.value ? 'bg-orange-500/20 border-orange-500 text-orange-400' : 'border-stone-700 text-stone-400 hover:border-stone-600'"
          class="px-4 py-2 rounded-lg border transition-all text-sm font-medium"
        >
          {{ type.label }}
          <span v-if="getCount(type.value) > 0" class="ml-1 px-1.5 py-0.5 bg-orange-500/10 rounded text-xs">{{ getCount(type.value) }}</span>
        </button>
      </div>

      <!-- 照片墙 -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        <button v-for="event in filteredTimeline" :key="photoKey(event)" @click="openPhoto(event)"
          class="card card-hover overflow-hidden text-left group">
          <div class="aspect-[4/3] bg-stone-800/60 overflow-hidden">
            <img v-if="event.image" :src="event.image" :alt="event.title"
              class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
              loading="lazy" decoding="async" @error="handleImageError($event)" />
            <div v-else class="w-full h-full flex items-center justify-center">
              <AppIcon name="document-text" class="w-10 h-10 text-stone-600" />
            </div>
          </div>
          <div class="p-4">
            <div class="flex items-center gap-2 mb-2">
              <span class="px-2 py-0.5 rounded text-xs bg-orange-500/15 text-orange-400">{{ getTypeName(event.type) }}</span>
              <span class="text-stone-500 text-xs">{{ event.date }}</span>
              <span class="ml-auto text-xs text-stone-500 flex items-center gap-1">
                <AppIcon name="chat-bubble" class="w-3.5 h-3.5" />{{ commentCounts[photoKey(event)] || 0 }}
              </span>
            </div>
            <h3 class="font-semibold text-white group-hover:text-orange-400 transition-colors">{{ event.title }}</h3>
            <p class="text-stone-400 text-sm mt-1 line-clamp-2">{{ event.description }}</p>
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
              <div class="w-8 h-8 rounded-full bg-gradient-to-br from-orange-400 to-amber-400 flex items-center justify-center text-white text-xs font-bold shrink-0">
                {{ (c.username || '?').charAt(0).toUpperCase() }}
              </div>
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
