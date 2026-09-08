<template>
  <div class="min-h-screen p-4 pt-24 pb-20">
    <div class="max-w-7xl mx-auto flex gap-6">
      <!-- 左侧文档列表 -->
      <aside class="w-64 flex-shrink-0 hidden lg:block">
        <div class="card p-4 sticky top-20 max-h-[calc(100vh-6rem)] overflow-y-auto">
          <h2 class="text-lg font-semibold text-white mb-4">服务器文档</h2>
          
          <!-- Loading -->
          <div v-if="docsLoading" class="space-y-2">
            <div v-for="i in 4" :key="i" class="h-10 bg-stone-700/50 rounded-lg animate-pulse"></div>
          </div>

          <!-- 文档分类列表 -->
          <nav v-else class="space-y-3">
            <!-- 分类文档 -->
            <div v-for="category in categories" :key="category.dirName">
              <button 
                @click="toggleCategory(category.dirName)"
                class="w-full flex items-center justify-between px-2 py-1.5 text-sm font-medium text-stone-400 hover:text-white transition-colors"
              >
                <span class="flex items-center gap-2">
                  <svg class="w-4 h-4 transition-transform" :class="{ 'rotate-90': expandedCategories[category.dirName] }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                  </svg>
                  {{ category.displayName || category.name }}
                </span>
                <span class="text-xs text-stone-500">{{ category.docs.length }}</span>
              </button>
              
              <div v-show="expandedCategories[category.dirName]" class="ml-4 space-y-0.5 mt-1">
                <button
                  v-for="doc in category.docs"
                  :key="doc.filename"
                  @click="selectDoc(doc.filename)"
                  class="w-full text-left px-3 py-2 rounded-lg text-sm transition-all duration-200"
                  :class="selectedSlug === doc.filename
                    ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20'
                    : 'text-stone-400 hover:text-white hover:bg-white/5'"
                >
                  {{ doc.title }}
                </button>
              </div>
            </div>

            <!-- 未分类文档 -->
            <div v-if="uncategorizedDocs.length > 0" class="border-t border-stone-700 pt-3 mt-3">
              <button
                v-for="doc in uncategorizedDocs"
                :key="doc.filename"
                @click="selectDoc(doc.filename)"
                class="w-full text-left px-3 py-2 rounded-lg text-sm transition-all duration-200"
                :class="selectedSlug === doc.filename
                  ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20'
                  : 'text-stone-400 hover:text-white hover:bg-white/5'"
              >
                {{ doc.title }}
              </button>
            </div>
          </nav>
        </div>
      </aside>

      <!-- 右侧内容区 -->
      <main class="flex-1 min-w-0">
        <!-- 移动端文档选择 -->
        <div class="lg:hidden mb-4">
          <select
            v-model="selectedSlug"
            @change="onSelectChange"
            class="input w-full"
          >
            <option value="">选择文档...</option>
            <optgroup v-for="category in categories" :key="category.dirName" :label="category.name">
              <option v-for="doc in category.docs" :key="doc.filename" :value="doc.filename">
                {{ doc.title }}
              </option>
            </optgroup>
            <optgroup v-if="uncategorizedDocs.length > 0" label="其他">
              <option v-for="doc in uncategorizedDocs" :key="doc.filename" :value="doc.filename">
                {{ doc.title }}
              </option>
            </optgroup>
          </select>
        </div>

        <!-- Loading -->
        <div v-if="contentLoading" class="card p-12 text-center">
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

        <!-- 文档首页 -->
        <div v-else class="card p-8">
          <div class="text-center mb-8">
            <div class="text-5xl mb-4">
              <svg class="w-12 h-12 text-orange-400 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
              </svg>
            </div>
            <h2 class="text-2xl font-bold text-white mb-2">服务器文档中心</h2>
            <p class="text-stone-400">欢迎查阅 {{ brand.full }} 服务器文档，请从左侧选择一个文档开始阅读</p>
          </div>

          <!-- 分类卡片 -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div v-for="category in categories" :key="category.dirName" class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
              <h3 class="text-white font-medium mb-3">{{ category.name }}</h3>
              <div class="space-y-2">
                <button
                  v-for="doc in category.docs"
                  :key="doc.filename"
                  @click="selectDoc(doc.filename)"
                  class="w-full text-left px-3 py-2 rounded-lg text-sm text-stone-400 hover:text-white hover:bg-white/5 transition-all"
                >
                  {{ doc.title }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useBrand } from '@/lib/brand'
const brand = useBrand()
import { ref, computed, onMounted } from 'vue'
import { renderMarkdown } from '@/lib/markdown'

interface Doc {
  filename: string
  title: string
}

interface Category {
  name: string
  dirName: string
  docs: Doc[]
}

const docsLoading = ref(true)
const contentLoading = ref(false)
const categories = ref<Category[]>([])
const uncategorizedDocs = ref<Doc[]>([])
const selectedSlug = ref('')
const content = ref('')
const expandedCategories = ref<Record<string, boolean>>({})

const renderedContent = computed(() => {
  if (!content.value) return ''
  return renderMarkdown(content.value)
})

const loadDocs = async () => {
  docsLoading.value = true
  try {
    const res = await fetch('/api/docs')
    const data = await res.json()
    if (data.success) {
      categories.value = data.data.categories || []
      uncategorizedDocs.value = data.data.uncategorized || []
      
      // 默认展开第一个分类
      if (categories.value.length > 0) {
        expandedCategories.value[categories.value[0].dirName] = true
      }
    }
  } catch (e) {
    console.error('Failed to load docs:', e)
  } finally {
    docsLoading.value = false
  }
}

const loadDoc = async (slug: string) => {
  if (!slug) {
    content.value = ''
    return
  }
  contentLoading.value = true
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
    contentLoading.value = false
  }
}

const selectDoc = (slug: string) => {
  selectedSlug.value = slug
  loadDoc(slug)
}

const onSelectChange = () => {
  if (selectedSlug.value) {
    loadDoc(selectedSlug.value)
  } else {
    content.value = ''
  }
}

const toggleCategory = (dirName: string) => {
  expandedCategories.value[dirName] = !expandedCategories.value[dirName]
}

onMounted(async () => {
  await loadDocs()
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
