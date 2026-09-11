<template>
  <section class="py-16 px-4">
    <div class="max-w-6xl mx-auto">
      <SectionHeader kicker="TEAM" title="管理团队" />
      <div class="grid grid-cols-2 md:grid-cols-4 gap-5">
        <div v-for="member in team" :key="member.name"
          class="group rounded-2xl p-6 text-center ring-1 ring-white/10 bg-stone-900/30 transition-all duration-300 hover:ring-orange-400/40 hover:bg-stone-900/50">
          <div class="w-20 h-20 rounded-full mx-auto mb-4 overflow-hidden ring-2 ring-white/10 group-hover:ring-orange-400/40 transition-all duration-300 group-hover:scale-105">
            <AppAvatar :name="member.name" :avatar-url="member.avatar || null" size-class="w-20 h-20" />
          </div>
          <h3 class="text-white font-semibold mb-1.5">{{ member.name }}</h3>
          <p class="inline-flex items-center gap-1.5 text-[11px] tracking-[0.18em] uppercase text-orange-300/90">
            <span class="inline-block w-1 h-1 rounded-full bg-orange-400"></span>{{ member.role }}
          </p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import AppAvatar from './ui/AppAvatar.vue'
import SectionHeader from './SectionHeader.vue'
defineProps<{
  team: Array<{ name: string; role: string; avatar?: string }>
}>()
</script>
