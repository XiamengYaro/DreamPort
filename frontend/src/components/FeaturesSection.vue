<template>
  <section class="py-16 px-4 bg-stone-900/30">
    <div class="max-w-6xl mx-auto">
      <SectionHeader kicker="FEATURES" title="服务器特色" />

      <!-- bento 不等宽网格:首卡跨两列,打破等宽呆板 -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-5">
        <div v-for="(feature, i) in features" :key="feature.title"
          class="group rounded-2xl p-6 ring-1 ring-white/10 bg-stone-900/40 transition-all duration-300 hover:ring-orange-400/40 hover:bg-stone-900/60"
          :class="i === 0 ? 'md:col-span-2' : ''">
          <div class="flex items-start gap-4">
            <div class="w-11 h-11 shrink-0 rounded-xl bg-orange-500/10 ring-1 ring-orange-500/20 flex items-center justify-center text-orange-400 transition-colors group-hover:bg-orange-500/20">
              <AppIcon :name="feature.icon" class="w-6 h-6" />
            </div>
            <div>
              <h3 class="text-lg font-semibold text-white group-hover:text-orange-300 transition-colors">{{ feature.title }}</h3>
              <p class="text-stone-400 text-sm mt-1.5 leading-relaxed">{{ feature.description }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import SectionHeader from './SectionHeader.vue'
defineProps<{
  features: Array<{ icon: string; title: string; description: string }>
}>()
</script>
