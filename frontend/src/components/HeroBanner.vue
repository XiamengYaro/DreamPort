<template>
  <div class="relative h-screen overflow-hidden">
    <!-- 轮播图片 -->
    <div class="absolute inset-0">
      <div v-for="(slide, index) in slides" :key="index"
        class="absolute inset-0 transition-opacity duration-1000 ease-in-out"
        :class="{ 'opacity-100': currentIndex === index, 'opacity-0': currentIndex !== index }">
        <img :src="slide.image" :alt="slide.title" class="w-full h-full object-cover"
          :loading="index === 0 ? 'eager' : 'lazy'"
          decoding="async"
          @error="handleImageError($event)" />
        <div class="absolute inset-0 bg-gradient-to-t from-stone-900/80 via-stone-900/40 to-transparent"></div>
      </div>
    </div>

    <!-- 文字内容 -->
    <div class="relative z-10 h-full flex flex-col items-center justify-center text-center px-4">
      <div>
        <img :src="logoUrl" alt="Logo" class="w-20 h-20 rounded-2xl mx-auto mb-6 shadow-2xl"
          loading="eager" decoding="async" />
        <h1 class="text-4xl md:text-6xl font-bold text-white mb-4 tracking-tight">
          {{ currentSlide.title }}
        </h1>
        <p class="text-lg md:text-xl text-white/80 mb-8 max-w-2xl">
          {{ currentSlide.subtitle }}
        </p>
        <div class="flex flex-wrap gap-4 justify-center">
          <router-link to="/whitelist" class="btn-primary text-lg px-8 py-4">
            申请白名单
          </router-link>
          <router-link to="/docs" class="btn-secondary text-lg px-8 py-4">
            查看文档
          </router-link>
        </div>
      </div>
    </div>

    <!-- 指示器 -->
    <div class="absolute bottom-8 left-1/2 -translate-x-1/2 flex gap-3 z-10">
      <button v-for="(_, index) in slides" :key="index"
        @click="goToSlide(index)"
        class="h-2 rounded-full transition-all duration-300"
        :class="currentIndex === index ? 'bg-orange-500 w-8' : 'bg-white/40 w-2 hover:bg-white/60'">
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

interface Slide {
  image: string
  title: string
  subtitle: string
}

const props = defineProps<{
  slides: Slide[]
  logoUrl?: string
}>()

const currentIndex = ref(0)
let timer: number | null = null

const currentSlide = computed(() => {
  return props.slides[currentIndex.value] || props.slides[0]
})

const goToSlide = (index: number) => {
  currentIndex.value = index
  resetTimer()
}

const nextSlide = () => {
  currentIndex.value = (currentIndex.value + 1) % props.slides.length
}

const resetTimer = () => {
  if (timer) clearInterval(timer)
  timer = window.setInterval(nextSlide, 5000)
}

const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="1920" height="600"%3E%3Crect fill="%23292524" width="1920" height="600"/%3E%3C/svg%3E'
}

onMounted(() => {
  if (props.slides.length > 1) {
    resetTimer()
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
