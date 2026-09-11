<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-4xl mx-auto">
      <!-- 页头(无玻璃面板,直接落在背景上;下方内容卡保留玻璃) -->
      <div class="mb-8">
        <SectionHeader compact kicker="ANNOUNCEMENTS" title="公告" subtitle="服务器资讯与版本更新,尽在掌握" />
        <div class="flex gap-2">
          <button class="tab-btn" :class="{ active: tab === 'news' }" @click="tab = 'news'">资讯中心</button>
          <button class="tab-btn" :class="{ active: tab === 'changelog' }" @click="tab = 'changelog'">更新日志</button>
        </div>
      </div>

      <div v-if="loading" class="text-center py-12 text-stone-500">加载中...</div>

      <!-- 资讯中心 -->
      <div v-else-if="tab === 'news'" class="space-y-4">
        <EmptyState v-if="sortedNews.length === 0" icon="chat-bubble" text="暂无资讯" />
        <div v-for="n in sortedNews" :key="n.id" class="card overflow-hidden">
          <button class="w-full text-left p-5" @click="expandedNews = expandedNews === n.id ? null : n.id">
            <div class="flex items-center gap-2 flex-wrap mb-1">
              <span v-if="n.pinned" class="px-2 py-0.5 rounded text-xs bg-orange-500/15 text-orange-400">置顶</span>
              <h2 class="text-lg font-semibold text-white">{{ n.title || '无标题' }}</h2>
              <span class="text-stone-500 text-xs ml-auto">{{ n.date }}</span>
            </div>
            <div v-if="expandedNews === n.id" class="prose prose-invert max-w-none text-sm" v-html="rendered(n.content)"></div>
            <p v-else class="text-stone-400 text-sm line-clamp-2">{{ plainPreview(n.content) }}</p>
          </button>
        </div>
      </div>

      <!-- 更新日志 -->
      <div v-else class="space-y-4">
        <EmptyState v-if="sortedChangelog.length === 0" icon="document-text" text="暂无版本记录" />
        <div v-for="c in sortedChangelog" :key="c.id" class="card p-5">
          <div class="flex items-center gap-3 mb-3">
            <span class="px-2.5 py-1 rounded-lg bg-orange-500/15 border border-orange-500/30 text-orange-400 font-mono text-sm font-bold">
              {{ c.version || 'v?.?.?' }}
            </span>
            <span class="text-stone-500 text-sm">{{ c.date }}</span>
          </div>
          <div class="prose prose-invert max-w-none text-sm" v-html="rendered(c.content)"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/services/api'
import { renderMarkdown } from '@/lib/markdown'
import EmptyState from '@/components/ui/EmptyState.vue'
import SectionHeader from '@/components/SectionHeader.vue'

const tab = ref<'news' | 'changelog'>('news')
const loading = ref(true)
const news = ref<any[]>([])
const changelog = ref<any[]>([])
const expandedNews = ref<string | null>(null)

onMounted(async () => {
  try {
    const r: any = await api.getAnnouncements()
    if (r.success) {
      news.value = r.data.news || []
      changelog.value = r.data.changelog || []
    }
  } catch (e) { console.error(e) }
  loading.value = false
})

const sortedNews = computed(() =>
  [...news.value].sort((a, b) =>
    (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0) || String(b.date || '').localeCompare(String(a.date || ''))))

const sortedChangelog = computed(() =>
  [...changelog.value].sort((a, b) => String(b.date || '').localeCompare(String(a.date || ''))))

const rendered = (content: string) => renderMarkdown(content || '')

const plainPreview = (content: string) =>
  String(content || '').replace(/[#>*`\-]/g, '').trim().slice(0, 80)
</script>
