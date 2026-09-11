<template>
  <div class="card p-6">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-white">好友</h3>
      <span class="text-xs text-stone-500">{{ friends.length }} 位好友</span>
    </div>

    <!-- 收到的申请 -->
    <div v-if="pendingIncoming.length" class="mb-4 space-y-2">
      <div class="text-xs text-amber-400 font-medium">收到的申请({{ pendingIncoming.length }})</div>
      <div v-for="p in pendingIncoming" :key="p.id"
        class="flex items-center gap-2 p-2.5 rounded-xl bg-amber-500/5 border border-amber-500/25">
        <AppAvatar :name="p.username" size-class="w-8 h-8 shrink-0" />
        <span class="flex-1 text-sm text-white truncate">{{ p.username }}</span>
        <button class="btn-primary text-xs px-3 py-1.5" @click="accept(p.id)">同意</button>
        <button class="btn-secondary text-xs px-3 py-1.5" @click="reject(p.id)">拒绝</button>
      </div>
    </div>

    <EmptyState v-if="friends.length === 0" icon="users" text="还没有好友 — 访问玩家资料页可发送申请" />

    <div class="space-y-1.5">
      <div v-for="f in friends" :key="f.username"
        class="flex items-center gap-2.5 p-2 rounded-xl bg-stone-900/40 border border-stone-800">
        <div class="relative shrink-0">
          <AppAvatar :name="f.username" size-class="w-9 h-9" />
          <span v-if="isOnline(f.username)" class="absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full bg-emerald-400 border-2 border-stone-900"
            title="在线"></span>
        </div>
        <router-link :to="`/player/${encodeURIComponent(f.username)}`" class="flex-1 min-w-0 group">
          <div class="text-sm text-white truncate group-hover:text-orange-300 transition-colors">{{ f.username }}</div>
        </router-link>
        <button class="text-xs text-stone-500 hover:text-rose-400 transition-colors shrink-0"
          @click="remove(f.username)">删除</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject } from 'vue'
import api from '@/services/api'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const notify = inject('notify') as any
const friends = ref<any[]>([])
const pendingIncoming = ref<any[]>([])
const onlinePlayers = ref<Set<string>>(new Set())

onMounted(async () => {
  await Promise.all([load(), loadOnline()])
})

async function load() {
  try {
    const r: any = await api.getFriends()
    if (r.success) {
      friends.value = r.data?.friends || []
      pendingIncoming.value = r.data?.pendingIncoming || []
    }
  } catch (e) { console.error(e) }
}

async function loadOnline() {
  try {
    const r: any = await api.getServerStatus()
    if (r.success) {
      const names = new Set<string>()
      for (const s of r.data?.servers || []) {
        for (const n of s.players || []) names.add(n)
      }
      onlinePlayers.value = names
    }
  } catch (e) { console.error(e) }
}

const isOnline = (name: string) => onlinePlayers.value.has(name)

async function accept(id: number) {
  try {
    const r: any = await api.acceptFriend(id)
    if (r.success) { notify?.success(r.message || '已同意'); await load() } else notify?.error(r.message || '操作失败')
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

async function reject(id: number) {
  try {
    const r: any = await api.rejectFriend(id)
    if (r.success) { notify?.success('已拒绝'); await load() } else notify?.error(r.message || '操作失败')
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

async function remove(username: string) {
  if (!confirm(`确认删除好友「${username}」?`)) return
  try {
    const r: any = await api.removeFriend(username)
    if (r.success) { notify?.success('已删除'); await load() } else notify?.error(r.message || '删除失败')
  } catch (e: any) { notify?.error(e.message || '删除失败') }
}
</script>
