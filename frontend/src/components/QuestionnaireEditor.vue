<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/services/api'
import AppIcon from '@/components/AppIcon.vue'

const emit = defineEmits<{ (e: 'changed'): void }>()

interface EdOption { text: string; score: number }
interface EdQuestion {
  uid: number
  id: number | null
  type: 'single_choice' | 'multiple_choice' | 'fill_blank' | 'essay'
  text: string
  textEn: string
  required: boolean
  maxScore: number
  scoringRule: string
  placeholder: string
  options: EdOption[]
}

const loading = ref(true)
const saving = ref(false)
const selected = ref(0)
const dirty = ref(false)
const qName = ref('默认问卷')
const passScore = ref(60)
const items = ref<EdQuestion[]>([])
const message = ref('')
const messageType = ref<'ok' | 'err'>('ok')

let uidSeed = 1
const newUid = () => uidSeed++

const TYPE_META: Record<string, { label: string; icon: string }> = {
  single_choice: { label: '单选题', icon: 'dot-circle' },
  multiple_choice: { label: '多选题', icon: 'check-square' },
  fill_blank: { label: '填空题', icon: 'minus' },
  essay: { label: '问答题', icon: 'pencil' }
}
const typeLabel = (t: string) => TYPE_META[t]?.label ?? t
const typeIcon = (t: string) => TYPE_META[t]?.icon ?? 'document-text'

const blank = (type: EdQuestion['type']): EdQuestion => ({
  uid: newUid(), id: null, type,
  text: '', textEn: '', required: true, maxScore: 3,
  scoringRule: type === 'fill_blank' ? 'qq' : '',
  placeholder: type === 'fill_blank' ? '请填写…' : '',
  options: (type === 'single_choice' || type === 'multiple_choice')
    ? [{ text: '', score: 3 }, { text: '', score: 0 }] : []
})

const totalScore = computed(() => items.value.reduce((s, q) => s + (Number(q.maxScore) || 0), 0))
const cur = computed(() => items.value[selected.value] ?? null)

const flash = (type: 'ok' | 'err', text: string) => {
  message.value = text
  messageType.value = type
  setTimeout(() => { message.value = '' }, 3500)
}

const load = async () => {
  loading.value = true
  try {
    const r: any = await api.getQuestionnaireList()
    if (r.success) {
      qName.value = r.data.questionnaire?.name || '默认问卷'
      passScore.value = r.data.questionnaire?.passScore ?? 60
      items.value = (r.data.questions || []).map((q: any, i: number) => ({
        uid: newUid(), id: Number(q.id), type: q.type || 'text',
        text: q.question_zh || q.questionZh || '',
        textEn: q.question_en || q.questionEn || '',
        required: !!q.required, maxScore: Number(q.max_score ?? q.maxScore ?? 0),
        scoringRule: q.scoring_rule || q.scoringRule || '',
        placeholder: q.placeholder_zh || q.placeholderZh || '',
        options: (q.options || []).map((o: any) => ({
          text: o.text_zh || o.textZh || o.text || '', score: Number(o.score ?? 0)
        }))
      }))
      selected.value = items.value.length ? 0 : -1
      dirty.value = false
    }
  } finally {
    loading.value = false
  }
}
onMounted(load)

const select = (i: number) => { selected.value = i }
const addQuestion = (type: EdQuestion['type']) => {
  items.value.push(blank(type))
  selected.value = items.value.length - 1
  dirty.value = true
}
const removeQuestion = (i: number) => {
  items.value.splice(i, 1)
  if (selected.value >= items.value.length) selected.value = items.value.length - 1
  dirty.value = true
}
const moveQuestion = (i: number, dir: -1 | 1) => {
  const j = i + dir
  if (j < 0 || j >= items.value.length) return
  const [q] = items.value.splice(i, 1)
  items.value.splice(j, 0, q)
  selected.value = j
  dirty.value = true
}
const addOption = (q: EdQuestion) => q.options.push({ text: '', score: 0 })
const removeOption = (q: EdQuestion, i: number) => {
  if (q.options.length > 2) q.options.splice(i, 1)
  dirty.value = true
}

const validate = (): string => {
  for (let i = 0; i < items.value.length; i++) {
    const q = items.value[i]
    if (!q.text.trim()) return `第 ${i + 1} 题未填写题干`
    if (q.type === 'single_choice' || q.type === 'multiple_choice') {
      const opts = q.options.filter(o => o.text.trim())
      if (opts.length < 2) return `第 ${i + 1} 题至少需要 2 个选项`
    }
  }
  return ''
}

const saveBulk = async () => {
  const err = validate()
  if (err) { flash('err', err); return }
  saving.value = true
  try {
    const payload = {
      name: qName.value,
      passScore: Number(passScore.value) || 60,
      questions: items.value.map(q => ({
        type: q.type,
        question_zh: q.text,
        question_en: q.textEn || null,
        required: q.required,
        max_score: Number(q.maxScore) || 0,
        scoring_rule: (q.type === 'fill_blank' || q.type === 'essay') ? (q.scoringRule || '') : null,
        placeholder: q.type === 'fill_blank' ? (q.placeholder || null) : null,
        options: (q.type === 'single_choice' || q.type === 'multiple_choice')
          ? q.options.filter(o => o.text.trim()).map(o => ({ text_zh: o.text, score: Number(o.score) || 0 }))
          : []
      }))
    }
    const r: any = await api.saveBulkQuestionnaire(payload)
    if (r.success) {
      flash('ok', r.message || '已保存')
      await load()
      emit('changed')
    } else {
      flash('err', r.message || '保存失败')
    }
  } catch (e: any) {
    flash('err', e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const resetDefault = async () => {
  if (!confirm('确认恢复默认题库？当前题目将被覆盖。')) return
  await api.resetQuestionnaire()
  await load()
  emit('changed')
  flash('ok', '已恢复默认题库')
}
</script>

<template>
  <div class="space-y-4">
    <!-- 工具栏 -->
    <div class="card p-4 flex items-center gap-3 flex-wrap">
      <input v-model="qName" class="input w-56" placeholder="问卷名称" />
      <div class="flex items-center gap-1.5 text-sm text-stone-400">
        及格分 <input v-model.number="passScore" type="number" min="0" max="100" class="input w-20 text-center" />
      </div>
      <span class="text-xs text-stone-500">{{ items.length }} 题 · 满分 {{ totalScore }} 分</span>
      <div class="ml-auto flex items-center gap-2">
        <button @click="resetDefault"
          class="px-3 py-2 rounded-xl text-sm text-stone-400 hover:text-white hover:bg-white/5 transition-colors">
          恢复默认题库
        </button>
        <button @click="saveBulk" :disabled="saving"
          class="px-5 py-2 rounded-xl text-sm font-medium transition-colors flex items-center gap-1.5"
          :class="dirty ? 'bg-orange-500 text-white hover:bg-orange-600' : 'bg-stone-700 text-stone-300 hover:bg-stone-600'">
          <AppIcon name="check-circle" class="w-4 h-4" /> {{ saving ? '保存中…' : '保存整卷' }}
        </button>
      </div>
    </div>

    <div v-if="message" class="px-4 py-2.5 rounded-xl text-sm"
      :class="messageType === 'ok' ? 'bg-green-500/10 text-green-400' : 'bg-rose-500/10 text-rose-400'">
      {{ message }}
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-[260px_1fr] gap-4 items-start">
      <!-- 左：题目大纲 -->
      <div class="card p-3 lg:sticky lg:top-24">
        <div class="text-xs text-stone-500 px-2 pb-2 uppercase tracking-wide">题目大纲</div>
        <div class="space-y-1 max-h-[420px] overflow-y-auto">
          <button v-for="(q, i) in items" :key="q.uid" @click="select(i)"
            class="w-full text-left px-2.5 py-2 rounded-lg text-sm transition-colors flex items-start gap-2"
            :class="i === selected ? 'bg-orange-500/15 text-orange-400' : 'text-stone-400 hover:text-white hover:bg-white/5'">
            <span class="font-mono text-xs mt-0.5">{{ i + 1 }}.</span>
            <span class="flex-1 truncate">{{ q.text || '（未命名）' }}</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-stone-700/60 shrink-0">{{ typeLabel(q.type) }}</span>
          </button>
          <div v-if="items.length === 0" class="text-center text-stone-500 text-sm py-4">暂无题目</div>
        </div>
        <div class="pt-2 mt-2 border-t border-stone-700/60 grid grid-cols-2 gap-1.5">
          <button v-for="(m, t) in TYPE_META" :key="t" @click="addQuestion(t as EdQuestion['type'])"
            class="flex items-center gap-1.5 px-2 py-2 rounded-lg text-xs text-stone-400 hover:text-orange-400 hover:bg-white/5 transition-colors">
            <AppIcon :name="m.icon" class="w-3.5 h-3.5" /> {{ m.label }}
          </button>
        </div>
      </div>

      <!-- 右：编辑画布 -->
      <div>
        <div v-if="cur" class="card p-6 space-y-5">
          <!-- 题目工具行 -->
          <div class="flex items-center gap-3 flex-wrap">
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-orange-500/10 text-orange-400 text-sm font-medium">
              <AppIcon :name="typeIcon(cur.type)" class="w-4 h-4" /> 第 {{ selected + 1 }} 题
            </span>
            <select v-model="cur.type" class="input w-36 text-sm">
              <option value="single_choice">单选题</option>
              <option value="multiple_choice">多选题</option>
              <option value="fill_blank">填空题</option>
              <option value="essay">问答题</option>
            </select>
            <label class="flex items-center gap-1.5 text-sm text-stone-400">
              <input type="checkbox" v-model="cur.required" class="accent-orange-500" /> 必答
            </label>
            <div class="flex items-center gap-1.5 text-sm text-stone-400">
              分值 <input v-model.number="cur.maxScore" type="number" min="0" class="input w-20 text-center" />
            </div>
            <div class="ml-auto flex items-center gap-1">
              <button @click="moveQuestion(selected, -1)" :disabled="selected === 0"
                class="p-2 rounded-lg text-stone-400 hover:text-white hover:bg-white/5 disabled:opacity-25 transition-colors" title="上移">
                <AppIcon name="chevron-down" class="w-4 h-4 rotate-180" />
              </button>
              <button @click="moveQuestion(selected, 1)" :disabled="selected === items.length - 1"
                class="p-2 rounded-lg text-stone-400 hover:text-white hover:bg-white/5 disabled:opacity-25 transition-colors" title="下移">
                <AppIcon name="chevron-down" class="w-4 h-4" />
              </button>
              <button @click="removeQuestion(selected)"
                class="p-2 rounded-lg text-rose-400 hover:text-rose-300 hover:bg-rose-500/10 transition-colors" title="删除">
                <AppIcon name="x-mark" class="w-4 h-4" />
              </button>
            </div>
          </div>

          <!-- 题干 -->
          <div>
            <div class="text-xs text-stone-500 mb-1.5">题干（中文）</div>
            <textarea v-model="cur.text" rows="2"
              class="w-full bg-stone-900/40 border border-stone-700 rounded-xl px-3 py-2.5 text-white outline-none focus:border-orange-500/50 transition-colors resize-none"
              placeholder="请输入题目内容"></textarea>
            <input v-model="cur.textEn" class="w-full bg-stone-900/40 border border-stone-700/60 rounded-xl px-3 py-2 text-sm text-stone-300 outline-none focus:border-orange-500/40 mt-2"
              placeholder="题干（英文，可选）" />
          </div>

          <!-- 单选/多选：选项编辑 -->
          <div v-if="cur.type === 'single_choice' || cur.type === 'multiple_choice'" class="space-y-2">
            <div class="text-xs text-stone-500 flex items-center justify-between">
              <span>选项（勾选状态图标仅为示意）</span>
              <span>得分</span>
            </div>
            <div v-for="(opt, oi) in cur.options" :key="oi" class="flex items-center gap-2">
              <span class="text-stone-500 text-xs w-5 text-center shrink-0">{{ cur.type === 'single_choice' ? '◯' : '▣' }}</span>
              <input v-model="opt.text" class="flex-1 bg-stone-900/40 border border-stone-700 rounded-xl px-3 py-2 text-sm text-white outline-none focus:border-orange-500/50 transition-colors" placeholder="选项内容" />
              <input v-model.number="opt.score" type="number" class="w-20 bg-stone-900/40 border border-stone-700 rounded-xl px-2 py-2 text-sm text-white text-center" />
              <button @click="removeOption(cur, oi)" :disabled="cur.options.length <= 2"
                class="p-1.5 text-stone-500 hover:text-rose-400 disabled:opacity-25 transition-colors">
                <AppIcon name="x-mark" class="w-3.5 h-3.5" />
              </button>
            </div>
            <button @click="addOption(cur)"
              class="text-sm text-orange-400 hover:text-orange-300 flex items-center gap-1 pl-6 transition-colors">
              <AppIcon name="plus" class="w-3.5 h-3.5" /> 添加选项
            </button>
          </div>

          <!-- 填空题：识别规则 -->
          <div v-else-if="cur.type === 'fill_blank'" class="space-y-3">
            <div class="bg-stone-900/40 border border-stone-700/50 rounded-xl px-4 py-3 text-stone-500 text-sm">
              {{ cur.placeholder || '请填写你的答案 ＿＿＿＿＿＿' }}
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <div class="text-xs text-stone-500 mb-1.5">自动识别规则（答案命中得满分）</div>
                <select v-model="cur.scoringRule" class="input w-full text-sm">
                  <option value="">任意内容（填写即得分）</option>
                  <option value="qq">QQ 号（5-12 位数字）</option>
                  <option value="email">邮箱</option>
                  <option value="phone">手机号</option>
                  <option value="number">纯数字</option>
                  <option value="regex:">自定义正则</option>
                </select>
              </div>
              <div>
                <div class="text-xs text-stone-500 mb-1.5">占位提示</div>
                <input v-model="cur.placeholder" class="input w-full text-sm" placeholder="请填写你的QQ号…" />
              </div>
            </div>
            <div v-if="cur.scoringRule === 'regex:'">
              <input v-model="cur.scoringRule" class="input w-full text-sm font-mono" placeholder="regex:在此输入正则表达式" />
            </div>
          </div>

          <!-- 问答题：评分规则 -->
          <div v-else-if="cur.type === 'essay'" class="space-y-3">
            <div class="bg-stone-900/40 border border-stone-700/50 rounded-xl px-4 py-3 text-stone-500 text-sm min-h-[70px]">
              开放式作答区域（玩家输入长文本）
            </div>
            <div>
              <div class="text-xs text-stone-500 mb-1.5">评分规则（供 AI 评分参考；未启用 AI 时按回答长度评分）</div>
              <textarea v-model="cur.scoringRule" rows="3"
                class="w-full bg-stone-900/40 border border-stone-700 rounded-xl px-3 py-2.5 text-sm text-white outline-none focus:border-orange-500/50 transition-colors"
                placeholder="例如：能准确理解恶意破坏的形式与危害得满分；与问题无关或敷衍得 0 分"></textarea>
            </div>
          </div>
        </div>

        <div v-else class="card p-12 text-center text-stone-500 text-sm">
          暂无题目，请从左侧「+ 单选题 / 多选题 / 填空题 / 问答题」添加
        </div>
      </div>
    </div>
  </div>
</template>
