<template>
  <div class="space-y-4">
    <EmptyState v-if="polls.length === 0 && !loading" text="暂无投票" />
    <div v-for="p in polls" :key="p.id" class="card p-6">
      <div class="flex flex-wrap items-center gap-2 mb-1">
        <h3 class="text-lg font-semibold text-white">{{ p.title }}</h3>
        <span class="text-[10px] px-1.5 py-0.5 rounded"
          :class="p.ended ? 'bg-stone-600/40 text-stone-300' : 'bg-emerald-500/20 text-emerald-300'">
          {{ p.ended ? '已结束' : '进行中' }}
        </span>
        <span v-if="p.multiple" class="text-[10px] px-1.5 py-0.5 rounded bg-sky-500/20 text-sky-300">多选</span>
      </div>
      <p v-if="p.description" class="text-sm text-stone-400 mb-3">{{ p.description }}</p>
      <p v-if="p.ends_at && !p.ended" class="text-xs text-stone-500 mb-3">
        截止:{{ new Date(p.ends_at).toLocaleString('zh-CN') }}
      </p>

      <div class="space-y-2">
        <div v-for="o in p.options" :key="o.id" class="relative">
          <button v-if="!p.ended && !p.voted" @click="toggleOption(p, o.id)"
            class="w-full text-left px-4 py-3 rounded-xl border transition-colors"
            :class="picked(p, o.id) ? 'border-orange-500/50 bg-orange-500/10 text-white' : 'border-stone-700 text-stone-300 hover:border-stone-500'">
            {{ o.label }}
          </button>
          <div v-else class="px-4 py-3 rounded-xl border"
            :class="o.myChoice ? 'border-orange-500/50 bg-orange-500/10' : 'border-stone-700'">
            <div class="flex justify-between text-sm" :class="o.myChoice ? 'text-white' : 'text-stone-300'">
              <span>{{ o.label }}<span v-if="o.myChoice" class="text-orange-400 ml-1.5">✓</span></span>
              <span v-if="p.showResults && o.votes != null" class="text-stone-400">
                {{ o.votes }} 票 · {{ percent(o.votes, p.totalVotes) }}%
              </span>
            </div>
            <div v-if="p.showResults && o.votes != null && p.totalVotes > 0"
              class="h-1.5 mt-2 rounded-full bg-white/5 overflow-hidden">
              <div class="h-full rounded-full bg-orange-500/60 transition-all"
                :style="{ width: percent(o.votes, p.totalVotes) + '%' }"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="flex items-center justify-between mt-4">
        <span class="text-xs text-stone-500">
          <template v-if="p.showResults && p.totalVotes != null">{{ p.totalVotes }} 人参与</template>
          <template v-else-if="p.ended">结果已随投票结束揭晓</template>
          <template v-else>投票后可见结果</template>
        </span>
        <button v-if="!p.ended && !p.voted && pickedIds[p.id]?.length" class="btn-primary text-sm"
          :disabled="sending" @click="submitVote(p)">投票</button>
        <span v-else-if="p.voted" class="text-xs text-emerald-400">已参与{{ !p.ended && !p.multiple ? ' · 单选不可修改' : '' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject } from 'vue'
import api from '@/services/api'
import EmptyState from '@/components/ui/EmptyState.vue'

const notify = inject('notify') as any
const polls = ref<any[]>([])
const pickedIds = ref<Record<number, number[]>>({})
const sending = ref(false)
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const r: any = await api.getPolls()
    if (r.success) polls.value = r.data || []
  } catch (e) { console.error(e) }
  loading.value = false
}

function picked(p: any, optionId: number): boolean {
  return (pickedIds.value[p.id] || []).includes(optionId)
}

function toggleOption(p: any, optionId: number) {
  const cur = pickedIds.value[p.id] || []
  if (p.multiple) {
    pickedIds.value[p.id] = cur.includes(optionId)
      ? cur.filter((x: number) => x !== optionId)
      : [...cur, optionId]
  } else {
    pickedIds.value[p.id] = [optionId]
  }
}

async function submitVote(p: any) {
  const ids = pickedIds.value[p.id] || []
  if (!ids.length) return
  sending.value = true
  try {
    const r: any = await api.votePoll(p.id, ids)
    if (r.success) {
      notify?.success(r.message || '投票成功')
      await load()
    } else notify?.error(r.message || '投票失败')
  } catch (e: any) { notify?.error(e.message || '投票失败') }
  sending.value = false
}

function percent(votes: number, total: number): number {
  if (!total) return 0
  return Math.round((votes / total) * 100)
}
</script>
