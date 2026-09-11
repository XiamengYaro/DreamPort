<template>
  <div class="space-y-4">
    <!-- 帖子列表视图 -->
    <template v-if="!selectedThread">
      <div class="flex flex-wrap gap-2 items-center">
        <button @click="selectSection(null)" class="px-3 py-1.5 rounded-xl text-sm transition-colors"
          :class="!sectionId ? 'bg-orange-500/15 text-orange-400' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          全部
        </button>
        <button v-for="s in sections" :key="s.id" @click="selectSection(s.id)"
          class="px-3 py-1.5 rounded-xl text-sm transition-colors"
          :class="sectionId === s.id ? 'bg-orange-500/15 text-orange-400' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          {{ s.name }}<span v-if="s.locked" class="text-[10px] text-stone-500 ml-1">(只读)</span>
        </button>
        <button v-if="isLoggedIn" class="btn-primary text-sm ml-auto" @click="composeOpen = true">+ 发帖</button>
      </div>

      <EmptyState v-if="threads.length === 0 && !loading" text="暂无帖子,来发第一帖吧" />
      <div v-for="t in threads" :key="t.id"
        class="card p-4 cursor-pointer hover:border-orange-500/40 transition-colors"
        @click="openThread(t)">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-1.5 mb-1">
              <span v-if="t.pinned" class="text-[10px] px-1.5 py-0.5 rounded bg-orange-500/20 text-orange-300">置顶</span>
              <span v-if="t.essence" class="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300">精华</span>
              <span v-if="t.status === 'pending'" class="text-[10px] px-1.5 py-0.5 rounded bg-stone-600/40 text-stone-300">审核中</span>
              <span v-if="t.status === 'rejected'" class="text-[10px] px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-300">未通过</span>
              <span v-if="t.locked" class="text-[10px] px-1.5 py-0.5 rounded bg-stone-600/40 text-stone-300">锁定</span>
            </div>
            <div class="text-white font-medium truncate">{{ t.title }}</div>
            <div class="text-xs text-stone-500 mt-1">{{ t.username }} · {{ timeAgo(t.last_reply_at || t.created_at) }}</div>
          </div>
          <div class="text-xs text-stone-500 shrink-0 flex gap-3">
            <span>💬 {{ t.reply_count || 0 }}</span>
            <span>👍 {{ t.likes ?? 0 }}</span>
          </div>
        </div>
      </div>
      <AppPagination :page="page" :pages="totalPages" @change="page = $event" />
    </template>

    <!-- 帖子详情视图 -->
    <template v-else>
      <button class="btn-ghost text-sm mb-2" @click="closeThread">← 返回列表</button>
      <div class="card p-6">
        <div class="flex flex-wrap items-center gap-2 mb-2">
          <span v-if="selectedThread.pinned" class="text-[10px] px-1.5 py-0.5 rounded bg-orange-500/20 text-orange-300">置顶</span>
          <span v-if="selectedThread.essence" class="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300">精华</span>
          <span v-if="selectedThread.status === 'pending'" class="text-[10px] px-1.5 py-0.5 rounded bg-stone-600/40 text-stone-300">审核中</span>
          <span v-if="selectedThread.edited" class="text-[10px] text-stone-500">已编辑</span>
        </div>
        <h2 class="text-xl font-bold text-white mb-2">{{ selectedThread.title }}</h2>
        <div class="text-xs text-stone-500 mb-4">{{ selectedThread.username }} · {{ formatTime(selectedThread.created_at) }}</div>
        <div class="prose prose-invert max-w-none text-sm" v-html="renderMarkdown(selectedThread.content || '')"></div>
        <div class="flex items-center gap-3 mt-4 pt-4 border-t border-white/5">
          <button @click="toggleLike" class="btn-secondary text-sm"
            :class="detail.myLike ? '!text-orange-400 !border-orange-500/40' : ''"
            :disabled="!isLoggedIn">
            👍 {{ likeCount }}
          </button>
          <button v-if="detail.mine && withinEditWindow" class="btn-secondary text-sm" @click="openEdit">编辑</button>
          <span class="text-xs text-stone-500 ml-auto">回复 {{ detail.replyTotal || 0 }}</span>
        </div>
      </div>

      <!-- 回复列表 -->
      <div v-for="r in detail.replies || []" :key="r.id" class="card p-4" :class="r.status === 'pending' ? 'opacity-70' : ''">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <AppAvatar :name="r.username" size-class="w-7 h-7" />
            <span class="text-sm text-white">{{ r.username }}</span>
            <span v-if="r.status === 'pending'" class="text-[10px] px-1.5 py-0.5 rounded bg-stone-600/40 text-stone-300">审核中</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-xs text-stone-500">{{ timeAgo(r.created_at) }}</span>
            <button @click="likeReply(r)" class="text-xs text-stone-400 hover:text-orange-400"
              :disabled="!isLoggedIn">👍 {{ r.likes ?? 0 }}</button>
          </div>
        </div>
        <div class="prose prose-invert max-w-none text-sm" v-html="renderMarkdown(r.content || '')"></div>
      </div>
      <AppPagination :page="replyPage" :pages="replyTotalPages" @change="replyPage = $event" />

      <!-- 回复框 -->
      <div v-if="isLoggedIn && !detail.locked" class="card p-4">
        <RichEditor v-model="replyText" :min-height="90" placeholder="写下你的回复…(@提及 通知对方)" />
        <button class="btn-primary text-sm mt-2" :disabled="!replyText.trim() || sending" @click="sendReply">
          {{ sending ? '发送中…' : '回复' }}
        </button>
      </div>
      <p v-else-if="detail.locked" class="text-xs text-stone-500 text-center">帖子已锁定,无法回复</p>
      <p v-else class="text-xs text-stone-500 text-center">登录后即可回复</p>
    </template>

    <!-- 发帖/编辑弹窗 -->
    <AppModal :open="composeOpen" :title="editing ? '编辑帖子' : '发布新帖'" size="lg" @close="composeOpen = false">
      <div class="space-y-3">
        <div>
          <label class="block text-xs text-stone-500 mb-1">板块</label>
          <select v-model.number="composeForm.sectionId" class="input" :disabled="editing">
            <option v-for="s in sections" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div>
          <label class="block text-xs text-stone-500 mb-1">标题</label>
          <input v-model="composeForm.title" class="input" maxlength="128" />
        </div>
        <div>
          <label class="block text-xs text-stone-500 mb-1">内容(支持 @提及 通知对方)</label>
          <RichEditor v-model="composeForm.content" :min-height="160" placeholder="正文…" />
        </div>
      </div>
      <template #footer>
        <button class="btn-secondary" @click="composeOpen = false">取消</button>
        <button class="btn-primary" :disabled="sending" @click="submitCompose">
          {{ sending ? '提交中…' : (editing ? '保存' : '发布') }}
        </button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, inject } from 'vue'
import api from '@/services/api'
import { renderMarkdown } from '@/lib/markdown'
import AppModal from '@/components/ui/AppModal.vue'
import AppPagination from '@/components/ui/AppPagination.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import RichEditor from '@/components/ui/RichEditor.vue'

const notify = inject('notify') as any
const isLoggedIn = computed(() => !!localStorage.getItem('token'))

const sections = ref<any[]>([])
const sectionId = ref<number | null>(null)
const threads = ref<any[]>([])
const page = ref(1)
const totalPages = ref(1)
const loading = ref(false)

const selectedThread = ref<any>(null)
const detail = ref<any>({})
const replyPage = ref(1)
const replyText = ref('')
const sending = ref(false)
const likeCount = ref(0)
const withinEditWindow = computed(() => {
  if (!detail.value?.created_at) return false
  return Date.now() - detail.value.created_at < 10 * 60_000
})

const composeOpen = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const composeForm = ref({ sectionId: null as number | null, title: '', content: '' })

onMounted(async () => {
  await loadSections()
  await loadThreads()
})

watch([sectionId, page], () => loadThreads())
watch(replyPage, () => loadThread())

async function loadSections() {
  try {
    const r: any = await api.getForumSections()
    if (r.success) sections.value = r.data || []
  } catch (e) { console.error(e) }
}

async function loadThreads() {
  loading.value = true
  try {
    const r: any = await api.getForumThreads(sectionId.value, page.value)
    if (r.success) {
      threads.value = r.data?.threads || []
      totalPages.value = Math.max(1, Math.ceil((r.data?.total || 0) / (r.data?.pageSize || 20)))
    }
  } catch (e) { console.error(e) }
  loading.value = false
}

function selectSection(id: number | null) {
  sectionId.value = id
  page.value = 1
}

async function openThread(t: any) {
  selectedThread.value = t
  replyPage.value = 1
  await loadThread()
}

function closeThread() {
  selectedThread.value = null
  detail.value = {}
  loadThreads()
}

async function loadThread() {
  if (!selectedThread.value) return
  try {
    const r: any = await api.getForumThread(selectedThread.value.id, replyPage.value)
    if (r.success) {
      detail.value = r.data || {}
      selectedThread.value = { ...selectedThread.value, ...r.data }
      likeCount.value = r.data?.like_count ?? 0
    } else {
      notify?.error(r.message || '加载失败')
      selectedThread.value = null
    }
  } catch (e: any) {
    notify?.error(e.message || '加载失败')
    selectedThread.value = null
  }
}

const replyTotalPages = computed(() => Math.max(1, Math.ceil((detail.value.replyTotal || 0) / (detail.value.replyPageSize || 20))))

async function toggleLike() {
  if (!selectedThread.value) return
  try {
    const r: any = await api.likeForumTarget('thread', selectedThread.value.id)
    if (r.success) {
      detail.value.myLike = r.data?.liked
      likeCount.value = r.data?.likes
    } else notify?.error(r.message || '操作失败')
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

async function likeReply(r: any) {
  try {
    const res: any = await api.likeForumTarget('reply', r.id)
    if (res.success) r.likes = res.data?.likes
  } catch (e) { console.error(e) }
}

async function sendReply() {
  if (!selectedThread.value) return
  sending.value = true
  try {
    const r: any = await api.replyForumThread(selectedThread.value.id, replyText.value.trim())
    if (r.success) {
      notify?.success(r.message || '回复成功')
      replyText.value = ''
      await loadThread()
    } else notify?.error(r.message || '回复失败')
  } catch (e: any) { notify?.error(e.message || '回复失败') }
  sending.value = false
}

function openEdit() {
  editing.value = true
  editId.value = selectedThread.value.id
  composeForm.value = {
    sectionId: selectedThread.value.section_id,
    title: selectedThread.value.title,
    content: selectedThread.value.content
  }
  composeOpen.value = true
}

async function submitCompose() {
  sending.value = true
  try {
    let r: any
    if (editing.value && editId.value) {
      r = await api.editForumThread(editId.value, composeForm.value.title.trim(), composeForm.value.content.trim())
    } else {
      if (!composeForm.value.sectionId) { notify?.error('请选择板块'); sending.value = false; return }
      r = await api.createForumThread(composeForm.value.sectionId, composeForm.value.title.trim(), composeForm.value.content.trim())
    }
    if (r.success) {
      notify?.success(r.message || '已发布')
      composeOpen.value = false
      editing.value = false
      composeForm.value = { sectionId: null, title: '', content: '' }
      if (selectedThread.value) await loadThread()
      else await loadThreads()
    } else notify?.error(r.message || '提交失败')
  } catch (e: any) { notify?.error(e.message || '提交失败') }
  sending.value = false
}

function timeAgo(ts: number): string {
  if (!ts) return ''
  const diff = Date.now() - ts
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return Math.floor(diff / 60_000) + ' 分钟前'
  if (diff < 86400_000) return Math.floor(diff / 3600_000) + ' 小时前'
  if (diff < 30 * 86400_000) return Math.floor(diff / 86400_000) + ' 天前'
  return new Date(ts).toLocaleDateString('zh-CN')
}

function formatTime(t: number) {
  return t ? new Date(t).toLocaleString('zh-CN') : '-'
}
</script>
