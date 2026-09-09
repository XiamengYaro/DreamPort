<template>
  <div class="space-y-6">
    <!-- 新建投票 -->
    <div class="card p-6 space-y-3">
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold text-white">投票管理</h3>
        <button class="btn-secondary text-sm" @click="createOpen = !createOpen">+ 新建投票</button>
      </div>
      <div v-if="createOpen" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 space-y-3">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div><label class="block text-xs text-stone-500 mb-1">标题</label><input v-model="form.title" class="input text-sm" /></div>
          <div><label class="block text-xs text-stone-500 mb-1">说明</label><input v-model="form.description" class="input text-sm" /></div>
          <label class="flex items-center gap-2 text-sm text-stone-300 self-end">
            <input type="checkbox" v-model="form.multiple" class="accent-orange-500" /> 允许多选
          </label>
          <div><label class="block text-xs text-stone-500 mb-1">结果可见性</label>
            <select v-model="form.resultVisibility" class="input text-sm">
              <option value="open">实时公开(默认)</option>
              <option value="ended">结束后/投票后公开</option>
            </select></div>
        </div>
        <div><label class="block text-xs text-stone-500 mb-1">选项(每行一个,至少 2 个)</label>
          <textarea v-model="form.optionsText" rows="4" class="input text-sm"></textarea></div>
        <button class="btn-primary text-sm" @click="create">创建(草稿)</button>
      </div>
      <p class="text-xs text-stone-500">开启后会给全站玩家发铃铛通知;改动选项会清空已有选票。</p>
    </div>

    <!-- 投票列表 -->
    <div v-for="p in polls" :key="p.id" class="card p-6 space-y-3">
      <div class="flex flex-wrap items-center gap-2">
        <span class="font-medium text-white">{{ p.title }}</span>
        <span class="text-[10px] px-1.5 py-0.5 rounded" :class="pollStatusClass(p.status)">{{ pollStatusText(p.status) }}</span>
        <span class="text-[10px] px-1.5 py-0.5 rounded bg-stone-600/40 text-stone-300">
          {{ p.result_visibility === 'open' ? '实时公开' : '结束后公开' }}
        </span>
        <span class="text-xs text-stone-500">{{ p.totalVotes }} 人参与</span>
      </div>
      <p v-if="p.description" class="text-sm text-stone-400">{{ p.description }}</p>
      <div class="text-xs text-stone-400 space-y-1">
        <div v-for="o in p.options" :key="o.id" class="flex justify-between">
          <span>{{ o.label }}{{ p.multiple ? '(多)' : '' }}</span>
          <span class="text-stone-500">{{ o.votes }} 票</span>
        </div>
      </div>
      <div class="flex flex-wrap gap-2">
        <button v-if="p.status === 'draft'" class="btn-primary text-xs px-3 py-1.5" @click="moderate(p, 'open')">开启</button>
        <button v-if="p.status === 'open'" class="btn-secondary text-xs px-3 py-1.5" @click="moderate(p, 'close')">关闭</button>
        <button class="btn-secondary text-xs px-3 py-1.5 !text-rose-300" @click="moderate(p, 'delete')">删除</button>
        <button class="btn-secondary text-xs px-3 py-1.5" @click="editOptions(p)">改选项</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject } from 'vue'
import api from '@/services/api'

const notify = inject('notify') as any
const polls = ref<any[]>([])
const createOpen = ref(false)
const form = ref({ title: '', description: '', multiple: false, resultVisibility: 'open', optionsText: '' })

onMounted(load)

async function load() {
  try {
    const r: any = await api.getPollsAdmin()
    if (r.success) polls.value = r.data || []
  } catch (e) { console.error(e) }
}

async function create() {
  const options = form.value.optionsText.split('\n').map((x: string) => x.trim()).filter(Boolean)
  if (!form.value.title.trim() || options.length < 2) { notify?.error('标题与至少 2 个选项必填'); return }
  try {
    const r: any = await api.createPoll({
      title: form.value.title.trim(), description: form.value.description.trim(),
      multiple: form.value.multiple, resultVisibility: form.value.resultVisibility, options
    })
    if (r.success) {
      notify?.success(r.message || '已创建')
      createOpen.value = false
      form.value = { title: '', description: '', multiple: false, resultVisibility: 'open', optionsText: '' }
      await load()
    } else notify?.error(r.message || '创建失败')
  } catch (e: any) { notify?.error(e.message || '创建失败') }
}

async function moderate(p: any, action: 'open' | 'close' | 'delete') {
  if (action === 'delete' && !confirm(`确认删除投票「${p.title}」?选票一并删除。`)) return
  try {
    const r: any = await api.moderatePoll(p.id, action)
    if (r.success) { notify?.success(r.message || '已处理'); await load() } else notify?.error(r.message || '操作失败')
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

async function editOptions(p: any) {
  const text = prompt('选项(每行一个,保存会清空已有选票):', (p.options || []).map((o: any) => o.label).join('\n'))
  if (text == null) return
  const options = text.split('\n').map((x: string) => x.trim()).filter(Boolean)
  if (options.length < 2) { notify?.error('至少 2 个选项'); return }
  try {
    const r: any = await api.updatePoll(p.id, { title: p.title, description: p.description, options })
    if (r.success) { notify?.success(r.message || '已保存'); await load() } else notify?.error(r.message || '保存失败')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}

function pollStatusText(s: string) {
  return s === 'open' ? '进行中' : s === 'closed' ? '已结束' : '草稿'
}

function pollStatusClass(s: string) {
  return s === 'open' ? 'bg-emerald-500/20 text-emerald-300'
    : s === 'closed' ? 'bg-stone-600/40 text-stone-300'
    : 'bg-amber-500/20 text-amber-300'
}
</script>
