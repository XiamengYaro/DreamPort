<template>
  <div class="card p-6">
    <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
      <h3 class="text-lg font-semibold text-white">服务器资源监控</h3>
      <div class="flex gap-2 items-center">
        <select v-model="selectedServer" class="input" style="max-width: 200px">
          <option v-for="s in serverOptions" :key="s" :value="s">{{ s }}</option>
        </select>
        <select v-model="hours" class="input" style="max-width: 130px">
          <option :value="24">近 24 小时</option>
          <option :value="168">近 7 天</option>
        </select>
        <button class="btn-secondary text-sm" @click="load">刷新</button>
      </div>
    </div>

    <EmptyState v-if="!live && metrics.length === 0"
      text="暂无指标数据——游戏服需升级插件并开启 features.report-metrics" />
    <template v-else>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-5">
        <StatCard :value="latest.tps1m != null ? latest.tps1m.toFixed(1) : '—'" label="TPS（1 分钟）"
          :tone="latest.tps1m != null && latest.tps1m < 15 ? 'rose' : 'emerald'" />
        <StatCard :value="latest.avgTickMs != null ? latest.avgTickMs.toFixed(1) + 'ms' : '—'" label="平均 Tick 耗时" tone="blue" />
        <StatCard :value="latest.memUsedMb != null ? latest.memUsedMb + ' MB' : '—'" label="内存占用" tone="orange" />
        <StatCard :value="latest.cpuLoad != null ? (latest.cpuLoad * 100).toFixed(1) + '%' : '—'" label="CPU 占用" tone="purple" />
      </div>

      <div class="space-y-6">
        <div>
          <div class="text-sm text-stone-400 mb-2">TPS 趋势</div>
          <LineChart :series="tpsSeries" :y-max="20" :height="180" />
        </div>
        <div>
          <div class="text-sm text-stone-400 mb-2">内存趋势（MB）</div>
          <LineChart :series="memSeries" :height="180" unit="" />
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

const serverOptions = ref<string[]>([])
const selectedServer = ref('')
const hours = ref(24)
const metrics = ref<any[]>([])
const live = ref(false)

onMounted(async () => {
  try {
    const r: any = await api.getServerStatus()
    if (r.success) {
      const servers: any[] = r.data?.servers || []
      serverOptions.value = servers.map((s: any) => s.serverId)
      live.value = servers.length > 0 && !r.data?.stale
      const primary = servers.find((s: any) => s.role === 'primary')
      selectedServer.value = primary?.serverId || serverOptions.value[0] || ''
    }
  } catch (e) {
    console.error(e)
  }
  if (selectedServer.value) {
    await load()
  }
})

watch([selectedServer, hours], () => load())

async function load() {
  if (!selectedServer.value) return
  try {
    const r: any = await api.getServerMetrics(selectedServer.value, hours.value)
    if (r.success) {
      metrics.value = r.data?.list || []
    }
  } catch (e) {
    console.error(e)
  }
}

const latest = computed(() => {
  if (metrics.value.length === 0) return {} as any
  return metrics.value[metrics.value.length - 1]
})

const toPoints = (key: string) =>
  metrics.value
    .filter((m) => m[key] != null)
    .map((m) => ({ t: Number(m.time), v: Number(m[key]) }))

const tpsSeries = computed(() => [
  { name: 'TPS 1 分钟', color: '#fb923c', points: toPoints('tps1m') },
  { name: 'TPS 5 分钟', color: '#38bdf8', points: toPoints('tps5m') },
  { name: 'TPS 15 分钟', color: '#a78bfa', points: toPoints('tps15m') }
])

const memSeries = computed(() => [
  { name: '已用内存', color: '#fb923c', points: toPoints('memUsedMb') },
  { name: '最大内存', color: '#475569', points: toPoints('memMaxMb') }
])
</script>
