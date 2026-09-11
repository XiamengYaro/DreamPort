<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-7xl mx-auto">
      <SectionHeader kicker="DOCS" title="服务器文档" subtitle="游戏规则 · 玩法指南 · 尽在文档中心" />

      <div class="flex gap-6">
        <!-- 左侧文档列表 -->
        <aside class="w-64 flex-shrink-0 hidden lg:block">
          <div class="card p-4 sticky top-20 max-h-[calc(100vh-6rem)] overflow-y-auto">
            <!-- 实时搜索(前端过滤标题;含匹配的分类自动展开) -->
            <input v-model="searchQuery" type="text" class="input text-sm mb-3" placeholder="搜索文档..." />

            <div v-if="docsLoading" class="space-y-2">
              <div v-for="i in 4" :key="i" class="h-10 bg-stone-700/50 rounded-lg animate-pulse"></div>
            </div>

            <nav v-else class="space-y-3">
              <!-- 分类文档 -->
              <div v-for="category in filteredCategories" :key="category.dirName">
                <button
                  @click="toggleCategory(category.dirName)"
                  class="w-full flex items-center justify-between px-2 py-1.5 text-sm font-medium text-stone-400 hover:text-white transition-colors"
                >
                  <span class="flex items-center gap-2">
                    <svg class="w-4 h-4 transition-transform" :class="{ 'rotate-90': isExpanded(category) }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                    </svg>
                    {{ category.displayName || category.name }}
                  </span>
                  <span class="text-xs text-stone-500">{{ category.docs.length }}</span>
                </button>

                <div v-show="isExpanded(category)" class="ml-4 space-y-0.5 mt-1">
                  <button
                    v-for="doc in category.docs"
                    :key="doc.filename"
                    @click="selectDoc(doc.filename)"
                    class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors duration-200"
                    :class="selectedSlug === doc.filename
                      ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20'
                      : 'text-stone-400 hover:text-white hover:bg-white/5'"
                  >
                    {{ doc.title }}
                  </button>
                </div>
              </div>

              <!-- 未分类文档 -->
              <div v-if="filteredUncategorized.length > 0" class="border-t border-stone-700 pt-3 mt-3">
                <button
                  v-for="doc in filteredUncategorized"
                  :key="doc.filename"
                  @click="selectDoc(doc.filename)"
                  class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors duration-200"
                  :class="selectedSlug === doc.filename
                    ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20'
                    : 'text-stone-400 hover:text-white hover:bg-white/5'"
                >
                  {{ doc.title }}
                </button>
              </div>

              <p v-if="filteredCategories.length === 0 && filteredUncategorized.length === 0" class="text-sm text-stone-500 text-center py-4">
                没有匹配「{{ searchQuery }}」的文档
              </p>
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
          <div v-if="contentLoading" class="card p-6">
            <AppSkeleton variant="text" :rows="6" />
          </div>

          <!-- 文档内容 -->
          <div v-else-if="content" class="card p-8">
            <!-- 元信息条 -->
            <div class="flex flex-wrap items-center gap-2 mb-5 pb-4 border-b border-white/10">
              <span class="inline-flex items-center gap-1.5 text-[11px] tracking-[0.18em] uppercase text-orange-300/90">
                <span class="inline-block w-1.5 h-1.5 rounded-full bg-orange-400"></span>{{ currentCategoryName }}
              </span>
              <span class="text-stone-500 text-sm">/</span>
              <span class="text-white font-semibold">{{ currentDocTitle }}</span>
            </div>

            <!-- 目录(可折叠,h2/h3 锚点) -->
            <details v-if="toc.length >= 2" class="mb-6 rounded-xl bg-stone-900/40 ring-1 ring-white/10">
              <summary class="px-4 py-2.5 text-sm text-stone-300 cursor-pointer select-none hover:text-white">
                目录({{ toc.length }})
              </summary>
              <nav class="px-4 pb-3 space-y-1">
                <button v-for="item in toc" :key="item.id"
                  @click="scrollToToc(item.id)"
                  class="block w-full text-left text-sm transition-colors"
                  :class="item.level === 2 ? 'text-stone-300 hover:text-orange-300' : 'text-stone-500 hover:text-orange-300 pl-5'">
                  {{ item.text }}
                </button>
              </nav>
            </details>

            <div class="prose prose-invert max-w-none" v-html="renderedContent"></div>

            <!-- 上一篇 / 下一篇(全库扁平顺序,跨分类) -->
            <div class="grid grid-cols-2 gap-3 mt-8 pt-5 border-t border-white/10">
              <button v-if="prevDoc" @click="goDoc(prevDoc)"
                class="text-left p-3 rounded-xl bg-stone-900/40 border border-stone-800 hover:border-orange-500/40 transition-colors min-w-0">
                <div class="text-[10px] text-stone-500 tracking-wider uppercase mb-0.5">← 上一篇</div>
                <div class="text-sm text-white truncate">{{ prevDoc.title }}</div>
              </button>
              <span v-else></span>
              <button v-if="nextDoc" @click="goDoc(nextDoc)"
                class="text-right p-3 rounded-xl bg-stone-900/40 border border-stone-800 hover:border-orange-500/40 transition-colors min-w-0">
                <div class="text-[10px] text-stone-500 tracking-wider uppercase mb-0.5">下一篇 →</div>
                <div class="text-sm text-white truncate">{{ nextDoc.title }}</div>
              </button>
            </div>
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
                    class="w-full text-left px-3 py-2 rounded-lg text-sm text-stone-400 hover:text-white hover:bg-white/5 transition-colors"
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
  </div>
</template>

<script setup lang="ts">
import { useBrand } from '@/lib/brand'
const brand = useBrand()
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { renderMarkdownWithToc } from '@/lib/markdown'
import api from '@/services/api'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import SectionHeader from '@/components/SectionHeader.vue'

const route = useRoute()
const router = useRouter()

interface Doc {
  filename: string
  title: string
}

interface Category {
  name: string
  dirName: string
  displayName?: string
  docs: Doc[]
}

const docsLoading = ref(true)
const contentLoading = ref(false)
const categories = ref<Category[]>([])
const uncategorizedDocs = ref<Doc[]>([])
const selectedSlug = ref('')
const content = ref('')
const expandedCategories = ref<Record<string, boolean>>({})
const searchQuery = ref('')
const currentDocTitle = ref('')
const currentCategoryName = ref('')
const tocOpen = ref(false)

// ---------- 渲染与 TOC ----------
const rendered = computed(() => content.value ? renderMarkdownWithToc(content.value) : { html: '', toc: [] })
const renderedContent = computed(() => rendered.value.html)
const toc = computed(() => rendered.value.toc)
const scrollToToc = (id: string) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })

// ---------- 侧栏搜索(前端过滤标题;含匹配分类自动展开) ----------
const searchActive = computed(() => searchQuery.value.trim().length > 0)
const hit = (doc: Doc) => doc.title.toLowerCase().includes(searchQuery.value.trim().toLowerCase())
const filteredCategories = computed(() => {
  if (!searchActive.value) return categories.value
  return categories.value
    .map(c => ({ ...c, docs: c.docs.filter(hit) }))
    .filter(c => c.docs.length > 0)
})
const filteredUncategorized = computed(() =>
  searchActive.value ? uncategorizedDocs.value.filter(hit) : uncategorizedDocs.value)
const isExpanded = (category: Category) =>
  searchActive.value || !!expandedCategories.value[category.dirName]

// ---------- 全库扁平顺序(上下篇用):分类顺序 → 分类内文件顺序 ----------
const flatDocs = computed(() => {
  const list: Array<{ filename: string; title: string; categoryName: string }> = []
  for (const c of categories.value) {
    for (const d of c.docs) list.push({ filename: d.filename, title: d.title, categoryName: c.displayName || c.name })
  }
  for (const d of uncategorizedDocs.value) list.push({ filename: d.filename, title: d.title, categoryName: '其他' })
  return list
})
const currentIndex = computed(() => flatDocs.value.findIndex(d => d.filename === selectedSlug.value))
const prevDoc = computed(() => (currentIndex.value > 0 ? flatDocs.value[currentIndex.value - 1] : null))
const nextDoc = computed(() => (currentIndex.value >= 0 && currentIndex.value < flatDocs.value.length - 1
  ? flatDocs.value[currentIndex.value + 1] : null))

// ---------- 数据加载 ----------
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
    // 修复审计：裸 fetch 收口至 api 层(Rules §7)
    const res: any = await api.readDocBySlug(slug)
    if (res.success) {
      content.value = res.data.content || ''
      currentDocTitle.value = res.data.title || slug
      // 分类名:接口返回 dirName → 映射 displayName
      const cat = categories.value.find(c => c.dirName === res.data.category || c.name === res.data.category)
      currentCategoryName.value = cat ? (cat.displayName || cat.name) : (res.data.category || '')
      // 含该文档的分类自动展开
      if (cat) expandedCategories.value[cat.dirName] = true
      tocOpen.value = false
    }
  } catch (e) {
    console.error('Failed to load doc:', e)
  } finally {
    contentLoading.value = false
  }
}

const categoryOf = (slug: string) => {
  for (const c of categories.value) {
    if (c.docs.some(d => d.filename === slug)) return c.dirName
  }
  return undefined
}

/** URL 同步:侧栏/移动端选择用 replace(不留历史) */
const selectDoc = (slug: string) => {
  selectedSlug.value = slug
  loadDoc(slug)
  router.replace({ path: `/docs/${encodeURIComponent(slug)}`, query: { category: categoryOf(slug) } })
}

/** 上/下篇用 push(浏览器可回退) */
const goDoc = (doc: { filename: string; title: string; categoryName: string }) => {
  selectedSlug.value = doc.filename
  loadDoc(doc.filename)
  router.push({ path: `/docs/${encodeURIComponent(doc.filename)}`, query: { category: categoryOf(doc.filename) } })
}

const onSelectChange = () => {
  if (selectedSlug.value) {
    loadDoc(selectedSlug.value)
    router.replace({ path: `/docs/${encodeURIComponent(selectedSlug.value)}`, query: { category: categoryOf(selectedSlug.value) } })
  } else {
    content.value = ''
  }
}

const toggleCategory = (dirName: string) => {
  expandedCategories.value[dirName] = !expandedCategories.value[dirName]
}

onMounted(async () => {
  await loadDocs()
  // 直达/刷新:/docs/:slug?category= → 自动定位(含所属分类展开)
  const slug = route.params.slug ? decodeURIComponent(String(route.params.slug)) : ''
  if (slug) {
    selectedSlug.value = slug
    await loadDoc(slug)
  }
})

// 浏览器前进/后退:路由 slug 变化时跟随加载
watch(() => route.params.slug, (v) => {
  const slug = v ? decodeURIComponent(String(v)) : ''
  if (!slug) return
  if (slug !== selectedSlug.value) {
    selectedSlug.value = slug
    loadDoc(slug)
  }
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
