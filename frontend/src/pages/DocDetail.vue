<template>
  <div class="min-h-screen p-4 pt-24 pb-20">
    <div class="max-w-4xl mx-auto">
      <!-- 返回按钮 -->
      <router-link to="/docs" class="inline-flex items-center gap-2 text-stone-400 hover:text-white mb-6 transition-colors">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
        </svg>
        返回文档列表
      </router-link>

      <!-- Loading -->
      <div v-if="loading" class="card p-12 text-center">
        <div class="inline-flex items-center gap-3 text-stone-400">
          <svg class="w-6 h-6 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.641z"></path>
          </svg>
          <span>加载文档内容...</span>
        </div>
      </div>

      <!-- 文档内容 -->
      <div v-else-if="content" class="card p-8">
        <div class="prose prose-invert max-w-none" v-html="renderedContent"></div>
      </div>

      <!-- 错误 -->
      <div v-else class="card p-12 text-center text-stone-500">
        <div class="text-4xl mb-4">❌</div>
        <div>文档加载失败</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { renderMarkdown } from '@/lib/markdown'

const route = useRoute()
const loading = ref(true)
const content = ref('')

const renderedContent = computed(() => {
  if (!content.value) return ''
  return renderMarkdown(content.value)
})

const loadDoc = async (slug: string) => {
  loading.value = true
  content.value = ''
  try {
    const res = await fetch(`/api/docs/${slug}`)
    const data = await res.json()
    if (data.success) {
      content.value = data.data.content || ''
    }
  } catch (e) {
    console.error('Failed to load doc:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDoc(route.params.slug as string)
})

watch(() => route.params.slug, (newSlug) => {
  if (newSlug) loadDoc(newSlug as string)
})
</script>

<style scoped>
.prose :deep(h1) {
  @apply text-3xl font-bold text-white mb-6 mt-8 first:mt-0;
}

.prose :deep(h2) {
  @apply text-2xl font-bold text-white mb-4 mt-6;
}

.prose :deep(h3) {
  @apply text-xl font-semibold text-white mb-3 mt-4;
}

.prose :deep(p) {
  @apply text-stone-300 mb-4 leading-relaxed;
}

.prose :deep(ul), .prose :deep(ol) {
  @apply text-stone-300 mb-4 pl-6;
}

.prose :deep(li) {
  @apply mb-2;
}

.prose :deep(code) {
  @apply bg-stone-800 text-orange-400 px-2 py-1 rounded text-sm;
}

.prose :deep(pre) {
  @apply bg-stone-800 rounded-xl p-4 overflow-x-auto mb-4;
}

.prose :deep(pre code) {
  @apply bg-transparent p-0;
}

.prose :deep(a) {
  @apply text-orange-400 hover:text-orange-300;
}

.prose :deep(blockquote) {
  @apply border-l-4 border-orange-500 pl-4 italic text-stone-400;
}

.prose :deep(table) {
  @apply w-full border-collapse mb-4;
}

.prose :deep(th), .prose :deep(td) {
  @apply px-4 py-2 border border-stone-700 text-stone-300;
}

.prose :deep(th) {
  @apply bg-stone-800;
}
</style>
