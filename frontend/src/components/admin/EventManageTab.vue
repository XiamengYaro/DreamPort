<template>
  <div class="card p-6 space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-lg font-semibold text-white">活动管理</h3>
      <button class="btn-secondary text-sm" @click="createOpen = !createOpen">+ 新建活动</button>
    </div>

    <div v-if="createOpen" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 space-y-3">
      <div><label class="block text-xs text-stone-500 mb-1">标题</label><input v-model="form.title" class="input text-sm" /></div>
      <div><label class="block text-xs text-stone-500 mb-1">描述</label><textarea v-model="form.description" rows="2" class="input text-sm"></textarea></div>
      <div><label class="block text-xs text-stone-500 mb-1">活动时间</label>
        <input v-model="form.eventAtLocal" type="datetime-local" class="input text-sm" /></div>
      <button class="btn-primary text-sm" @click="create">创建</button>
    </div>

    <EmptyState v-if="events.length === 0" icon="calendar" text="暂无活动" />
    <div v-for="e in events" :key="e.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800">
      <div class="flex flex-wrap items-center justify-between gap-2 mb-1">
        <span class="font-medium text-white">{{ e.title }}</span>
        <span class="text-xs text-stone-500">{{ new Date(e.event_at).toLocaleString('zh-CN') }} · {{ e.signups }} 人报名</span>
      </div>
      <p v-if="e.description" class="text-sm text-stone-400 mb-2">{{ e.description }}</p>
      <div class="flex gap-2">
        <button class="btn-secondary text-xs px-3 py-1.5" @click="viewSignups(e)">报名名单</button>
        <button class="btn-secondary text-xs px-3 py-1.5 !text-rose-300" @click="remove(e)">删除</button>
      </div>
      <div v-if="signupsFor === e.id" class="mt-2 p-3 rounded-lg bg-stone-800/60">
        <p v-if="signupList.length === 0" class="text-xs text-stone-500">暂无人报名</p>
        <p v-else class="text-xs text-stone-300">{{ signupList.map((s: any) => s.username).join('、') }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject } from 'vue'
import api from '@/services/api'
import EmptyState from '@/components/ui/EmptyState.vue'

const notify = inject('notify') as any
const events = ref<any[]>([])
const createOpen = ref(false)
const form = ref({ title: '', description: '', eventAtLocal: '' })
const signupsFor = ref<number | null>(null)
const signupList = ref<any[]>([])

onMounted(load)

async function load() {
  try {
    const r: any = await api.getEvents()
    if (r.success) events.value = r.data?.events || []
  } catch (e) { console.error(e) }
}

async function create() {
  if (!form.value.title.trim() || !form.value.eventAtLocal) { notify?.error('标题与时间必填'); return }
  try {
    const r: any = await api.createEvent({
      title: form.value.title.trim(),
      description: form.value.description.trim(),
      eventAt: new Date(form.value.eventAtLocal).getTime()
    })
    if (r.success) {
      notify?.success(r.message || '已创建')
      createOpen.value = false
      form.value = { title: '', description: '', eventAtLocal: '' }
      await load()
    } else notify?.error(r.message || '创建失败')
  } catch (e: any) { notify?.error(e.message || '创建失败') }
}

async function remove(e: any) {
  if (!confirm(`确认删除活动「${e.title}」?报名记录一并删除。`)) return
  try {
    const r: any = await api.deleteEvent(e.id)
    if (r.success) { notify?.success('已删除'); await load() } else notify?.error(r.message || '删除失败')
  } catch (e: any) { notify?.error(e.message || '删除失败') }
}

async function viewSignups(e: any) {
  try {
    const r: any = await api.getEventSignups(e.id)
    if (r.success) {
      signupsFor.value = e.id
      signupList.value = r.data?.list || []
      if (signupList.value.length === 0) notify?.info('暂无人报名')
    }
  } catch (e: any) { notify?.error(e.message || '加载失败') }
}
</script>
