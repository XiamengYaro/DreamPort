<template>
  <div class="relative">
    <button @click.stop="toggle" class="relative p-2 rounded-xl text-white/80 hover:text-white hover:bg-white/10 transition-colors">
      <AppIcon name="bell" class="w-5 h-5" />
      <span v-if="unreadCount > 0"
        class="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-rose-500 text-white text-[10px] font-bold flex items-center justify-center">
        {{ unreadCount > 99 ? '99+' : unreadCount }}
      </span>
    </button>

    <transition name="modal">
      <div v-if="open" class="absolute right-0 top-full mt-2 w-80 glass-panel rounded-xl shadow-xl z-50 overflow-hidden" @click.stop>
        <div class="flex items-center justify-between px-4 py-3 border-b border-white/10">
          <span class="text-white font-semibold text-sm">通知中心</span>
          <button v-if="unreadCount > 0" @click="markAll" class="text-xs text-orange-400 hover:text-orange-300">全部已读</button>
        </div>
        <div class="max-h-80 overflow-y-auto">
          <div v-if="notifications.length === 0" class="px-4 py-8 text-center text-stone-500 text-sm">暂无通知</div>
          <div v-for="n in notifications" :key="n.id"
            class="px-4 py-3 border-b border-white/5 hover:bg-white/5" :class="!n.isRead ? 'bg-orange-500/5' : ''">
            <div class="flex items-start gap-2">
              <span class="mt-1.5 w-2 h-2 rounded-full shrink-0" :class="n.isRead ? 'bg-stone-600' : 'bg-orange-400'"></span>
              <div class="min-w-0 flex-1">
                <div class="text-sm text-white">{{ n.title }}</div>
                <p class="text-xs text-stone-400 break-words mt-0.5">{{ n.message }}</p>
                <div class="text-xs text-stone-600 mt-1">{{ formatTime(n.createdAt) }}</div>
              </div>
              <div class="flex flex-col items-end gap-1 shrink-0">
                <button v-if="!n.isRead" @click="markRead(n)" class="text-xs text-orange-400 hover:text-orange-300">已读</button>
                <button @click="remove(n)" class="text-xs text-stone-600 hover:text-rose-400">删除</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api } from '../services/api'
import AppIcon from './AppIcon.vue'

const open = ref(false)
const notifications = ref<any[]>([])
const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length)

const toggle = () => {
  open.value = !open.value
  if (open.value) load()
}

const load = async () => {
  try {
    const r: any = await api.getNotifications()
    if (r.success) notifications.value = r.data.notifications || []
  } catch (e) { console.error(e) }
}

const markRead = async (n: any) => {
  try {
    const r: any = await api.markNotificationRead(n.id)
    if (r.success) n.isRead = true
  } catch (e) { console.error(e) }
}

const markAll = async () => {
  try {
    const r: any = await api.markAllNotificationsRead()
    if (r.success) notifications.value = notifications.value.map(n => ({ ...n, isRead: true }))
  } catch (e) { console.error(e) }
}

const remove = async (n: any) => {
  try {
    const r: any = await api.deleteNotification(n.id)
    if (r.success) notifications.value = notifications.value.filter(x => x.id !== n.id)
  } catch (e) { console.error(e) }
}

const formatTime = (ts: number) => {
  if (!ts) return ''
  const d = new Date(ts)
  const diff = Date.now() - ts
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return `${d.getMonth() + 1}-${d.getDate()} ${d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
}

const onDocClick = () => { open.value = false }
onMounted(() => document.addEventListener('click', onDocClick))
onUnmounted(() => document.removeEventListener('click', onDocClick))
</script>
