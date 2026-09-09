<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-7xl mx-auto">
      <!-- 标题 -->
      <div class="card p-6 mb-6">
        <h1 class="text-2xl font-bold text-white">世界地图</h1>
        <p class="text-stone-400">{{ serverName }} - 实时地图</p>
      </div>

      <!-- 地图选择 -->
      <div v-if="mapOptions.length > 1" class="card p-4 mb-6">
        <div class="flex flex-wrap gap-2 items-center">
          <button v-for="(opt, i) in mapOptions" :key="i"
            @click="selectMap(i)"
            class="px-4 py-2 rounded-xl text-sm font-medium transition-colors duration-200 inline-flex items-center gap-2"
            :class="selectedMap === i
              ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20'
              : 'text-stone-400 hover:text-white hover:bg-white/5 border border-transparent'">
            {{ opt.name || '地图 ' + (i + 1) }}
            <span v-if="opt.type !== 'generic'" class="text-[10px] px-1.5 py-0.5 rounded-md bg-white/10 text-stone-300 uppercase">
              {{ opt.type }}
            </span>
          </button>
          <a v-if="currentUrl" :href="currentUrl" target="_blank" rel="noopener noreferrer"
            class="ml-auto px-4 py-2 rounded-xl text-sm font-medium bg-orange-500/15 text-orange-400 border border-orange-500/20 hover:bg-orange-500/25 transition-colors">
            打开地图 ↗
          </a>
        </div>
      </div>

      <div v-if="currentUrl" class="grid gap-6" :class="showSidebar ? 'lg:grid-cols-[1fr_280px]' : ''">
        <!-- 地图 iframe -->
        <div class="card overflow-hidden" style="height: calc(100vh - 280px);">
          <iframe
            :src="currentUrl"
            class="w-full h-full border-0"
            allowfullscreen
            loading="lazy"
            referrerpolicy="no-referrer">
          </iframe>
        </div>

        <!-- 在线位置侧栏(BlueMap/Dynmap) -->
        <div v-if="showSidebar" class="card p-4 flex flex-col overflow-hidden" style="height: calc(100vh - 280px);">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-semibold text-white">在线位置</h3>
            <button @click="loadLive" class="text-xs text-stone-400 hover:text-white" title="刷新">
              <svg class="w-4 h-4" :class="liveLoading ? 'animate-spin' : ''" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          </div>
          <EmptyState v-if="livePlayers.length === 0 && !liveLoading" text="暂无玩家位置数据" />
          <div class="flex-1 overflow-y-auto space-y-1.5 -mx-1 px-1">
            <button v-for="(p, i) in livePlayers" :key="i"
              @click="locate(p)"
              class="w-full flex items-center gap-2.5 p-2 rounded-xl bg-stone-900/40 border border-stone-800 hover:border-orange-500/40 hover:bg-white/5 transition-colors text-left">
              <AppAvatar :name="p.name" size-class="w-8 h-8 shrink-0" />
              <div class="flex-1 min-w-0">
                <div class="text-sm text-white truncate">{{ p.name }}</div>
                <div class="text-xs text-stone-500 truncate">{{ p.world }} · {{ Math.round(p.x) }}, {{ Math.round(p.y) }}, {{ Math.round(p.z) }}</div>
              </div>
            </button>
          </div>
          <p class="text-[11px] text-stone-600 mt-2">点击玩家可跳转地图定位</p>
        </div>
      </div>

      <!-- 未配置提示 -->
      <div v-else class="card p-12 text-center">
        <svg class="w-16 h-16 text-stone-600 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
        </svg>
        <h2 class="text-xl font-semibold text-white mb-2">世界地图未配置</h2>
        <p class="text-stone-400">管理员尚未配置地图地址</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, inject } from 'vue'
import api from '@/services/api'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const config = inject('config') as any
const serverName = ref('夏日小镇')
const selectedMap = ref(0)

interface MapOption {
  name: string
  url: string
  type: string
}

const mapOptions = ref<MapOption[]>([])
const livePlayers = ref<Array<{ name: string; world: string; x: number; y: number; z: number }>>([])
const liveMapId = ref<string | null>(null)
const liveLoading = ref(false)
let pollTimer: number | undefined

const current = computed(() => mapOptions.value[selectedMap.value])
const currentUrl = computed(() => current.value?.url || '')
const showSidebar = computed(() => !!currentUrl.value && current.value?.type !== 'generic')

function selectMap(i: number) {
  selectedMap.value = i
  livePlayers.value = []
  liveMapId.value = null
  scheduleLive()
}

onMounted(async () => {
  try {
    const res = await fetch('/api/config')
    const data = await res.json()
    if (data.success) {
      serverName.value = data.data.portal?.server_name || '夏日小镇'
      const portal = data.data.portal || {}
      if (Array.isArray(portal.map_items) && portal.map_items.length) {
        mapOptions.value = portal.map_items
          .filter((m: any) => (m.url || '').trim())
          .map((m: any) => ({ name: m.name || '', url: m.url, type: m.type || 'generic' }))
      } else if (portal.map_url) {
        // 旧格式兼容:map_url 竖线分隔
        const urls = String(portal.map_url).split('|').filter((s: string) => s.trim())
        mapOptions.value = urls.map((url: string, i: number) => ({
          name: urls.length === 1 ? '世界地图' : `地图 ${i + 1}`,
          url: url.trim(),
          type: 'generic'
        }))
      }
    }
  } catch (e) {}
  scheduleLive()
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})

function scheduleLive() {
  if (pollTimer) window.clearInterval(pollTimer)
  if (!showSidebar.value) return
  loadLive()
  pollTimer = window.setInterval(loadLive, 15_000)
}

async function loadLive() {
  if (!showSidebar.value) return
  liveLoading.value = true
  try {
    const r: any = await api.getMapLive(selectedMap.value)
    if (r.success) {
      livePlayers.value = r.data?.players || []
      liveMapId.value = r.data?.mapId ?? null
    }
  } catch (e) {
    // 拉取失败保持现状(空列表 → 侧栏显示暂无数据),不影响 iframe
  }
  liveLoading.value = false
}

/** 点击玩家 → 按地图类型生成定位深链(尽力而为,失败仅打开地图) */
function locate(p: { world: string; x: number; y: number; z: number }) {
  const base = currentUrl.value
  let link = base
  if (current.value?.type === 'bluemap') {
    const root = base.split('#')[0]
    link = liveMapId.value
      ? `${root}#?map=${encodeURIComponent(liveMapId.value)}&pos=${Math.round(p.x)}:${Math.round(p.y)}:${Math.round(p.z)}:0`
      : root
  } else if (current.value?.type === 'dynmap') {
    const sep = base.includes('?') ? '&' : '?'
    link = `${base}${sep}world=${encodeURIComponent(p.world)}&x=${Math.round(p.x)}&y=${Math.round(p.y)}&z=${Math.round(p.z)}`
  }
  window.open(link, '_blank', 'noopener')
}
</script>
