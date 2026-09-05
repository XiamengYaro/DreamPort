<template>
  <section class="py-16 px-4">
    <div class="max-w-6xl mx-auto">
      <h2 class="text-3xl font-bold text-white text-center mb-12">管理团队</h2>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-6">
        <div v-for="member in team" :key="member.name"
          class="card p-6 text-center card-hover">
          <div class="w-20 h-20 rounded-full mx-auto mb-4 overflow-hidden bg-gradient-to-br from-orange-400 to-amber-400 flex items-center justify-center">
            <img v-if="member.avatar" :src="member.avatar" :alt="member.name" class="w-full h-full object-cover"
              @error="handleAvatarError($event)" />
            <span v-else class="text-2xl font-bold text-white">{{ member.name.charAt(0) }}</span>
          </div>
          <h3 class="text-white font-semibold mb-1">{{ member.name }}</h3>
          <p class="text-orange-400 text-sm">{{ member.role }}</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  team: Array<{ name: string; role: string; avatar?: string }>
}>()

const handleAvatarError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
}
</script>
