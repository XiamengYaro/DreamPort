<template>
  <div class="min-h-screen flex items-center justify-center p-4">
    <div class="w-full max-w-2xl">
      <div class="card p-8
">
        <div class="text-center mb-8">
          <div class="inline-flex items-center justify-center w-20 h-20 rounded-full mb-4" :class="result?.passed ? 'bg-emerald-900/20' : 'bg-orange-900/20'">
            <svg v-if="result?.passed" class="w-10 h-10 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
            </svg>
            <svg v-else class="w-10 h-10 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          </div>
          <h1 class="text-3xl font-bold tracking-tight mb-2 text-white">
            {{ result?.passed ? '恭喜通过问卷审核！' : '未通过审核' }}
          </h1>
          <p class="text-stone-400" v-if="result?.passed">
            您的问卷已通过审核，请等待管理员确认<br>
            <span class="text-orange-500">（若需加急请@管理员并截图发至群内）</span>
          </p>
          <p class="text-stone-400" v-else>
            很遗憾，你的问卷未达到通过标准
          </p>
        </div>

        <div class="rounded-2xl p-6 mb-6" :class="result?.passed ? 'bg-emerald-900/20' : 'bg-orange-900/20'">
          <div class="flex items-center justify-between mb-4">
            <span class="text-stone-300">你的得分</span>
            <span class="text-4xl font-bold" :class="result?.passed ? 'text-emerald-500' : 'text-orange-500'">
              {{ result?.totalScore || 0 }}<span class="text-lg font-normal text-stone-500">/{{ result?.maxScore || 0 }}</span>
            </span>
          </div>
          <div class="w-full h-2 bg-stone-200 dark:bg-stone-700 rounded-full overflow-hidden">
            <div class="h-full rounded-full transition-all duration-1000" :class="result?.passed ? 'bg-emerald-500' : 'bg-orange-500'" :style="{ width: percentage + '%' }"></div>
          </div>
          <div class="flex items-center justify-between mt-3 text-sm text-stone-400">
            <span>通过线：{{ passScore }}分（{{ passScorePercent }}%）</span>
            <span>你的得分率：{{ percentage }}%</span>
          </div>
        </div>

        <div v-if="result?.results" class="mb-6">
          <h3 class="font-medium mb-3 text-stone-200">得分明细</h3>
          <div class="space-y-2">
            <div v-for="(item, index) in result.results" :key="item.questionId"
              class="flex items-center justify-between p-3 rounded-xl bg-stone-800/50 border border-stone-700">
              <span class="text-sm text-stone-300">第 {{ index + 1 }} 题</span>
              <span class="text-sm font-semibold" :class="item.score >= item.maxScore * 0.6 ? 'text-emerald-500' : item.score > 0 ? 'text-orange-500' : 'text-stone-400'">
                {{ item.score }}/{{ item.maxScore }}
              </span>
            </div>
          </div>
        </div>

        <div class="flex gap-4">
          <button v-if="!result?.passed" @click="retryQuestionnaire" class="btn-primary flex-1">重新答题</button>
          <button @click="goBack" class="btn-secondary flex-1">{{ result?.passed ? '返回登录' : '退出' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const result = ref<any>(null)
const passScore = ref(60)

const percentage = computed(() => { if (!result.value) return 0; return Math.round(result.value.totalScore * 100 / result.value.maxScore) })
const passScorePercent = computed(() => passScore.value)

onMounted(async () => {
  const stored = sessionStorage.getItem('questionnaireResult')
  if (stored) { result.value = JSON.parse(stored) } else { router.push('/login'); return }
  try { const res = await fetch('/api/config'); const data = await res.json(); if (data.success && data.data.questionnairePassScore) passScore.value = data.data.questionnairePassScore } catch (e) {}
})

const retryQuestionnaire = () => router.push('/questionnaire')
const goBack = () => { sessionStorage.removeItem('questionnaireResult'); router.push('/dashboard') }
</script>
