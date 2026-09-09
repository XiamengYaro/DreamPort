<template>
  <div>
    <div v-if="!hasData" class="text-center py-8 text-stone-500 text-sm">暂无数据</div>
    <div v-else>
      <div class="relative" :style="{ height: heightPx + 'px' }">
        <svg :viewBox="`0 0 ${width} ${height}`" class="w-full h-full" preserveAspectRatio="none">
          <!-- 网格线 -->
          <line v-for="i in 4" :key="'g' + i"
            :x1="0" :y1="(height / 4) * i" :x2="width" :y2="(height / 4) * i"
            stroke="rgba(255,255,255,0.08)" stroke-width="1" />
          <!-- 每条序列:淡填充 + 折线 -->
          <g v-for="(s, si) in rendered" :key="'s' + si">
            <path :d="s.area" :fill="s.color" opacity="0.1" />
            <path :d="s.line" fill="none" :stroke="s.color" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" />
          </g>
        </svg>
        <!-- Y 轴标签 -->
        <div class="absolute left-0 top-0 bottom-0 flex flex-col justify-between text-xs text-stone-500 py-1 pointer-events-none">
          <span>{{ formatValue(yTop) }}</span>
          <span>{{ formatValue(yTop / 2) }}</span>
          <span>{{ formatValue(0) }}</span>
        </div>
      </div>
      <!-- 图例:名称 + 当前值 -->
      <div class="flex flex-wrap gap-x-5 gap-y-1 mt-2 text-xs text-stone-400">
        <span v-for="(s, si) in rendered" :key="'l' + si" class="inline-flex items-center gap-1.5">
          <span class="w-2.5 h-2.5 rounded-full inline-block" :style="{ background: s.color }"></span>
          {{ s.name }}<span class="text-white">{{ formatValue(s.latest) }}</span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用 SVG 折线图(多序列,无图表库):序列 [{name, color, points:[{t,v}]}],按时间域归一化 X 轴
import { computed } from 'vue'

interface Pt { t: number; v: number }
interface Series { name: string; color: string; points: Pt[] }

const props = withDefaults(defineProps<{
  series: Series[]
  height?: number
  yMax?: number
  unit?: string
}>(), { height: 200, unit: '' })

const width = 800
const height = 160

const heightPx = computed(() => props.height)

const hasData = computed(() => props.series.some(s => s.points.length > 0))

const domain = computed(() => {
  let min = Infinity, max = -Infinity
  for (const s of props.series) {
    for (const p of s.points) {
      if (p.t < min) min = p.t
      if (p.t > max) max = p.t
    }
  }
  if (min === Infinity) return { min: 0, max: 1 }
  if (min === max) { max = min + 1 }
  return { min, max }
})

const yTop = computed(() => {
  if (props.yMax != null) return props.yMax
  let max = 0
  for (const s of props.series) {
    for (const p of s.points) {
      if (p.v > max) max = p.v
    }
  }
  return Math.max(max * 1.15, 1)
})

const rendered = computed(() => {
  const { min, max } = domain.value
  return props.series.map(s => {
    const pts = s.points.map((p, i) => ({
      x: ((p.t - min) / (max - min)) * width,
      y: height - (p.v / yTop.value) * height
    }))
    // 审计修复同款:单点时除零产生 NaN,单点退化为水平短线
    if (pts.length === 1) {
      pts[0] = { x: 0, y: pts[0].y }
    }
    const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ')
    const area = pts.length > 1
      ? `${line} L ${width} ${height} L 0 ${height} Z`
      : ''
    const last = s.points[s.points.length - 1]
    return { name: s.name, color: s.color, line, area, latest: last ? last.v : 0 }
  })
})

function formatValue(v: number): string {
  if (v == null || Number.isNaN(v)) return '—'
  const abs = Math.abs(v)
  const text = abs >= 100 ? Math.round(v).toString() : v.toFixed(Math.abs(v) < 10 ? 2 : 1)
  return props.unit ? `${text}${props.unit}` : text
}
</script>
