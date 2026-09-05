<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/services/api'
import AppIcon from '@/components/AppIcon.vue'

const emit = defineEmits<{ (e: 'changed'): void }>()

interface OptionItem { text_zh: string; text_en?: string | null; score: number }
interface BankQuestion {
  id: number | null
  type: string
  question_zh: string
  question_en?: string | null
  required: boolean
  max_score: number
  scoring_rule?: string | null
  multiline?: boolean
  placeholder?: string | null
  options: OptionItem[]
  isNew?: boolean
}

const items = ref<BankQuestion[]>([])
const loading = ref(false)
const expanded = ref<Set<number>>(new Set())

const TYPE_META: Record<string, { label: string; icon: string }> = {
  single_choice: { label: '单选题', icon: 'dot-circle' },
  multiple_choice: { label: '多选题', icon: 'check-square' },
  fill_blank: { label: '填空题', icon: 'minus' },
  essay: { label: '问答题', icon: 'pencil' }
}
const typeLabel = (t: string) => TYPE_META[t]?.label ?? t
const typeIcon = (t: string) => TYPE_META[t]?.icon ?? '?'

const load = async () => {
  loading.value = true
  try {
    const r: any = await api.getQuestionnaireList()
    if (r.success) {
      items.value = (r.data.questions || []).map((q: any) => ({
        id: q.id,
        type: q.type,
        question_zh: q.question_zh || '',
        question_en: q.question_en || '',
        required: !!q.required,
        max_score: q.max_score || 0,
        scoring_rule: q.scoring_rule || '',
        options: (q.options || []).map((o: any) => ({ text_zh: o.text_zh || o.text || '', score: o.score || 0 }))
      }))
      expanded.value = new Set()
    }
  } finally {
    loading.value = false
  }
}
onMounted(load)

const toggleExpand = (i: number) => {
  if (expanded.value.has(i)) expanded.value.delete(i)
  else expanded.value.add(i)
}

const blank = (type: string): BankQuestion => ({
  id: null, type, question_zh: '', question_en: '', required: true, max_score: 3,
  scoring_rule: type === 'fill_blank' ? 'qq' : '', multiline: type === 'essay',
  options: (type === 'single_choice' || type === 'multiple_choice')
    ? [{ text_zh: '', score: 3 }, { text_zh: '', score: 0 }] : [],
  isNew: true
})
const onTypeChange = (q: BankQuestion) => {
  if ((q.type === 'single_choice' || q.type === 'multiple_choice') && q.options.length === 0) {
    q.options = [{ text_zh: '', score: 3 }, { text_zh: '', score: 0 }]
  }
}

const addQuestion = (type: string) => {
  items.value.push(blank(type))
  expanded.value.add(items.value.length - 1)
}
const addOption = (q: BankQuestion) => q.options.push({ text_zh: '', score: 0 })
const removeOption = (q: BankQuestion, i: number) => {
  if (q.options.length > 2) q.options.splice(i, 1)
}

const validate = (q: BankQuestion): string => {
  if (!q.question_zh.trim()) return '请填写题目内容'
  if (q.type === 'single_choice' || q.type === 'multiple_choice') {
    const opts = q.options.filter(o => o.text_zh.trim())
    if (opts.length < 2) return '选择题至少需要 2 个选项'
  }
  return ''
}

const toPayload = (q: BankQuestion) => ({
  question: {
    id: q.id, question_zh: q.question_zh, question_en: q.question_en || null,
    type: q.type, required: q.required, max_score: Number(q.max_score) || 0,
    scoring_rule: q.type === 'fill_blank' || q.type === 'essay' ? (q.scoring_rule || '') : null,
    multiline: q.type === 'essay', placeholder: q.type === 'fill_blank' ? (q.placeholder || null) : null
  },
  options: (q.type === 'single_choice' || q.type === 'multiple_choice'
    ? q.options.filter(o => o.text_zh.trim()) : []).map(o => ({ text_zh: o.text_zh, score: Number(o.score) || 0 }))
})

const saveCard = async (q: BankQuestion) => {
  const err = validate(q)
  if (err) return alert(err)
  const payload = toPayload(q)
  if (q.isNew) {
    const r: any = await api.addQuestion(payload)
    if (r.success) { await load(); emit('changed') }
  } else {
    const r: any = await api.updateQuestion(payload)
    if (r.success) { await load(); emit('changed') }
  }
}

const removeCard = async (q: BankQuestion, i: number) => {
  if (!confirm('确认删除该题目？')) return
  if (q.isNew) { items.value.splice(i, 1); return }
  await api.deleteQuestion(q.id as number)
  await load()
  emit('changed')
}

const move = async (q: BankQuestion, direction: 'up' | 'down') => {
  if (q.isNew || q.id === null) return
  await api.moveQuestion({ id: q.id, direction })
  await load()
}

const savingId = ref<number | 'new' | null>(null)
const saveWithState = async (q: BankQuestion, i: number) => {
  savingId.value = q.id ?? 'new'
  try { await saveCard(q) } finally { savingId.value = null }
}
</script>

<template>
  <div class="space-y-4" v-if="!loading">
    <!-- 问卷卡片列表（Google Forms 风格：始终可编辑） -->
    <div v-for="(q, i) in items" :key="q.id ?? 'new' + i"
      class="rounded-2xl border-2 bg-stone-800/40 transition-all duration-200"
      :class="expanded.has(i) ? 'border-orange-500/40' : 'border-stone-700/60 hover:border-stone-600'">
      <!-- 卡片头：题型 + 操作 -->
      <div class="flex items-center gap-2 px-4 pt-3 pb-2 border-b border-stone-700/40" :class="expanded.has(i) ? '' : 'pb-3'">
        <AppIcon :name="typeIcon(q.type)" class="w-5 h-5 text-orange-400" :title="typeLabel(q.type)" />
        <select v-model="q.type" @change="onTypeChange(q)"
          class="text-xs bg-stone-900/60 border border-stone-700 rounded-lg px-2 py-1 text-stone-300">
          <option value="single_choice">单选题</option>
          <option value="multiple_choice">多选题</option>
          <option value="fill_blank">填空题</option>
          <option value="essay">问答题</option>
        </select>
        <span class="text-xs text-stone-500">{{ q.required ? '必答' : '选答' }}</span>
        <div class="ml-auto flex items-center gap-1">
          <button @click="move(q, 'up')" :disabled="i === 0" title="上移"
            class="p-1.5 text-stone-400 hover:text-white disabled:opacity-25 transition-all">↑</button>
          <button @click="move(q, 'down')" :disabled="i === items.length - 1" title="下移"
            class="p-1.5 text-stone-400 hover:text-white disabled:opacity-25 transition-all">↓</button>
          <button @click="toggleExpand(i)" :title="expanded.has(i) ? '收起' : '展开'"
            class="p-1.5 text-stone-400 hover:text-white transition-all">{{ expanded.has(i) ? '▴' : '▾' }}</button>
          <button @click="removeCard(q, i)" title="删除"
            class="p-1.5 text-rose-400 hover:text-rose-300 transition-all"><AppIcon name="x-mark" class="w-4 h-4" /></button>
        </div>
      </div>

      <!-- 卡片体 -->
      <div class="px-4 py-3 space-y-3">
        <!-- 收起态：显示题干 -->
        <div v-if="!expanded.has(i)" class="text-sm text-white truncate">{{ q.question_zh || '(未命名题目)' }}</div>

        <template v-else>
          <input v-model="q.question_zh" class="w-full bg-transparent border-b-2 border-stone-700 focus:border-orange-500/60 text-white text-base pb-1 outline-none transition-all"
            placeholder="题目内容" />

          <!-- 单选/多选选项 -->
          <div v-if="q.type === 'single_choice' || q.type === 'multiple_choice'" class="space-y-2 pl-1">
            <div v-for="(opt, oi) in q.options" :key="oi" class="flex items-center gap-2">
              <span class="text-stone-500 text-xs w-4">{{ q.type === 'single_choice' ? '○' : '☑' }}</span>
              <input v-model="opt.text_zh" class="flex-1 bg-transparent border-b border-stone-700 focus:border-orange-500/60 text-sm text-white pb-0.5 outline-none transition-all" placeholder="选项内容" />
              <div class="flex items-center gap-1">
                <input v-model.number="opt.score" type="number" class="w-16 bg-stone-900/60 border border-stone-700 rounded-lg px-2 py-1 text-xs text-white text-center" title="该选项分值" />
                <span class="text-xs text-stone-500">分</span>
              </div>
              <button @click="removeOption(q, oi)" class="text-stone-500 hover:text-rose-400 text-sm px-1"><AppIcon name="x-mark" class="w-3.5 h-3.5" /></button>
            </div>
            <button @click="addOption(q)" class="text-sm text-orange-400 hover:text-orange-300 flex items-center gap-1 pl-1">＋ 添加选项</button>
          </div>

          <!-- 填空题：识别规则 -->
          <div v-else-if="q.type === 'fill_blank'" class="space-y-2 pl-1">
            <div class="bg-stone-900/40 border border-stone-700/50 rounded-lg px-3 py-2 text-stone-500 text-sm">请填写你的答案 ____________</div>
            <div class="flex items-center gap-2 text-xs text-stone-400">
              <span>自动识别：</span>
              <select v-model="q.scoring_rule" class="bg-stone-900/60 border border-stone-700 rounded-lg px-2 py-1 text-xs text-white">
                <option value="">任意内容</option>
                <option value="qq">QQ 号（5-12 位数字）</option>
                <option value="email">邮箱</option>
                <option value="phone">手机号</option>
                <option value="number">纯数字</option>
                <option value="regex:">自定义正则</option>
              </select>
              <input v-if="q.scoring_rule && q.scoring_rule.startsWith('regex:')" v-model="q.scoring_rule"
                class="flex-1 bg-stone-900/60 border border-stone-700 rounded-lg px-2 py-1 text-xs text-white" placeholder="regex:你的正则" />
            </div>
            <div class="text-xs text-stone-500">答案命中规则 → 得满分（{{ q.max_score }} 分），否则 0 分</div>
          </div>

          <!-- 问答题：评分规则 -->
          <div v-else-if="q.type === 'essay'" class="space-y-2 pl-1">
            <div class="bg-stone-900/40 border border-stone-700/50 rounded-lg px-3 py-2 text-stone-500 text-sm min-h-[60px]">开放式作答区域…</div>
            <textarea v-model="q.scoring_rule" rows="2"
              class="w-full bg-stone-900/40 border border-stone-700/50 rounded-lg px-3 py-2 text-sm text-white outline-none focus:border-orange-500/40"
              placeholder="评分规则（供 AI 评分参考，如：能准确理解恶意破坏的形式与危害得满分；与问题无关得 0 分）"></textarea>
            <div class="text-xs text-stone-500">启用 AI 评分（config.yml wl.llm）时按规则打分；未启用按回答长度评分</div>
          </div>
        </template>
      </div>

      <!-- 卡片底：分值/必答/保存 -->
      <div v-if="expanded.has(i)" class="flex items-center gap-3 px-4 py-3 border-t border-stone-700/40">
        <label class="flex items-center gap-2 text-xs text-stone-400">
          <input type="checkbox" v-model="q.required" class="accent-orange-500" /> 必答
        </label>
        <div class="flex items-center gap-1 text-xs text-stone-400">
          分值 <input v-model.number="q.max_score" type="number" min="0" class="w-16 bg-stone-900/60 border border-stone-700 rounded-lg px-2 py-1 text-white text-center" />
        </div>
        <button @click="saveWithState(q, i)" :disabled="savingId !== null"
          class="ml-auto px-4 py-1.5 rounded-xl bg-orange-500 text-white text-xs font-medium hover:bg-orange-600 disabled:opacity-40 transition-all">
          {{ savingId === (q.id ?? 'new') ? '保存中…' : (q.isNew ? '创建题目' : '保存修改') }}
        </button>
      </div>
    </div>

    <!-- 添加题目按钮组 -->
    <div class="flex justify-center gap-2 py-2">
      <button v-for="t in ['single_choice', 'multiple_choice', 'fill_blank', 'essay']" :key="t"
        @click="addQuestion(t)" :title="typeLabel(t)"
        class="w-11 h-11 rounded-full bg-stone-800 border-2 border-dashed border-stone-600 text-stone-400 hover:text-orange-400 hover:border-orange-500/50 transition-all flex items-center justify-center">
        <AppIcon :name="typeIcon(t)" class="w-5 h-5" />
      </button>
    </div>
  </div>
</template>
