<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-7xl mx-auto">
      <!-- 页头 -->
      <div class="card p-6 mb-6">
        <h1 class="text-2xl font-bold text-white">聊天广场</h1>
        <p class="text-stone-400">游戏 / QQ / 网页三端实时互通 · 保留近 7 天历史 · 点击右侧玩家可查看档案</p>
      </div>

      <div class="flex flex-col lg:flex-row gap-6 items-start">
        <!-- 左:聊天 -->
        <ChatBox class="flex-1 min-w-0 w-full" tall :server-names="serverNames" />

        <!-- 右:在线玩家列表 -->
        <div class="card p-4 w-full lg:w-64 lg:sticky lg:top-24 shrink-0">
          <h3 class="text-lg font-semibold text-white mb-3 flex items-center gap-2">
            在线玩家
            <span class="text-xs px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400">{{ onlinePlayers.length }}</span>
          </h3>
          <EmptyState v-if="onlinePlayers.length === 0" text="暂无玩家在线" />
          <div v-else class="space-y-1 max-h-[55vh] overflow-y-auto">
            <router-link v-for="p in onlinePlayers" :key="p.name + p.server" :to="`/player/${p.name}`"
              class="flex items-center gap-3 p-2 rounded-xl hover:bg-white/5 transition-colors">
              <img :src="`/api/avatar/${encodeURIComponent(p.name)}?size=48`" :alt="p.name"
                class="w-8 h-8 rounded-full border border-orange-500/30" loading="lazy" />
              <div class="min-w-0">
                <div class="text-sm text-white truncate">{{ p.name }}</div>
                <div class="text-xs text-stone-500 truncate">{{ p.server }}</div>
              </div>
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/services/api'
import ChatBox from '@/components/ChatBox.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const servers = ref<any[]>([])

onMounted(async () => {
  try {
    const r: any = await api.getServerStatus()
    if (r.success) servers.value = r.data.servers || []
  } catch (e) { console.error(e) }
})

const onlinePlayers = computed(() =>
  servers.value
    .filter(s => !s.stale)
    .flatMap(s => (s.players || []).map((name: string) => ({ name, server: s.serverName || s.serverId }))))

const serverNames = computed(() => {
  const map: Record<string, string> = {}
  for (const s of servers.value) map[s.serverId] = s.serverName || s.serverId
  return map
})
</script>
