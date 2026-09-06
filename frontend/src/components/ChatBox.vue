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
    <div ref="chatContainer" class="h-64 overflow-y-auto mb-4 p-3 bg-stone-800/50 rounded-xl border border-stone-700">
      <div class="text-center mb-2">
        <button v-if="canLoadEarlier" @click="loadEarlier" :disabled="loadingEarlier"
          class="text-xs text-orange-400 hover:text-orange-300 px-3 py-1 rounded-full bg-stone-800/80">
          {{ loadingEarlier ? '加载中...' : '加载更早消息' }}
        </button>
        <div v-else-if="historyLoaded" class="text-xs text-stone-600">已到 7 天窗口最早记录</div>
      </div>
      <div v-if="messages.length === 0" class="text-center text-stone-500 py-8">
        <AppIcon name="chat-bubble" class="w-10 h-10 mx-auto mb-2 text-stone-500" />
        <div>暂无消息</div>
      </div>
      <div v-for="(msg, index) in messages" :key="msg.id ?? index" class="mb-2">
        <span class="text-orange-400 font-medium text-sm">{{ msg.player }}</span>
        <span class="text-stone-300">: {{ msg.message }}</span>
        <span class="text-stone-600 text-xs ml-2">{{ formatTime(msg.timestamp) }}</span>
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

interface ChatMessage { id?: number; player: string; message: string; timestamp: number; origin?: string }

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
