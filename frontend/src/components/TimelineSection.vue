<template>
  <section class="py-16 px-4 overflow-hidden">
    <div class="max-w-6xl mx-auto">
      <h2 class="text-3xl font-bold text-white text-center mb-4">历史时刻 · Timeline</h2>
      
      <!-- 分类筛选 -->
      <div class="flex gap-2 justify-center mb-8 flex-wrap">
        <button 
          v-for="type in timelineTypes" 
          :key="type.value"
          @click="currentType = type.value"
          :class="currentType === type.value ? 'bg-orange-500/20 border-orange-500 text-orange-400' : 'border-stone-700 text-stone-400 hover:border-stone-600'"
          class="px-4 py-2 rounded-lg border transition-all text-sm font-medium"
        >
          {{ type.label }}
          <span v-if="getCount(type.value) > 0" class="ml-1 px-1.5 py-0.5 bg-orange-500/10 rounded text-xs">{{ getCount(type.value) }}</span>
        </button>
      </div>
      
      <!-- 垂直时间轴 -->
      <div class="relative">
        <!-- 中心竖线 -->
        <div class="absolute left-1/2 transform -translate-x-1/2 h-full w-0.5 bg-gradient-to-b from-orange-500 via-amber-500 to-transparent"></div>
        
        <!-- 事件列表 -->
        <div class="space-y-12">
          <div v-for="(event, index) in filteredTimeline" :key="index" class="relative">
            <!-- 时间节点 -->
            <div class="absolute left-1/2 transform -translate-x-1/2 z-10 timeline-marker">
              <div class="w-4 h-4 bg-orange-500 rounded-full ring-4 ring-stone-900 relative">
                <div class="absolute -top-1 -left-1 w-6 h-6 bg-orange-500/30 rounded-full animate-ping"></div>
              </div>
            </div>
            
            <!-- 左侧内容 (奇数项) -->
            <div v-if="index % 2 === 0" class="flex items-center gap-8">
              <div class="flex-1 card p-6 card-hover group card-container">
                <div class="flex items-center gap-2 mb-3">
                  <span class="px-2 py-1 rounded text-xs bg-orange-500/15 text-orange-400 inline-flex items-center gap-1"><AppIcon :name="getTypeIcon(event.type)" class="w-3.5 h-3.5" />{{ getTypeTag(event.type) }}</span>
                  <span class="text-stone-500 text-sm">{{ event.date }}</span>
                </div>
                <h3 class="text-xl font-semibold text-white mb-2 group-hover:text-orange-400 transition-colors">{{ event.title }}</h3>
                <p class="text-stone-400 text-sm leading-relaxed mb-4">{{ event.description }}</p>
                <img v-if="event.image" :src="event.image" :alt="event.title" class="w-full max-h-64 object-cover rounded-lg shadow-lg" @error="handleImageError($event)" />
              </div>
            </div>
            
            <!-- 右侧内容 (偶数项) -->
            <div v-else class="flex items-center gap-8 flex-row-reverse">
              <div class="flex-1 card p-6 card-hover group card-container">
                <div class="flex items-center gap-2 mb-3">
                  <span class="px-2 py-1 rounded text-xs bg-orange-500/15 text-orange-400 inline-flex items-center gap-1"><AppIcon :name="getTypeIcon(event.type)" class="w-3.5 h-3.5" />{{ getTypeTag(event.type) }}</span>
                  <span class="text-stone-500 text-sm">{{ event.date }}</span>
                </div>
                <h3 class="text-xl font-semibold text-white mb-2 group-hover:text-orange-400 transition-colors">{{ event.title }}</h3>
                <p class="text-stone-400 text-sm leading-relaxed mb-4">{{ event.description }}</p>
                <img v-if="event.image" :src="event.image" :alt="event.title" class="w-full max-h-64 object-cover rounded-lg shadow-lg" @error="handleImageError($event)" />
              </div>
            </div>
          </div>
        </div>
        
        <!-- 空状态 -->
        <EmptyState v-if="filteredTimeline.length === 0" icon="document-text" text="暂无相关内容" />
      </div>
      
      <!-- 加载更多按钮 -->
      <div v-if="timeline.length > 10 && !showAll" class="text-center mt-8">
        <button @click="showAll = true" class="px-6 py-3 rounded-xl bg-orange-500/15 border border-orange-500/30 text-orange-400 hover:bg-orange-500/25 transition-all">
          加载更多
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import EmptyState from './ui/EmptyState.vue'
import { ref, computed } from 'vue'

const props = defineProps<{
  timeline: Array<{ date: string; title: string; description: string; image?: string; type?: string }>
}>()

const currentType = ref('all')
const showAll = ref(false)

// 时间类型定义
const timelineTypes = [
  { label: '全部', value: 'all' },
  { label: '公告更新', value: 'announcement' },
  { label: '活动赛事', value: 'event' },
  { label: '成就纪念', value: 'milestone' }
]

// 类型标签映射
const typeLabels: Record<string, string> = {
  announcement: 'megaphone',
  event: 'trophy',
  milestone: 'sparkles'
}

// 获取类型标签
const getTypeTag = (type?: string) => {
  if (!type) return '其他'
  const icon = typeIcons[type] || 'document-text'
  return `${label} ${formatType(type)}`
}

// 格式化类型名
const formatType = (type: string) => {
  const map: Record<string, string> = {
    announcement: '公告更新',
    event: '活动赛事',
    milestone: '成就纪念'
  }
  return map[type] || '其他'
}

// 按类型过滤
const filteredTimeline = computed(() => {
  if (currentType.value === 'all') {
    return showAll.value ? props.timeline : props.timeline.slice(0, 10)
  }
  const filtered = props.timeline.filter(item => item.type === currentType.value)
  return showAll.value ? filtered : filtered.slice(0, 10)
})

// 统计每种类型的数量
const getCount = (type: string) => {
  if (type === 'all') return props.timeline.length
  return props.timeline.filter(item => item.type === type).length
}

// 处理图片加载失败
const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
}
</script>

<style scoped>
/* 响应式布局 */
@media (max-width: 768px) {
  .flex-row-reverse {
    flex-direction: column !important;
  }
  
  .timeline-marker {
    display: none !important;
  }
  
  .card-container {
    padding-left: 2rem !important;
  }
  
  .card-container::before {
    content: '';
    position: absolute;
    left: 0.75rem;
    top: 0;
    bottom: 0;
    width: 2px;
    background: linear-gradient(to bottom, #f97316, #f59e0b);
  }
}

/* 淡入动画 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.group {
  animation: fadeInUp 0.6s ease-out forwards;
}
</style>
