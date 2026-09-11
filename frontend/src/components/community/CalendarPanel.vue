<template>
  <div class="space-y-5">
    <!-- 月历 -->
    <div class="card p-5">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-base font-semibold text-white">{{ year }} 年 {{ month }} 月</h3>
        <div class="flex gap-1.5">
          <button class="btn-secondary text-xs px-2.5 py-1" @click="prevMonth">←</button>
          <button class="btn-secondary text-xs px-2.5 py-1" @click="nextMonth">→</button>
        </div>
      </div>
      <div class="grid grid-cols-7 gap-1 text-center text-[11px] text-stone-500 mb-1">
        <span v-for="d in ['日', '一', '二', '三', '四', '五', '六']" :key="d">{{ d }}</span>
      </div>
      <div class="grid grid-cols-7 gap-1">
        <div v-for="(cell, i) in calendarCells" :key="i"
          class="aspect-square rounded-lg flex flex-col items-center justify-start pt-1 text-xs"
          :class="cell.inMonth ? 'bg-stone-900/40 text-stone-300' : 'text-stone-700'">
          <span :class="cell.isToday ? 'bg-orange-500 text-white rounded-full w-5 h-5 flex items-center justify-center' : ''">{{ cell.day }}</span>
          <div class="flex flex-wrap gap-0.5 mt-0.5 justify-center">
            <span v-for="e in cell.events" :key="e.id"
              class="w-1.5 h-1.5 rounded-full bg-orange-400"
              :title="e.title"></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 活动列表 -->
    <div class="space-y-3">
      <EmptyState v-if="monthEvents.length === 0" icon="calendar" text="本月暂无活动" />
      <div v-for="e in monthEvents" :key="e.id" class="card p-5">
        <div class="flex flex-wrap items-center gap-2 mb-1.5">
          <h3 class="text-base font-semibold text-white">{{ e.title }}</h3>
          <span class="text-xs px-2 py-0.5 rounded"
            :class="isFuture(e) ? 'bg-emerald-500/15 text-emerald-400' : 'bg-stone-600/40 text-stone-400'">
            {{ isFuture(e) ? '即将开始' : '已结束' }}
          </span>
        </div>
        <p v-if="e.description" class="text-sm text-stone-400 mb-2">{{ e.description }}</p>
        <div class="flex items-center justify-between">
          <div class="text-xs text-stone-500">
            {{ new Date(e.event_at).toLocaleString('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }}
            · {{ e.signups || 0 }} 人报名
          </div>
          <button v-if="isLoggedIn && isFuture(e)" class="text-xs px-3 py-1.5 rounded-lg transition-colors"
            :class="e.signedUp ? 'bg-emerald-500/15 text-emerald-400' : 'bg-orange-500/15 text-orange-400 hover:bg-orange-500/25'"
            @click="toggleSignup(e)">
            {{ e.signedUp ? '✓ 已报名(取消)' : '报名' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, inject } from 'vue'
import api from '@/services/api'
import EmptyState from '@/components/ui/EmptyState.vue'

const notify = inject('notify') as any
const isLoggedIn = computed(() => !!localStorage.getItem('token'))

const events = ref<any[]>([])
const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth() + 1)

onMounted(load)
watch([year, month], () => {}, { deep: true })

async function load() {
  try {
    const r: any = await api.getEvents()
    if (r.success) events.value = r.data?.events || []
  } catch (e) { console.error(e) }
}

const monthEvents = computed(() =>
  events.value.filter((e: any) => {
    const d = new Date(e.event_at)
    return d.getFullYear() === year.value && d.getMonth() + 1 === month.value
  })
)

const calendarCells = computed(() => {
  const first = new Date(year.value, month.value - 1, 1)
  const startPad = first.getDay()
  const daysInMonth = new Date(year.value, month.value, 0).getDate()
  const today = new Date()
  const cells: Array<{ day: number; inMonth: boolean; isToday: boolean; events: any[] }> = []
  for (let i = 0; i < startPad; i++) cells.push({ day: 0, inMonth: false, isToday: false, events: [] })
  for (let d = 1; d <= daysInMonth; d++) {
    const dayEvents = monthEvents.value.filter((e: any) => {
      const ed = new Date(e.event_at)
      return ed.getFullYear() === year.value && ed.getMonth() + 1 === month.value && ed.getDate() === d
    })
    cells.push({
      day: d,
      inMonth: true,
      isToday: today.getFullYear() === year.value && today.getMonth() + 1 === month.value && today.getDate() === d,
      events: dayEvents
    })
  }
  return cells
})

function prevMonth() {
  if (month.value === 1) { year.value--; month.value = 12 } else month.value--
}
function nextMonth() {
  if (month.value === 12) { year.value++; month.value = 1 } else month.value++
}

async function toggleSignup(e: any) {
  try {
    const r: any = await api.signupEvent(e.id)
    if (r.success) { notify?.success(r.message || '操作成功'); await load() } else notify?.error(r.message || '操作失败')
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

const isFuture = (e: any) => (e?.event_at || 0) >= Date.now()
</script>
