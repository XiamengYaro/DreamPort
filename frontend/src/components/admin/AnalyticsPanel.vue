<template>
  <div class="card p-6 space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h3 class="text-lg font-semibold text-white">行为分析</h3>
      <select v-model.number="days" class="input" style="max-width: 150px">
        <option :value="7">近 7 天</option>
        <option :value="30">近 30 天</option>
        <option :value="60">近 60 天</option>
        <option :value="90">近 90 天</option>
      </select>
    </div>

    <EmptyState v-if="empty && !loading" text="暂无行为数据(需玩家产生在线/聊天行为)" />
    <template v-else>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard :value="(totals.totalPlaytimeHours || 0).toFixed(1)" label="总在线时长(小时)" tone="orange" />
        <StatCard :value="totals.activePlayers || 0" label="活跃玩家数" tone="emerald" />
        <StatCard :value="(totals.avgHoursPerPlayer || 0).toFixed(1)" label="人均在线(小时)" tone="blue" />
      </div>

      <div class="space-y-6">
        <div>
          <div class="text-sm text-stone-400 mb-2">每日活跃玩家数(DAU)</div>
          <LineChart :series="dauSeries" :height="170" />
        </div>
        <div>
          <div class="text-sm text-stone-400 mb-2">注册趋势(每日新增)</div>
          <LineChart :series="regSeries" :height="150" />
        </div>
        <div v-if="chatSeries.length">
          <div class="text-sm text-stone-400 mb-2">聊天量趋势(近 7 天)</div>
          <LineChart :series="chatSeries" :height="140" />
        </div>
      </div>

      <div>
        <div class="text-sm text-stone-400 mb-2">Top 活跃玩家(在线时长)</div>
        <div class="space-y-1.5">
          <div v-for="(p, i) in topActive" :key="p.username"
            class="flex items-center gap-3 p-2.5 rounded-xl bg-stone-900/40 border border-stone-800">
            <span class="w-7 text-center font-serif tabular-nums"
              :class="i === 0 ? 'text-amber-300' : i === 1 ? 'text-stone-300' : i === 2 ? 'text-orange-400' : 'text-stone-500'">{{ i + 1 }}</span>
            <span class="flex-1 text-sm text-white truncate">{{ p.username }}</span>
            <span class="text-xs text-stone-500">{{ p.activeDays }} 天活跃</span>
            <span class="text-sm text-orange-400 tabular-nums">{{ (p.hours || 0).toFixed(1) }}h</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import api from '@/services/api'
import LineChart from '@/components/LineChart.vue'
import StatCard from '@/components/ui/StatCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const days = ref(30)
const overview = ref<any>(null)
const loading = ref(false)

onMounted(load)
watch(days, load)

async function load() {
  loading.value = true
  try {
    const r: any = await api.getAnalyticsOverview(days.value)
    if (r.success) overview.value = r.data || {}
  } catch (e) { console.error(e) }
  loading.value = false
}

const empty = computed(() => !overview.value)

const totals = computed(() => overview.value?.totals || {})

const toPoints = (rows: any[]) =>
  (rows || []).map((r: any) => ({ t: new Date(r.t + 'T00:00:00').getTime(), v: Number(r.v) }))

const dauSeries = computed(() => [
  { name: '活跃玩家', color: '#fb923c', points: toPoints(overview.value?.dau) }
])
const regSeries = computed(() => [
  { name: '每日新增', color: '#38bdf8', points: toPoints(overview.value?.registrations) }
])
const chatSeries = computed(() => [
  { name: '聊天条数', color: '#a78bfa', points: toPoints(overview.value?.chat) }
])

const topActive = computed(() => overview.value?.topActive || [])
</script>
