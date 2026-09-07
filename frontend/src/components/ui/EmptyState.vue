<template>
  <div class="py-8 text-center text-stone-500">
    <AppIcon v-if="icon" :name="icon" class="mx-auto mb-2 h-10 w-10 text-stone-500" />
    <div>{{ text }}</div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import AppIcon from '../AppIcon.vue'

withDefaults(defineProps<{
  text: string
  icon?: string
}>(), { icon: '' })
</script>
