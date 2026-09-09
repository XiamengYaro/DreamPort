<template>
  <div class="card p-6 space-y-4">
    <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="cursor-arrow-rays" class="w-5 h-5" /> Webhook 事件推送</h3>
    <p class="text-xs text-stone-500">审核/封禁/问卷/反馈/论坛/投票/礼包等事件发生时,向配置的 URL 发送 POST(JSON),带 HMAC-SHA256 签名头 <code class="text-orange-400">X-DP-Signature</code>(对 <code>timestamp.body</code> 签名,时间戳在 <code>X-DP-Timestamp</code>),可用于对接 QQ 机器人/Discord/自建系统。</p>
    <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
      <input type="checkbox" v-model="cfg.enabled" class="accent-orange-500" /> 启用 Webhook 推送
    </label>
    <div>
      <div class="flex items-center justify-between mb-1">
        <label class="text-xs text-stone-500">目标 URL(可多条;secret 留空则不签名)</label>
        <button @click="addUrl" class="btn-secondary text-xs py-1 px-2">+ 添加</button>
      </div>
      <div v-for="(u, i) in cfg.urls" :key="i" class="flex flex-wrap gap-2 mb-2">
        <input v-model="u.url" class="input flex-1 min-w-[220px] font-mono text-sm" placeholder="https://example.com/webhook" />
        <input v-model="u.secret" class="input w-56 font-mono text-sm" placeholder="签名密钥(可空)" />
        <button @click="cfg.urls.splice(i, 1)" class="text-rose-400 hover:text-rose-300 text-sm px-2">删除</button>
      </div>
    </div>
    <div>
      <label class="block text-xs text-stone-500 mb-1">事件过滤(留空 = 推送全部;可选:review.* / appeal.* / questionnaire.submitted / reward.sent / feedback.* / forum.* / poll.* / community.* / server.*)</label>
      <textarea v-model="eventsText" rows="2" class="input w-full font-mono text-sm resize-y"
        placeholder="每行一个事件类型,如:&#10;review.approved&#10;feedback.replied"></textarea>
    </div>
    <div class="flex gap-2">
      <button @click="save" class="btn-primary text-sm">保存 Webhook 配置</button>
      <button @click="test" class="btn-secondary text-sm" :disabled="!cfg.enabled">发送测试事件</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject } from 'vue'
import api from '@/services/api'
import AppIcon from '@/components/AppIcon.vue'

const notify = inject('notify') as any
const cfg = ref<any>({ enabled: false, urls: [] as any[] })
const eventsText = ref('')

onMounted(load)

async function load() {
  try {
    const r: any = await api.getWebhookConfig()
    if (r.success) {
      cfg.value = {
        enabled: !!r.data?.enabled,
        urls: Array.isArray(r.data?.urls) ? r.data.urls : []
      }
      eventsText.value = (Array.isArray(r.data?.events) ? r.data.events : []).join('\n')
    }
  } catch (e) { console.error(e) }
}

function addUrl() {
  cfg.value.urls.push({ url: '', secret: '' })
}

async function save() {
  const urls = cfg.value.urls.filter((u: any) => (u.url || '').trim())
  const events = eventsText.value.split('\n').map((x: string) => x.trim()).filter(Boolean)
  try {
    const r: any = await api.saveWebhookConfig({ enabled: cfg.value.enabled, urls, events })
    if (r.success) { notify?.success('Webhook 配置已保存'); await load() } else notify?.error(r.message || '保存失败')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}

async function test() {
  try {
    const r: any = await api.testWebhook()
    if (r.success) notify?.success(r.message || '测试事件已发送')
    else notify?.error(r.message || '发送失败')
  } catch (e: any) { notify?.error(e.message || '发送失败') }
}
</script>
