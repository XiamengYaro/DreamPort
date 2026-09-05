<template>
  <div class="card p-6">
    <h3 class="text-lg font-semibold text-white mb-4">在线人数趋势（24小时）</h3>
    <div v-if="loading" class="text-center py-8 text-stone-500">
      <svg class="animate-spin h-8 w-8 mx-auto mb-2 text-orange-400" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
      加载中...
    </div>
    <div v-else-if="data.length === 0" class="text-center py-8 text-stone-500">暂无数据</div>
    <div v-else class="relative" style="height: 200px;">
      <svg :viewBox="`0 0 ${width} ${height}`" class="w-full h-full" preserveAspectRatio="none">
        <!-- 网格线 -->
        <line v-for="i in 5" :key="'h'+i"
          :x1="0" :y1="(height / 5) * i" :x2="width" :y2="(height / 5) * i"
          stroke="rgba(255,255,255,0.1)" stroke-width="1" />
        
        <!-- 填充区域 -->
        <path :d="areaPath" fill="url(#gradient)" opacity="0.3" />
        
        <!-- 折线 -->
        <path :d="linePath" fill="none" stroke="#fb923c" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        
        <!-- 数据点 -->
        <circle v-for="(point, i) in points" :key="i"
          :cx="point.x" :cy="point.y" r="3"
          fill="#fb923c" class="opacity-0 hover:opacity-100 transition-opacity" />
        
        <!-- 渐变定义 -->
        <defs>
          <linearGradient id="gradient" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#fb923c" stop-opacity="0.4" />
            <stop offset="100%" stop-color="#fb923c" stop-opacity="0" />
          </linearGradient>
        </defs>
      </svg>
      
      <!-- Y轴标签 -->
      <div class="absolute left-0 top-0 bottom-0 flex flex-col justify-between text-xs text-stone-500 py-2">
        <span>{{ maxY }}</span>
        <span>{{ Math.floor(maxY / 2) }}</span>
        <span>0</span>
      </div>
      
      <!-- X轴标签 -->
      <div class="flex justify-between text-xs text-stone-500 mt-2">
        <span>24h前</span>
        <span>18h</span>
        <span>12h</span>
        <span>6h</span>
        <span>现在</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/services/api'

const loading = ref(false)
const data = ref<Array<{players: number; time: number}>>([])
const width = 800
const height = 160

onMounted(async () => {
  await loadData()
})

const loadData = async () => {
  loading.value = true
  try {
    const r: any = await api.getPlayerHistory()
    if (r.success) {
      data.value = r.data.list || []
    }
  } catch (e) {
    console.error('Failed to load player history:', e)
  }
  loading.value = false
}

const maxY = computed(() => {
  if (data.value.length === 0) return 10
  return Math.max(...data.value.map(d => d.players), 10)
})

const points = computed(() => {
  if (data.value.length === 0) return []
  const len = data.value.length
  return data.value.map((d, i) => ({
    x: (i / (len - 1)) * width,
    y: height - (d.players / maxY.value) * height
  }))
})

const linePath = computed(() => {
  if (points.value.length === 0) return ''
  return points.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
})

const areaPath = computed(() => {
  if (points.value.length === 0) return ''
  const line = points.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
  return `${line} L ${width} ${height} L 0 ${height} Z`
})
</script>
