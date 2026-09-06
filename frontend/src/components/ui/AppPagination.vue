<template>
  <div v-if="pages > 1" class="flex items-center justify-between pt-2">
    <button class="btn-ghost text-sm" :disabled="page <= 1" @click="$emit('change', page - 1)">上一页</button>
    <span class="text-sm text-stone-400">第 {{ page }} / {{ pages }} 页</span>
    <button class="btn-ghost text-sm" :disabled="page >= pages" @click="$emit('change', page + 1)">下一页</button>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  page: number
  pages: number
}>(), { page: 1, pages: 1 })

defineEmits<{ change: [page: number] }>()
</script>
