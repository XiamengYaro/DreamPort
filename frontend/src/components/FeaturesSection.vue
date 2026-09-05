<template>
  <section class="py-16 px-4 bg-stone-900/50">
    <div class="max-w-6xl mx-auto">
      <h2 class="text-3xl font-bold text-white text-center mb-12">服务器特色</h2>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div v-for="feature in features" :key="feature.title"
          class="card p-6 card-hover">
          <div class="text-4xl mb-4" v-html="feature.icon"></div>
          <h3 class="text-xl font-semibold text-white mb-2">{{ feature.title }}</h3>
          <p class="text-stone-400">{{ feature.description }}</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  features: Array<{ icon: string; title: string; description: string }>
}>()
</script>
