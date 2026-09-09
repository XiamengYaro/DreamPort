<template>
  <Teleport to="body">
    <transition name="modal">
      <div
        v-if="open"
        class="modal-overlay fixed inset-0 z-50 flex items-center justify-center p-4"
      >
        <div class="card w-full p-6" :class="sizeClass">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="text-lg font-semibold text-white">{{ title }}</h3>
            <button class="text-stone-400 hover:text-white" @click="emit('close')">
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
import { computed, onMounted, onUnmounted, watch } from 'vue'
import AppIcon from '../AppIcon.vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
}>(), { size: 'md' })

const emit = defineEmits<{ close: [] }>()

// ESC 关闭(点空白不再退出弹窗)
const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') emit('close')
}
watch(() => props.open, (v) => {
  if (v) window.addEventListener('keydown', onKeydown)
  else window.removeEventListener('keydown', onKeydown)
})
onMounted(() => { if (props.open) window.addEventListener('keydown', onKeydown) })
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

const sizeClass = computed(() => ({
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-2xl',
  xl: 'max-h-[85vh] max-w-4xl overflow-y-auto'
})[props.size])
</script>
