<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center p-4" style="background: rgba(0,0,0,0.4); backdrop-filter: blur(4px);">
      <div class="card w-full max-w-md p-8 animate-scale-in">
        <div class="text-center mb-6">
          <div class="inline-flex items-center justify-center w-16 h-16 rounded-full mb-4" :class="status === 'completed' ? (passed ? 'bg-emerald-900/30' : 'bg-orange-900/30') : 'bg-orange-900/30'">
            <AppIcon v-if="status === 'scoring'" name="clock" class="w-8 h-8 animate-spin" />
            <AppIcon v-else-if="status === 'completed' && passed" name="check-circle" class="w-10 h-10 text-green-400" />
            <AppIcon v-else-if="status === 'completed' && !passed" name="pencil-square" class="w-10 h-10 text-stone-400" />
            <AppIcon v-else-if="status === 'error'" name="x-circle" class="w-10 h-10 text-rose-400" />
          </div>
          <h2 class="text-xl font-semibold text-white mb-2">{{ title }}</h2>
          <p class="text-sm text-stone-400">{{ subtitle }}</p>
        </div>

        <!-- 评分进度 -->
        <div v-if="status === 'scoring'" class="space-y-4 mb-6">
          <div class="w-full h-2 bg-stone-200 dark:bg-stone-700 rounded-full overflow-hidden">
            <div class="h-full bg-gradient-to-r from-orange-400 to-amber-400 rounded-full transition-all duration-500" :style="{ width: progress + '%' }"></div>
          </div>
          <div class="text-center text-sm text-stone-400">
            AI 正在评分中... {{ currentQuestion }}/{{ totalQuestions }}
          </div>
        </div>

        <!-- 实时评语 -->
        <div v-if="comments.length > 0" class="space-y-3 mb-6 max-h-[300px] overflow-y-auto">
          <div v-for="(comment, index) in comments" :key="index"
            class="p-3 rounded-xl text-sm border"
            :class="comment.score >= comment.maxScore * 0.6
              ? 'bg-emerald-50 dark:bg-emerald-900/20 border-emerald-200 dark:border-emerald-800'
              : comment.score > 0
                ? 'bg-orange-900/20 border-orange-200 dark:border-orange-800'
                : 'bg-stone-50 bg-stone-800 border-stone-700'">
            <div class="flex items-center justify-between mb-1">
              <span class="font-medium text-stone-700 dark:text-stone-200">第 {{ index + 1 }} 题</span>
              <span class="font-semibold" :class="comment.score >= comment.maxScore * 0.6 ? 'text-emerald-600 text-emerald-400' : comment.score > 0 ? 'text-orange-600 text-orange-400' : 'text-stone-400'">
                {{ comment.score }}/{{ comment.maxScore }}
              </span>
            </div>
            <div class="text-stone-300">{{ comment.reason }}</div>
          </div>
        </div>

        <!-- 最终分数 -->
        <div v-if="status === 'completed'" class="text-center mb-6">
          <div class="text-4xl font-bold mb-2" :class="passed ? 'text-emerald-500' : 'text-orange-500'">
            {{ totalScore }}/{{ maxScore }}
          </div>
          <div class="text-sm" :class="passed ? 'text-emerald-600 text-emerald-400' : 'text-orange-600 text-orange-400'">
            {{ passed ? '恭喜通过！' : '未达到通过标准' }}
          </div>
        </div>

        <!-- 操作按钮 -->
        <div v-if="status === 'completed' || status === 'error'" class="flex gap-3">
          <button v-if="!passed" @click="$emit('retry')" class="btn-primary flex-1">重新答题</button>
          <button @click="$emit('close')" class="btn-secondary flex-1">{{ passed ? '返回登录' : '退出' }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  visible: boolean
  status: 'scoring' | 'completed' | 'error'
  currentQuestion: number
  totalQuestion: number
  comments: Array<{ score: number; maxScore: number; reason: string }>
  totalScore: number
  maxScore: number
  passed: boolean
}>()

defineEmits(['close', 'retry'])

const progress = computed(() => {
  if (props.totalQuestion === 0) return 0
  return Math.round((props.currentQuestion / props.totalQuestion) * 100)
})

const title = computed(() => {
  if (props.status === 'scoring') return 'AI 评分中'
  if (props.status === 'error') return '评分出错'
  return props.passed ? '恭喜通过！' : '未通过审核'
})

const subtitle = computed(() => {
  if (props.status === 'scoring') return '正在分析你的回答...'
  if (props.status === 'error') return '评分服务暂时不可用，请稍后重试'
  return props.passed ? '你的问卷已通过审核' : '请继续努力，你可以重新答题'
})
</script>
