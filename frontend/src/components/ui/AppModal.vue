<template>
  <Teleport to="body">
    <transition name="modal">
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
        @click.self="$emit('close')"
      >
        <div class="card w-full p-6" :class="sizeClass">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="text-lg font-semibold text-white">{{ title }}</h3>
            <button class="text-stone-400 hover:text-white" @click="$emit('close')">
              <AppIcon name="x-mark" class="h-5 w-5" />
            </button>
          </div>
          <slot />
          <div v-if="$slots.footer" class="mt-6 flex justify-end gap-2">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '../AppIcon.vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
}>(), { size: 'md' })

defineEmits<{ close: [] }>()

const sizeClass = computed(() => ({
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-2xl',
  xl: 'max-h-[85vh] max-w-4xl overflow-y-auto'
})[props.size])
</script>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active > div,
.modal-leave-active > div {
  transition: transform 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-from > div,
.modal-leave-to > div {
  transform: scale(0.96);
}
</style>
