<template>
  <div class="relative text-center" :class="compact ? 'mb-4' : 'mb-12'">
    <span v-if="kicker"
      class="hidden md:block absolute left-1/2 -translate-x-1/2 -top-9 text-[6.5rem] leading-none font-serif italic text-white/5 select-none pointer-events-none whitespace-nowrap"
      aria-hidden="true">{{ kicker }}</span>
    <p v-if="kicker" class="relative flex items-center justify-center gap-2 text-[11px] tracking-[0.25em] uppercase text-orange-300/90 mb-2">
      <span class="inline-block w-1.5 h-1.5 rounded-full bg-orange-400"></span>{{ kicker }}
    </p>
    <h2 class="relative text-2xl sm:text-3xl font-bold text-white">{{ title }}</h2>
    <p v-if="subtitle" class="relative text-stone-400 text-sm tracking-wide mt-2">{{ subtitle }}</p>
  </div>
</template>

<script setup lang="ts">
/**
 * 杂志编辑风统一节头(与时光照片墙同款语言):
 * 小字 kicker(带橙点)+ 中文大标题 + 衬线斜体英文水印垫底;门户各区块共用。
 */
defineProps<{
  /** 英文小标(同时作衬线水印文案),如 ABOUT/TEAM/FEATURES/MOMENTS */
  kicker?: string
  title: string
  subtitle?: string
  /** 紧凑档(标题卡内嵌 tab 条等场景,收窄下边距) */
  compact?: boolean
}>()
</script>
