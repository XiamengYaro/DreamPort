<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-4xl mx-auto">
      <!-- 页头 -->
      <SectionHeader kicker="BANS" title="封禁名单" subtitle="当前处于封禁状态的玩家公示 · 违规零容忍,共建绿色服务器" />

      <div v-if="loading" class="text-center py-12 text-stone-500">加载中...</div>

      <EmptyState v-else-if="bans.length === 0" icon="check-circle" text="太好了,当前没有玩家被封禁" />

      <div v-else class="space-y-5">
        <div v-for="b in bans" :key="b.username" class="card p-5">
          <div class="flex items-start gap-5">
            <!-- 左:大头照 -->
            <img :src="b.avatarUrl" :alt="b.username"
              class="w-24 h-24 rounded-2xl border-2 border-rose-500/40 shadow-xl shrink-0 object-cover"
              @error="handleAvatarError" />
            <!-- 右:ID + 封禁信息 -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap mb-2">
                <h2 class="text-lg font-bold text-white">{{ b.username }}</h2>
                <span v-if="b.minecraftName && b.minecraftName !== b.username" class="text-xs text-stone-500 font-mono">
                  {{ b.minecraftName }}
                </span>
                <span class="px-2 py-0.5 rounded text-xs bg-rose-500/15 text-rose-400 border border-rose-500/30">已封禁</span>
              </div>
              <div class="space-y-1.5 text-sm">
                <p>
                  <span class="text-stone-500 inline-block w-20">封禁原因</span>
                  <span class="text-rose-300">{{ b.banReason || '违规操作' }}</span>
                </p>
                <p>
                  <span class="text-stone-500 inline-block w-20">封禁时间</span>
                  <span class="text-stone-300">{{ formatTime(b.banTime) }}</span>
                </p>
                <p>
                  <span class="text-stone-500 inline-block w-20">封禁时长</span>
                  <span :class="b.banUntil ? 'text-amber-400' : 'text-stone-300'">{{ durationText(b) }}</span>
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/services/api'
import SectionHeader from '@/components/SectionHeader.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(true)
const bans = ref<any[]>([])

onMounted(async () => {
  try {
    const r: any = await api.getBans()
    if (r.success) bans.value = r.data.bans || []
  } catch (e) { console.error(e) }
  loading.value = false
})

const formatTime = (ts: number) => (ts ? new Date(ts).toLocaleString('zh-CN') : '-')

const durationText = (b: any) => {
  if (!b.banUntil) return '永久封禁'
  const remain = b.banUntil - Date.now()
  if (remain <= 0) return '已到期,等待系统自动解封'
  const days = Math.floor(remain / 86400000)
  const hours = Math.floor((remain % 86400000) / 3600000)
  return days > 0 ? `临时封禁,剩余 ${days} 天 ${hours} 小时` : `临时封禁,剩余 ${hours} 小时`
}

const handleAvatarError = (e: Event) => {
  (e.target as HTMLImageElement).src = '/Logo111.png'
}
</script>
