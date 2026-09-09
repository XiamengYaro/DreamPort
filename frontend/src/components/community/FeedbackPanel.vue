<template>
  <div class="space-y-4">
    <!-- 会话详情视图 -->
    <template v-if="selected">
      <button class="btn-ghost text-sm mb-2" @click="selected = null; detail = {}">← 返回工单列表</button>
      <div class="card p-6">
        <div class="flex flex-wrap items-center gap-2 mb-1">
          <h3 class="text-lg font-semibold text-white">{{ selected.title }}</h3>
          <span class="text-[10px] px-1.5 py-0.5 rounded" :class="statusClass(selected.status)">{{ statusText(selected.status) }}</span>
          <span class="text-[10px] px-1.5 py-0.5 rounded bg-stone-600/40 text-stone-300">{{ categoryText(selected.category) }}</span>
        </div>
        <div class="text-xs text-stone-500 mb-4">#{{ selected.id }} · {{ formatTime(selected.created_at) }}</div>
        <div class="space-y-3">
          <div v-for="m in detail.messages || []" :key="m.id"
            class="flex" :class="m.sender_role === 'admin' ? 'justify-start' : 'justify-end'">
            <div class="max-w-[80%]">
              <div class="text-[11px] text-stone-500 mb-1"
                :class="m.sender_role === 'admin' ? 'text-left' : 'text-right'">
                {{ m.sender_role === 'admin' ? '管理员 · ' : '' }}{{ m.sender }} · {{ formatTime(m.created_at) }}
              </div>
              <div class="rounded-2xl px-4 py-2.5 text-sm whitespace-pre-wrap"
                :class="m.sender_role === 'admin'
                  ? 'bg-sky-500/10 text-stone-200 border border-sky-500/20'
                  : 'bg-orange-500/10 text-stone-200 border border-orange-500/20'">
                {{ m.content }}
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="selected.status !== 'closed'" class="card p-4">
        <textarea v-model="replyText" class="input min-h-[80px]" placeholder="继续补充说明…"></textarea>
        <div class="flex gap-2 mt-2">
          <button class="btn-primary text-sm" :disabled="!replyText.trim() || sending" @click="sendReply">
            {{ sending ? '发送中…' : '发送' }}
          </button>
          <button class="btn-secondary text-sm" @click="closeTicket">确认解决并关闭</button>
        </div>
      </div>
      <p v-else class="text-xs text-stone-500 text-center">工单已关闭 · 如仍有问题请新建工单</p>
    </template>

    <!-- 列表 + 新建 -->
    <template v-else>
      <div class="card p-4">
        <div v-if="!isLoggedIn" class="text-sm text-stone-400 text-center py-2">登录后即可提交反馈与查看回复</div>
        <template v-else>
          <div class="grid grid-cols-1 sm:grid-cols-[140px_1fr] gap-2 mb-2">
            <select v-model="form.category" class="input">
              <option value="suggestion">功能建议</option>
              <option value="bug">问题反馈</option>
              <option value="other">其他</option>
            </select>
            <input v-model="form.title" class="input" placeholder="一句话标题(≤128 字)" maxlength="128" />
          </div>
          <textarea v-model="form.content" class="input min-h-[90px]" placeholder="详细描述…"></textarea>
          <button class="btn-primary text-sm mt-2" :disabled="!form.title.trim() || !form.content.trim() || sending"
            @click="submit">{{ sending ? '提交中…' : '提交反馈' }}</button>
        </template>
      </div>

      <EmptyState v-if="tickets.length === 0 && !loading && isLoggedIn" text="暂无工单,有问题或建议就提交吧" />
      <div v-for="t in tickets" :key="t.id" class="card p-4 cursor-pointer hover:border-orange-500/40 transition-colors"
        @click="openTicket(t)">
        <div class="flex items-center justify-between gap-3">
          <div class="min-w-0">
            <div class="text-white font-medium truncate">{{ t.title }}</div>
            <div class="text-xs text-stone-500 mt-0.5">#{{ t.id }} · {{ categoryText(t.category) }} · {{ formatTime(t.updated_at || t.created_at) }}</div>
          </div>
          <span class="text-[10px] px-1.5 py-0.5 rounded shrink-0" :class="statusClass(t.status)">{{ statusText(t.status) }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, inject } from 'vue'
import api from '@/services/api'
import EmptyState from '@/components/ui/EmptyState.vue'

const notify = inject('notify') as any
const isLoggedIn = computed(() => !!localStorage.getItem('token'))

const tickets = ref<any[]>([])
const selected = ref<any>(null)
const detail = ref<any>({})
const replyText = ref('')
const sending = ref(false)
const loading = ref(false)
const form = ref({ category: 'suggestion', title: '', content: '' })

onMounted(load)

async function load() {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const r: any = await api.getMyFeedback()
    if (r.success) tickets.value = r.data || []
  } catch (e) { console.error(e) }
  loading.value = false
}

async function openTicket(t: any) {
  selected.value = t
  try {
    const r: any = await api.getFeedbackDetail(t.id)
    if (r.success) detail.value = r.data || {}
  } catch (e) { console.error(e) }
}

async function sendReply() {
  if (!selected.value) return
  sending.value = true
  try {
    const r: any = await api.replyFeedback(selected.value.id, replyText.value.trim())
    if (r.success) {
      notify?.success('已发送')
      replyText.value = ''
      await openTicket(selected.value)
    } else notify?.error(r.message || '发送失败')
  } catch (e: any) { notify?.error(e.message || '发送失败') }
  sending.value = false
}

async function closeTicket() {
  if (!selected.value) return
  if (!confirm('确认问题已解决并关闭该工单?')) return
  try {
    const r: any = await api.closeFeedback(selected.value.id)
    if (r.success) {
      notify?.success('已关闭')
      selected.value = null
      detail.value = {}
      await load()
    } else notify?.error(r.message || '操作失败')
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

async function submit() {
  sending.value = true
  try {
    const r: any = await api.createFeedback(form.value.category, form.value.title.trim(), form.value.content.trim())
    if (r.success) {
      notify?.success(r.message || '已提交')
      form.value = { category: 'suggestion', title: '', content: '' }
      await load()
    } else notify?.error(r.message || '提交失败')
  } catch (e: any) { notify?.error(e.message || '提交失败') }
  sending.value = false
}

function statusText(s: string) {
  return s === 'open' ? '待处理' : s === 'answered' ? '已回复' : '已关闭'
}

function statusClass(s: string) {
  return s === 'open' ? 'bg-amber-500/20 text-amber-300'
    : s === 'answered' ? 'bg-emerald-500/20 text-emerald-300'
    : 'bg-stone-600/40 text-stone-300'
}

function categoryText(c: string) {
  return c === 'suggestion' ? '功能建议' : c === 'bug' ? '问题反馈' : '其他'
}

function formatTime(t: number) {
  return t ? new Date(t).toLocaleString('zh-CN') : '-'
}
</script>
