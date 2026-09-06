<template>
  <div class="card p-4">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-white flex items-center gap-2">
        <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8c0 1.017-.177 1.99-.481 2.89" />
        </svg>
        服务器聊天
        <span class="text-xs px-2 py-0.5 rounded-full" :class="isConnected ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'">
          {{ isConnected ? '已连接' : '未连接' }}
        </span>
        <span v-if="reconnectStatus" class="text-xs text-amber-400">
          {{ reconnectStatus }}
        </span>
      </h3>
      <span class="text-xs text-stone-500">保留近 7 天历史</span>
    </div>

    <!-- 消息列表 -->
    <div ref="chatContainer" :class="tall ? 'h-[55vh]' : 'h-64'" class="overflow-y-auto mb-4 p-3 bg-stone-800/50 rounded-xl border border-stone-700">
      <div class="text-center mb-2">
        <button v-if="canLoadEarlier" @click="loadEarlier" :disabled="loadingEarlier"
          class="text-xs text-orange-400 hover:text-orange-300 px-3 py-1 rounded-full bg-stone-800/80">
          {{ loadingEarlier ? '加载中...' : '加载更早消息' }}
        </button>
        <div v-else-if="historyLoaded" class="text-xs text-stone-600">已到 7 天窗口最早记录</div>
      </div>
      <EmptyState v-if="messages.length === 0" icon="chat-bubble" text="暂无消息" />
      <div v-for="(msg, index) in messages" :key="msg.id ?? index" class="flex gap-3 mb-4">
        <img v-if="originOf(msg) === 'game'" :src="`https://crafthead.net/avatar/${displayName(msg)}/64`"
          :alt="displayName(msg)" class="w-9 h-9 rounded-full shrink-0" loading="lazy" />
        <div v-else class="w-9 h-9 rounded-full shrink-0 flex items-center justify-center text-white text-xs font-bold"
          :class="avatarBg(originOf(msg))">
          {{ (displayName(msg) || '?').charAt(0).toUpperCase() }}
        </div>
        <div class="min-w-0 flex-1">
          <div class="flex items-baseline gap-2 flex-wrap">
            <span class="text-orange-400 font-medium text-sm">{{ displayName(msg) }}</span>
            <span v-if="serverLabel(msg)" class="text-xs text-stone-500">{{ serverLabel(msg) }}</span>
            <span class="text-stone-600 text-xs">{{ formatTime(msg.timestamp) }}</span>
          </div>
          <div class="text-stone-200 text-sm mt-0.5 break-words">{{ msg.message }}</div>
        </div>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="flex gap-2">
      <input
        v-model="newMessage"
        type="text"
        class="input flex-1"
        placeholder="输入消息..."
        @keyup.enter="sendMessage"
        :disabled="!isConnected"
      />
      <button
        @click="sendMessage"
        class="btn-primary"
        :disabled="!isConnected || !newMessage.trim()"
      >
        发送
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { api } from '../services/api'
import EmptyState from './ui/EmptyState.vue'

interface ChatMessage { id?: number; player: string; message: string; timestamp: number; origin?: string; server_id?: string | null }

const props = withDefaults(defineProps<{
  /** 独立聊天页使用加高消息区 */
  tall?: boolean
  /** 服务器 ID → 显示名映射(聊天页传入) */
  serverNames?: Record<string, string>
}>(), { tall: false, serverNames: () => ({}) })

const originOf = (m: any) =>
  m.origin || (String(m.player || '').startsWith('[QQ]') ? 'qq'
    : String(m.player || '').startsWith('[系统]') ? 'system' : 'game')

const displayName = (m: any) => {
  const raw = String(m.player || '')
  const stripped = raw.replace(/^\[(QQ|系统|网页)\]\s*/, '')
  if (stripped) return stripped
  return originOf(m) === 'system' ? '系统' : (raw || '未知')
}

const avatarBg = (origin: string) =>
  (({ web: 'bg-sky-500/70', qq: 'bg-rose-500/70', system: 'bg-stone-500' }) as Record<string, string>)[origin] || 'bg-stone-500'

const serverLabel = (m: any) => {
  const o = originOf(m)
  if (o === 'web') return '网页'
  if (o === 'qq') return 'QQ'
  if (o === 'game') return props.serverNames?.[String(m.server_id)] || String(m.server_id || '') || ''
  return ''
}

const messages = ref<ChatMessage[]>([])
const newMessage = ref('')
const isConnected = ref(false)
const chatContainer = ref<HTMLElement | null>(null)
const reconnectStatus = ref('')
const canLoadEarlier = ref(false)
const loadingEarlier = ref(false)
const historyLoaded = ref(false)

let nextBefore: number | null = null

let eventSource: EventSource | null = null
let reconnectCount = 0
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

const connectSSE = () => {
  const token = localStorage.getItem('token') || ''
  if (!token) return

  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  eventSource = new EventSource(`/api/chat/stream?token=${encodeURIComponent(token)}`)

  eventSource.onopen = () => {
    reconnectCount = 0
    reconnectStatus.value = ''
    isConnected.value = true
    if (!historyLoaded.value) {
      loadHistory()
    }
  }

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'connected') return
      if (data.type === 'chat') {
        // 去重：检查消息是否已存在
        const key = `${data.player}:${data.message}:${data.timestamp}`
        const exists = messages.value.some(m =>
          `${m.player}:${m.message}:${m.timestamp}` === key
        )
        if (!exists) {
          messages.value.push({
            player: data.player,
            message: data.message,
            timestamp: data.timestamp,
            origin: data.origin
          })
          if (messages.value.length > 200) {
            messages.value = messages.value.slice(-200)
          }
          nextTick(() => {
            if (chatContainer.value) {
              chatContainer.value.scrollTop = chatContainer.value.scrollHeight
            }
          })
        }
      }
    } catch (e) {}
  }

  eventSource.onerror = () => {
    isConnected.value = false
    eventSource?.close()
    eventSource = null

    reconnectCount++
    if (reconnectCount > 10) {
      reconnectStatus.value = '连接失败，请刷新页面重试'
      return
    }

    const delay = Math.min(1000 * Math.pow(2, reconnectCount - 1), 30000)
    reconnectStatus.value = `重连中 (${reconnectCount}/10)`
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connectSSE()
    }, delay)
  }
}

const disconnectSSE = () => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  isConnected.value = false
  reconnectStatus.value = ''
  reconnectCount = 0
}

const msgKey = (m: { player: string; message: string; timestamp: number }) =>
  `${m.player}:${m.message}:${m.timestamp}`

/** 初始加载：最近 200 条（与实时消息去重合并） */
const loadHistory = async () => {
  historyLoaded.value = true
  try {
    const res: any = await api.getChatHistory({ limit: 200 })
    if (res.success && res.data) {
      const existing = new Set(messages.value.map(msgKey))
      const older: ChatMessage[] = []
      for (const msg of res.data.history || []) {
        if (!existing.has(msgKey(msg))) {
          older.push(msg)
        }
      }
      if (older.length) {
        messages.value = [...older, ...messages.value]
      }
      nextBefore = res.data.nextBefore ?? null
      canLoadEarlier.value = nextBefore != null
      nextTick(() => {
        if (chatContainer.value) {
          chatContainer.value.scrollTop = chatContainer.value.scrollHeight
        }
      })
    }
  } catch (e) { console.error('loadHistory failed', e) }
}

/** 向前翻页：游标 before 取更早一页，保持视口位置 */
const loadEarlier = async () => {
  if (nextBefore == null || loadingEarlier.value) return
  loadingEarlier.value = true
  try {
    const res: any = await api.getChatHistory({ before: nextBefore, limit: 200 })
    if (res.success && res.data) {
      const container = chatContainer.value
      const prevHeight = container?.scrollHeight ?? 0
      const existing = new Set(messages.value.map(msgKey))
      const older: ChatMessage[] = (res.data.history || []).filter(
        (m: ChatMessage) => !existing.has(msgKey(m)))
      if (older.length) {
        messages.value = [...older, ...messages.value]
      }
      nextBefore = res.data.nextBefore ?? null
      canLoadEarlier.value = nextBefore != null
      nextTick(() => {
        if (container && older.length) {
          container.scrollTop = container.scrollHeight - prevHeight
        }
      })
    }
  } catch (e) { console.error('loadEarlier failed', e) }
  finally { loadingEarlier.value = false }
}

const sendMessage = async () => {
  if (!newMessage.value.trim()) return
  const msg = newMessage.value.trim()
  newMessage.value = ''
  try {
    await api.sendChat(msg)
  } catch (e) {}
}

/** 跨天显示日期,当天只显示时分 */
const formatTime = (timestamp: number) => {
  const date = new Date(timestamp)
  const today = new Date()
  const sameDay = date.toDateString() === today.toDateString()
  const time = date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return sameDay ? time : `${date.getMonth() + 1}-${date.getDate()} ${time}`
}

onMounted(() => {
  connectSSE()
})

onUnmounted(() => {
  disconnectSSE()
})
</script>
