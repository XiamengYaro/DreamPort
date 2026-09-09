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
            @click="selectedMap = i"
            class="px-4 py-2 rounded-xl text-sm font-medium transition-colors duration-200"
            :class="selectedMap === i
              ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20'
              : 'text-stone-400 hover:text-white hover:bg-white/5'">
            {{ opt.name }}
          </button>
          <a v-if="currentUrl" :href="currentUrl" target="_blank" rel="noopener noreferrer"
            class="ml-auto px-4 py-2 rounded-xl text-sm font-medium bg-orange-500/15 text-orange-400 border border-orange-500/20 hover:bg-orange-500/25 transition-colors">
            打开地图 ↗
          </a>
        </div>
      </div>

      <!-- 地图 iframe -->
      <div v-if="currentUrl" class="card overflow-hidden" style="height: calc(100vh - 280px);">
        <iframe
          :src="currentUrl"
          class="w-full h-full border-0"
          allowfullscreen
          loading="lazy"
          referrerpolicy="no-referrer">
        </iframe>
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
import { ref, computed, onMounted, inject } from 'vue'

const config = inject('config') as any
const serverName = ref('夏日小镇')
const selectedMap = ref(0)

interface MapOption {
  name: string
  url: string
}

const mapOptions = ref<MapOption[]>([])

onMounted(async () => {
  try {
    const res = await fetch('/api/config')
    const data = await res.json()
    if (data.success) {
      serverName.value = data.data.portal?.server_name || '夏日小镇'
      const mapUrl = data.data.portal?.map_url || ''
      if (mapUrl) {
        // 支持多个地图地址（用 | 分隔）
        const urls = mapUrl.split('|').filter((s: string) => s.trim())
        if (urls.length === 1) {
          mapOptions.value = [{ name: '世界地图', url: urls[0].trim() }]
        } else {
          mapOptions.value = urls.map((url: string, i: number) => ({
            name: `地图 ${i + 1}`,
            url: url.trim()
          }))
        }
      }
    }
  } catch (e) {}
})

const currentUrl = computed(() => {
  if (mapOptions.value.length === 0) return ''
  return mapOptions.value[selectedMap.value]?.url || ''
})
</script>
