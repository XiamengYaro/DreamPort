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
    </div>

    <!-- 消息列表 -->
    <div ref="chatContainer" class="h-64 overflow-y-auto mb-4 p-3 bg-stone-800/50 rounded-xl border border-stone-700">
      <div v-if="messages.length === 0" class="text-center text-stone-500 py-8">
        <AppIcon name="chat-bubble" class="w-10 h-10 mx-auto mb-2 text-stone-500" />
        <div>暂无消息</div>
      </div>
      <div v-for="(msg, index) in messages" :key="index" class="mb-2">
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

const messages = ref<Array<{ player: string; message: string; timestamp: number }>>([])
const newMessage = ref('')
const isConnected = ref(false)
const chatContainer = ref<HTMLElement | null>(null)
const reconnectStatus = ref('')

let eventSource: EventSource | null = null
let historyLoaded = false
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
    if (!historyLoaded) {
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
            timestamp: data.timestamp
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

const sendMessage = async () => {
  if (!newMessage.value.trim()) return
  const msg = newMessage.value.trim()
  newMessage.value = ''

  const token = localStorage.getItem('token') || ''
  try {
    await fetch('/api/chat/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ message: msg })
    })
  } catch (e) {}
}

const loadHistory = async () => {
  if (historyLoaded) return
  historyLoaded = true
  try {
    const res = await fetch('/api/chat/history')
    const data = await res.json()
    if (data.success && data.data) {
      // 去重：只添加不存在的消息
      const existingKeys = new Set(messages.value.map(m => `${m.player}:${m.message}:${m.timestamp}`))
      for (const msg of data.data) {
        const key = `${msg.player}:${msg.message}:${msg.timestamp}`
        if (!existingKeys.has(key)) {
          messages.value.push(msg)
          existingKeys.add(key)
        }
      }
      // 只保留最近200条
      if (messages.value.length > 200) {
        messages.value = messages.value.slice(-200)
      }
      nextTick(() => {
        if (chatContainer.value) {
          chatContainer.value.scrollTop = chatContainer.value.scrollHeight
        }
      })
    }
  } catch (e) {}
}

const formatTime = (timestamp: number) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  connectSSE()
})

onUnmounted(() => {
  historyLoaded = false
  disconnectSSE()
})
</script>
