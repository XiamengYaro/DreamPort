<template>
  <div class="fixed bottom-6 left-1/2 -translate-x-1/2 z-40">
    <div class="flex items-center gap-3 px-6 py-2.5 rounded-full liquid-glass shadow-lg">
      <span class="text-white font-medium text-sm drop-shadow">XMCraft</span>
      <span class="text-white/40">/</span>
      <span class="text-white/60 text-sm drop-shadow">© 2020-{{ currentYear }}</span>
      <a v-if="icp" :href="icpUrl" target="_blank" rel="noopener noreferrer" class="text-white/50 hover:text-orange-400 text-sm transition-colors drop-shadow">
        {{ icp }}
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  icp?: string
}>()

const currentYear = new Date().getFullYear()
const icpUrl = 'https://beian.miit.gov.cn'
</script>

<style scoped>
.liquid-glass {
  background: linear-gradient(135deg, var(--lg-tint-strong) 0%, var(--lg-tint) 100%);
  backdrop-filter: saturate(var(--lg-saturation)) blur(var(--lg-blur));
  -webkit-backdrop-filter: saturate(var(--lg-saturation)) blur(var(--lg-blur));
  border: 1px solid var(--lg-border);
  box-shadow: var(--lg-shadow);
}
</style>
