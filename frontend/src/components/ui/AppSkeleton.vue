<template>
  <!-- 表格骨架:多行脉冲条 -->
  <div v-if="variant === 'table'" class="space-y-2" aria-busy="true">
    <div v-for="i in rows" :key="i" class="h-11 rounded-lg bg-stone-700/40 animate-pulse"
      :style="{ animationDelay: `${i * 60}ms` }"></div>
  </div>

  <!-- 卡片网格骨架 -->
  <div v-else-if="variant === 'cards'" class="grid gap-4" :class="gridClass" aria-busy="true">
    <div v-for="i in rows" :key="i" class="rounded-xl bg-stone-700/40 animate-pulse"
      :style="{ height: `${cardHeight}px`, animationDelay: `${i * 60}ms` }"></div>
  </div>

  <!-- 轻量居中:脉动点 + 文案(替代转圈 spinner) -->
  <div v-else class="flex items-center justify-center gap-2.5 py-8 text-stone-500 text-sm" aria-busy="true">
    <span class="flex gap-1">
      <span v-for="i in 3" :key="i" class="w-1.5 h-1.5 rounded-full bg-orange-400/80 animate-pulse"
        :style="{ animationDelay: `${i * 150}ms` }"></span>
    </span>
    {{ label }}
  </div>
</template>

<script setup lang="ts">
/**
 * 全局骨架屏(加载占位,统一替代 spinner 转圈):
 * - table:多行脉冲条(表格/列表加载)
 * - cards:网格脉冲块(卡片墙加载,gridClass 控制列数)
 * - pulse:居中三点脉动+文案(轻量/行内加载)
 * 交错延迟避免整批同频闪烁。
 */
withDefaults(defineProps<{
  variant?: 'table' | 'cards' | 'pulse'
  rows?: number
  cardHeight?: number
  gridClass?: string
  label?: string
}>(), {
  variant: 'table',
  rows: 5,
  cardHeight: 120,
  gridClass: 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3',
  label: '加载中...'
})
</script>
