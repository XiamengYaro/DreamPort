<template>
  <div class="space-y-6">
    <!-- 发帖开关 -->
    <div class="card p-6 space-y-3">
      <h3 class="text-lg font-semibold text-white">论坛设置</h3>
      <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
        <input type="checkbox" v-model="cfg.moderation" class="accent-orange-500" />
        先审后发(关闭 = 发布即公开,敏感词过滤+事后删除兜底;开启 = 帖子/回复审核通过后展示)
      </label>
      <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
        <input type="checkbox" v-model="cfg.likeEnabled" class="accent-orange-500" /> 启用点赞
      </label>
      <button class="btn-primary text-sm" @click="saveConfig">保存论坛设置</button>
    </div>

    <!-- 待审核 -->
    <div class="card p-6 space-y-4">
      <h3 class="text-lg font-semibold text-white">待审核内容</h3>
      <EmptyState v-if="pending.threads.length === 0 && pending.replies.length === 0" icon="check-circle" text="没有待审核的帖子/回复" />
      <div v-for="t in pending.threads" :key="'t' + t.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800">
        <div class="flex items-center justify-between mb-1">
          <span class="text-white font-medium">{{ t.title }}</span>
          <span class="text-xs text-stone-500">{{ t.username }}</span>
        </div>
        <p class="text-xs text-stone-400 line-clamp-2 mb-2">{{ t.content }}</p>
        <div class="flex gap-2">
          <button class="btn-primary text-xs px-3 py-1.5" @click="moderateThread(t.id, 'approve')">通过</button>
          <button class="btn-secondary text-xs px-3 py-1.5 !text-rose-300" @click="moderateThread(t.id, 'reject')">拒绝</button>
          <button class="btn-secondary text-xs px-3 py-1.5" @click="moderateThread(t.id, 'delete')">删除</button>
        </div>
      </div>
      <div v-for="r in pending.replies" :key="'r' + r.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800">
        <div class="flex items-center justify-between mb-1">
          <span class="text-stone-300 text-sm">回复:{{ r.thread_title }}</span>
          <span class="text-xs text-stone-500">{{ r.username }}</span>
        </div>
        <p class="text-xs text-stone-400 line-clamp-2 mb-2">{{ r.content }}</p>
        <div class="flex gap-2">
          <button class="btn-primary text-xs px-3 py-1.5" @click="moderateReply(r.id, 'approve')">通过</button>
          <button class="btn-secondary text-xs px-3 py-1.5 !text-rose-300" @click="moderateReply(r.id, 'delete')">删除</button>
        </div>
      </div>
    </div>

    <!-- 板块管理 -->
    <div class="card p-6 space-y-4">
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold text-white">板块管理</h3>
        <button class="btn-secondary text-sm" @click="addSection">+ 新建板块</button>
      </div>
      <div v-for="s in sections" :key="s.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 space-y-2">
        <div class="grid grid-cols-1 sm:grid-cols-[1fr_2fr_90px_90px_auto] gap-2 items-center">
          <input v-model="s.name" class="input text-sm" placeholder="板块名" />
          <input v-model="s.description" class="input text-sm" placeholder="描述" />
          <input v-model.number="s.sort" type="number" class="input text-sm" placeholder="排序" />
          <label class="flex items-center gap-1.5 text-xs text-stone-300">
            <input type="checkbox" v-model="s.locked" class="accent-orange-500" /> 只读
          </label>
          <div class="flex gap-1.5">
            <button class="btn-primary text-xs px-3 py-1.5" @click="saveSection(s)">保存</button>
            <button class="btn-secondary text-xs px-3 py-1.5 !text-rose-300" @click="removeSection(s)">删除</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject } from 'vue'
import api from '@/services/api'
import EmptyState from '@/components/ui/EmptyState.vue'

const notify = inject('notify') as any
const pending = ref<any>({ threads: [], replies: [] })
const sections = ref<any[]>([])
const cfg = ref<any>({ moderation: false, likeEnabled: true })

onMounted(load)

async function load() {
  try {
    const [p, s, c]: any[] = await Promise.all([api.getForumAdminPending(), api.getForumSections(), api.getForumConfig()])
    if (p.success) pending.value = p.data || { threads: [], replies: [] }
    if (s.success) sections.value = s.data || []
    if (c.success) cfg.value = { moderation: !!c.data?.moderation, likeEnabled: c.data?.likeEnabled !== false }
  } catch (e) { console.error(e) }
}

async function saveConfig() {
  try {
    const r: any = await api.saveForumConfig(cfg.value.moderation, cfg.value.likeEnabled)
    if (r.success) notify?.success(r.message || '已保存')
    else notify?.error(r.message || '保存失败')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}

async function moderateThread(id: number, action: string) {
  try {
    const r: any = await api.moderateForumThread(id, action)
    if (r.success) { notify?.success('已处理'); await load() } else notify?.error(r.message || '操作失败')
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

async function moderateReply(id: number, action: string) {
  try {
    const r: any = await api.moderateForumReply(id, action)
    if (r.success) { notify?.success('已处理'); await load() } else notify?.error(r.message || '操作失败')
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

function addSection() {
  sections.value.push({ id: null, name: '', description: '', sort: sections.value.length, locked: false })
}

async function saveSection(s: any) {
  if (!s.name?.trim()) { notify?.error('板块名必填'); return }
  try {
    const r: any = await api.saveForumSection({ id: s.id, name: s.name.trim(), description: s.description, sort: s.sort, locked: s.locked })
    if (r.success) { notify?.success(r.message || '已保存'); await load() } else notify?.error(r.message || '保存失败')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}

async function removeSection(s: any) {
  if (!s.id) { sections.value = sections.value.filter(x => x !== s); return }
  if (!confirm(`确认删除板块「${s.name}」?`)) return
  try {
    const r: any = await api.deleteForumSection(s.id)
    if (r.success) { notify?.success('已删除'); await load() } else notify?.error(r.message || '删除失败')
  } catch (e: any) { notify?.error(e.message || '删除失败') }
}
</script>
