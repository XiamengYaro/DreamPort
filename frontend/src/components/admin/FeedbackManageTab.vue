<template>
  <div class="space-y-6">
    <!-- 会话视图 -->
    <template v-if="selected">
      <button class="btn-ghost text-sm mb-2" @click="selected = null; detail = {}">← 返回工单列表</button>
      <div class="card p-6">
        <div class="flex flex-wrap items-center gap-2 mb-1">
          <h3 class="text-lg font-semibold text-white">{{ selected.title }}</h3>
          <span class="text-[10px] px-1.5 py-0.5 rounded" :class="statusClass(selected.status)">{{ statusText(selected.status) }}</span>
        </div>
        <div class="text-xs text-stone-500 mb-4">{{ selected.username }} · #{{ selected.id }} · {{ categoryText(selected.category) }}</div>
        <div class="space-y-3">
          <div v-for="m in detail.messages || []" :key="m.id" class="flex"
            :class="m.sender_role === 'admin' ? 'justify-end' : 'justify-start'">
            <div class="max-w-[80%]">
              <div class="text-[11px] text-stone-500 mb-1" :class="m.sender_role === 'admin' ? 'text-right' : 'text-left'">
                {{ m.sender }} · {{ formatTime(m.created_at) }}
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
        <textarea v-model="replyText" class="input min-h-[80px]" placeholder="回复玩家(会同时发站内铃铛+邮件通知)…"></textarea>
        <div class="flex gap-2 mt-2">
          <button class="btn-primary text-sm" :disabled="!replyText.trim() || sending" @click="sendReply">
            {{ sending ? '发送中…' : '回复并通知玩家' }}
          </button>
          <button class="btn-secondary text-sm" @click="closeTicket">关闭工单</button>
        </div>
      </div>
    </template>

    <!-- 列表 -->
    <template v-else>
      <div class="card p-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-white">反馈工单</h3>
          <select v-model="statusFilter" class="input w-36 text-sm">
            <option value="">全部状态</option>
            <option value="open">待处理</option>
            <option value="answered">已回复</option>
            <option value="closed">已关闭</option>
          </select>
        </div>
        <EmptyState v-if="tickets.length === 0" icon="check-circle" text="暂无工单" />
        <div v-for="t in tickets" :key="t.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 mb-2 cursor-pointer hover:border-orange-500/40 transition-colors"
          @click="openTicket(t)">
          <div class="flex items-center justify-between gap-3">
            <div class="min-w-0">
              <div class="text-white font-medium truncate">{{ t.title }}</div>
              <div class="text-xs text-stone-500 mt-0.5">{{ t.username }} · {{ categoryText(t.category) }} · {{ formatTime(t.updated_at || t.created_at) }}</div>
            </div>
            <span class="text-[10px] px-1.5 py-0.5 rounded shrink-0" :class="statusClass(t.status)">{{ statusText(t.status) }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, inject } from 'vue'
import api from '@/services/api'
import EmptyState from '@/components/ui/EmptyState.vue'

const notify = inject('notify') as any
const tickets = ref<any[]>([])
const statusFilter = ref('')
const selected = ref<any>(null)
const detail = ref<any>({})
const replyText = ref('')
const sending = ref(false)

onMounted(load)
watch(statusFilter, load)

async function load() {
  try {
    const r: any = await api.getFeedbackAdmin(statusFilter.value)
    if (r.success) tickets.value = r.data || []
  } catch (e) { console.error(e) }
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
    const r: any = await api.adminReplyFeedback(selected.value.id, replyText.value.trim())
    if (r.success) {
      notify?.success('已回复并通知玩家')
      replyText.value = ''
      await openTicket(selected.value)
    } else notify?.error(r.message || '回复失败')
  } catch (e: any) { notify?.error(e.message || '回复失败') }
  sending.value = false
}

async function closeTicket() {
  if (!selected.value) return
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
