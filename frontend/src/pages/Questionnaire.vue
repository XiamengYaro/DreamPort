<template>
  <div class="min-h-screen p-4 pt-24 pb-8">
    <div class="w-full max-w-2xl mx-auto">
      <div class="text-center mb-8">
        <img :src="logoUrl" :alt="brand.short" class="w-16 h-16 rounded-2xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
        <h1 class="text-3xl font-bold tracking-tight text-white">{{ brand.short }} 入服审核问卷</h1>
        <p class="mt-1 text-stone-400">
          共 {{ questions.length }} 题 · 满分 {{ totalMaxScore }} 分 · 通过分数 {{ passScore }} 分
        </p>
      </div>

      <div v-if="!showScoringModal" class="card p-8
">
        <div v-if="!username" class="text-center py-8">
          <p class="text-stone-400 mb-4">请先登录或注册后再填写问卷</p>
          <router-link to="/login" class="btn-primary">去登录</router-link>
        </div>

        <form v-else @submit.prevent="handleSubmit">
          <!-- 进度条 -->
          <div class="mb-6">
            <div class="flex items-center justify-between text-sm text-stone-400 mb-2">
              <span>已完成 {{ answeredCount }}/{{ questions.length }} 题</span>
              <span>{{ Math.round(answeredCount / questions.length * 100) }}%</span>
            </div>
            <div class="w-full h-2 bg-stone-700 rounded-full overflow-hidden">
              <div class="h-full bg-gradient-to-r from-orange-400 to-amber-400 rounded-full transition-all duration-300"
                :style="{ width: (answeredCount / questions.length * 100) + '%' }"></div>
            </div>
          </div>

          <div v-for="(question, index) in questions" :key="question.id" class="mb-6">
            <label class="block text-sm font-medium mb-1 text-stone-200">
              {{ index + 1 }}. {{ question.text }}
              <span v-if="question.required" class="text-rose-500">*</span>
            </label>
            <div class="text-xs mb-3 text-stone-500">{{ question.maxScore }}分</div>

            <div v-if="question.type === 'single_choice'" class="space-y-2">
              <label v-for="option in question.options" :key="option.text"
                class="flex items-center gap-3 p-3 rounded-xl cursor-pointer transition-all duration-200"
                :class="answers[question.id] === option.text ? 'bg-orange-900/20 border border-orange-700' : 'bg-stone-800/50 border border-stone-700 hover:bg-stone-800'">
                <input type="radio" :name="'q_' + question.id" :value="option.text" v-model="answers[question.id]" class="w-4 h-4 text-orange-500" />
                <span class="text-sm text-stone-200">{{ option.text }}</span>
              </label>
            </div>

            <div v-else-if="question.type === 'multiple_choice'" class="space-y-2">
              <label v-for="option in question.options" :key="option.text"
                class="flex items-center gap-3 p-3 rounded-xl cursor-pointer transition-all duration-200"
                :class="isMultiSelected(question.id, option.text) ? 'bg-orange-900/20 border border-orange-700' : 'bg-stone-800/50 border border-stone-700 hover:bg-stone-800'">
                <input type="checkbox" :value="option.text" @change="toggleMultiOption(question.id, option.text)" class="w-4 h-4 text-orange-500" />
                <span class="text-sm text-stone-200">{{ option.text }}</span>
              </label>
            </div>

            <div v-else-if="question.type === 'fill_blank'">
              <input v-model="answers[question.id]" type="text" class="input"
                :placeholder="question.placeholder || '请填写...'" />
              <div v-if="question.scoringRule" class="text-xs mt-1 text-stone-500">自动识别：{{ validationLabel(question.scoringRule) }}</div>
            </div>

            <div v-else-if="question.type === 'essay'">
              <textarea v-model="answers[question.id]" class="input min-h-[130px] resize-y"
                :placeholder="question.placeholder || '请详细作答...'"></textarea>
            </div>

            <div v-else-if="question.type === 'text'">
              <textarea v-if="question.multiline" v-model="answers[question.id]" class="input min-h-[100px] resize-y"
                :placeholder="question.placeholder || '请输入你的回答...'" :minlength="question.minLength || 0" :maxlength="question.maxLength || 500"></textarea>
              <input v-else v-model="answers[question.id]" type="text" class="input"
                :placeholder="question.placeholder || '请输入你的回答...'" :minlength="question.minLength || 0" :maxlength="question.maxLength || 200" />
              <div class="text-xs mt-1 text-stone-500">{{ (answers[question.id] || '').length }} / {{ question.maxLength || 500 }}</div>
            </div>
          </div>

          <div class="flex gap-4 mt-8">
            <button type="button" @click="goBack" class="btn-secondary flex-1">返回</button>
            <button type="submit" class="btn-primary flex-1" :disabled="loading">{{ loading ? '提交中...' : '提交问卷' }}</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 实时评分弹窗 -->
    <Teleport to="body">
      <div v-if="showScoringModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
        <div class="card p-8
 max-w-lg w-full max-h-[90vh] overflow-y-auto">
          <div class="text-center mb-6">
            <div class="inline-flex items-center justify-center w-16 h-16 rounded-full mb-4"
              :class="scoringComplete ? (scoringPassed ? 'bg-emerald-900/30' : 'bg-orange-900/30') : 'bg-orange-900/30'">
              <AppIcon v-if="!scoringComplete" name="cpu" class="w-7 h-7 animate-pulse" />
              <svg v-else-if="scoringPassed" class="w-8 h-8 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
              </svg>
              <svg v-else class="w-8 h-8 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
            </div>
            <h2 class="text-xl font-semibold mb-2 text-white">
              {{ scoringComplete ? (scoringPassed ? '恭喜通过问卷审核！' : '未通过审核') : 'AI 正在分析你的回答...' }}
            </h2>
            <p class="text-sm text-stone-400">
              <template v-if="scoringComplete && scoringPassed">
                您的问卷已通过审核，请等待管理员确认<br>
                <span class="text-orange-500">（若需加急请@管理员并截图发至群内）</span>
              </template>
              <template v-else-if="scoringComplete && !scoringPassed">
                请继续努力，你可以重新答题
              </template>
              <template v-else>
                已评分 {{ scoringResults.length }}/{{ questions.length }} 题
              </template>
            </p>
          </div>

          <div v-if="!scoringComplete" class="mb-3">
            <div class="w-full h-2 bg-stone-700 rounded-full overflow-hidden">
              <div class="h-full bg-gradient-to-r from-orange-400 to-amber-400 rounded-full transition-all duration-300"
                :style="{ width: scoringProgress + '%' }"></div>
            </div>
            <div v-if="currentQuestionText" class="text-xs text-stone-500 mt-2 truncate">正在评分：{{ currentQuestionText }}</div>
          </div>

          <div class="space-y-3 mb-6 max-h-[400px] overflow-y-auto">
            <div v-for="(item, index) in scoringResults" :key="index"
              class="p-4 rounded-xl border border-stone-700 transition-all duration-300"
              :class="item.isNew ? 'ring-2 ring-orange-400' : ''">
              <div class="flex items-center justify-between mb-2">
                <span class="font-medium text-stone-200">
                  第 {{ index + 1 }} 题
                  <span v-if="item.isNew" class="text-xs text-orange-500 ml-2">新</span>
                </span>
                <span class="font-semibold" :class="item.score >= item.maxScore * 0.6 ? 'text-emerald-400' : item.score > 0 ? 'text-orange-400' : 'text-stone-400'">
                  {{ item.score }}/{{ item.maxScore }}
                </span>
              </div>
              <div class="text-sm text-stone-300">{{ item.reason }}</div>
            </div>
          </div>

          <div v-if="scoringComplete" class="text-center mb-6 p-6 rounded-2xl"
            :class="scoringPassed ? 'bg-emerald-900/20' : 'bg-orange-900/20'">
            <div class="text-5xl font-bold mb-2" :class="scoringPassed ? 'text-emerald-500' : 'text-orange-500'">
              {{ scoringTotalScore }}/{{ scoringMaxScore }}
            </div>
            <div class="text-sm" :class="scoringPassed ? 'text-emerald-400' : 'text-orange-400'">
              通过分数：{{ passScore }}分
            </div>
          </div>

          <div v-if="scoringComplete && scoringSummary" class="mb-6 p-4 rounded-xl bg-stone-800/60">
            <div class="text-xs text-stone-500 mb-1.5">总评</div>
            <div class="text-sm text-stone-300 leading-relaxed">{{ scoringSummary }}</div>
          </div>

          <div v-if="scoringComplete" class="flex gap-3">
            <button v-if="!scoringPassed" @click="retryQuestionnaire" class="btn-primary flex-1">重新答题</button>
            <button @click="goToLogin" class="btn-secondary flex-1">{{ scoringPassed ? '返回登录' : '退出' }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { useBrand } from '@/lib/brand'
const brand = useBrand()
import { ref, onMounted, inject, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const notify = inject('notify') as any

const loading = ref(false)
const qnDisabled = ref(false)
const questions = ref<any[]>([])
const answers = ref<Record<string, any>>({})
const passScore = ref(60)

const showScoringModal = ref(false)
const scoringComplete = ref(false)
const scoringPassed = ref(false)
const currentScoringIndex = ref(0)
const currentQuestionText = ref('')
const scoringSummary = ref('')
const scoringResults = ref<Array<{ score: number; maxScore: number; reason: string; isNew: boolean }>>([])
const scoringTotalScore = ref(0)
const scoringMaxScore = ref(0)

const logoUrl = ref('/Logo111.png')
const username = computed(() => localStorage.getItem('pendingUsername') || localStorage.getItem('username'))
const totalMaxScore = computed(() => questions.value.reduce((sum, q) => sum + (q.maxScore || 10), 0))
const answeredCount = computed(() => {
  return questions.value.filter(q => {
    const val = answers.value[q.id]
    return val !== undefined && val !== null && val !== '' && !(Array.isArray(val) && val.length === 0)
  }).length
})
const scoringProgress = computed(() => {
  if (questions.value.length === 0) return 0
  return Math.round((scoringResults.value.length / questions.value.length) * 100)
})

onMounted(async () => {
  if (!username.value) { notify?.info('请先登录或注册'); router.push('/login'); return }
  await loadQuestionnaire()
})

const loadQuestionnaire = async () => {
  try {
    const res = await fetch('/api/config'); const configData = await res.json(); if (configData.success) { passScore.value = configData.data.questionnairePassScore || 60; if (configData.data.portal?.logo) logoUrl.value = configData.data.portal.logo }
    const response: any = await fetch('/api/questionnaire/config'); const data = await response.json()
    if (data.success && data.data.enabled) {
      questions.value = data.data.questions || []
    } else {
      qnDisabled.value = true
      notify?.info('当前未启用入服问卷')
    }
  } catch (error) { notify?.error('加载问卷失败'); router.push('/login') }
}

const validationLabel = (rule: string) => {
  const r = (rule || '').toLowerCase()
  if (r.startsWith('regex:')) return '自定义格式'
  if (r.includes('qq')) return 'QQ 号'
  if (r.includes('邮箱') || r.includes('email')) return '邮箱'
  if (r.includes('手机')) return '手机号'
  if (r.includes('数字') || r.includes('number')) return '数字'
  return '任意内容'
}

const isMultiSelected = (questionId: string, optionText: string) => {
  const selected = answers.value[questionId]
  return Array.isArray(selected) && selected.includes(optionText)
}

const toggleMultiOption = (questionId: string, optionText: string) => {
  if (!answers.value[questionId]) answers.value[questionId] = []
  const arr = answers.value[questionId]
  const idx = arr.indexOf(optionText)
  if (idx >= 0) arr.splice(idx, 1); else arr.push(optionText)
}

const goBack = () => {
  if (localStorage.getItem('token')) router.push('/dashboard'); else router.push('/login')
}

const handleSubmit = async () => {
  if (!username.value) { notify?.error('用户信息丢失'); router.push('/login'); return }

  for (const q of questions.value) {
    if (q.required) {
      const val = answers.value[q.id]
      if (val === undefined || val === null || val === '' || (Array.isArray(val) && val.length === 0)) {
        notify?.error(`请填写第 ${questions.value.indexOf(q) + 1} 题`); return
      }
    }
  }

  showScoringModal.value = true
  scoringComplete.value = false
  scoringResults.value = []
  currentScoringIndex.value = 0
  scoringTotalScore.value = 0
  scoringMaxScore.value = 0
  loading.value = true

  try {
    const response = await fetch('/api/questionnaire/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: JSON.stringify({ username: username.value, answers: answers.value })
    })

    if (!response.ok || !response.body) {
      const errText = await response.json().catch(() => null)
      throw new Error(errText?.message || `提交失败（${response.status}）`)
    }
    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        if (!scoringComplete.value) {
          notify?.error('评分流意外中断，请稍后重试或联系管理员')
          loading.value = false
        }
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          try {
            const event = JSON.parse(line.substring(5).trim())

            if (event.type === 'error') {
              notify?.error(event.message || '评分失败，请重新提交')
              loading.value = false
              showScoringModal.value = false
              return
            }

            if (event.type === 'question_scored') {
              scoringResults.value.push({
                score: event.score,
                maxScore: event.maxScore,
                reason: event.reason,
                questionText: event.questionText || '',
                isNew: true
              })
              currentScoringIndex.value = event.index
              currentQuestionText.value = event.questionText || ''
              scoringTotalScore.value = event.totalScore
              scoringMaxScore.value = event.maxScoreTotal

              setTimeout(() => {
                const idx = scoringResults.value.length - 1
                if (scoringResults.value[idx]) {
                  scoringResults.value[idx].isNew = false
                }
              }, 1000)

            } else if (event.type === 'complete') {
              scoringComplete.value = true
              scoringPassed.value = event.passed
              scoringTotalScore.value = event.totalScore
              scoringMaxScore.value = event.maxScore
              scoringSummary.value = event.summary || ''
            }
          } catch (e) {
            console.error('Parse event error:', e)
          }
        }
      }
    }
  } catch (error: any) {
    notify?.error(error.message || '提交失败')
    showScoringModal.value = false
  } finally {
    loading.value = false
  }
}

const retryQuestionnaire = () => {
  showScoringModal.value = false
  scoringResults.value = []
  currentScoringIndex.value = 0
}

const goToLogin = () => {
  showScoringModal.value = false
  router.push('/login')
}
</script>

<style scoped>
@keyframes fadeInScale {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
