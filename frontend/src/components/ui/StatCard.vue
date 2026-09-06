<template>
  <div class="rounded-xl p-3 text-center" :class="boxClass">
    <div class="text-2xl font-bold" :class="valueClass">{{ value }}</div>
    <div class="text-xs text-stone-400">{{ label }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  value: string | number
  label: string
  /** 语义色:orange/emerald/amber/rose/blue/purple/cyan,缺省为白色数值+中性底 */
  tone?: string
  /** 紧凑模式(Dashboard 半宽卡内用) */
  compact?: boolean
}>(), { tone: '', compact: false })

const TONES: Record<string, { box: string; value: string }> = {
  orange: { box: 'bg-stone-800/50', value: 'text-orange-400' },
  emerald: { box: 'bg-stone-800/50', value: 'text-emerald-400' },
  amber: { box: 'bg-stone-800/50', value: 'text-amber-400' },
  rose: { box: 'bg-stone-800/50', value: 'text-rose-400' },
  blue: { box: 'bg-blue-500/10', value: 'text-blue-400' },
  purple: { box: 'bg-purple-500/10', value: 'text-purple-400' },
  cyan: { box: 'bg-cyan-500/10', value: 'text-cyan-400' },
}

const boxClass = computed(() => TONES[props.tone]?.box ?? 'bg-stone-800/50')
const valueClass = computed(() => TONES[props.tone]?.value ?? 'text-white')
</script>
