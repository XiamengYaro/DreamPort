<template>
  <div class="space-y-6">
    <!-- 鉴权模式 -->
    <div class="card p-6">
      <h3 class="text-lg font-semibold text-white mb-2">服务器通道鉴权</h3>
      <p class="text-sm text-stone-400 mb-4">
        shared = 全局单令牌（各服 config.yml 填同一个 wl.internal.server-token）；
        per_server = 每服独立令牌（下方签发后粘贴到对应服的 config.yml，可按服吊销）。
        切换后立即生效，插件无需重启。
      </p>
      <div class="flex items-center gap-3">
        <select v-model="mode" class="input" style="max-width: 220px">
          <option value="shared">shared · 全局共享令牌</option>
          <option value="per_server">per_server · 按服独立令牌</option>
        </select>
        <button class="btn-primary" :disabled="mode === originalMode" @click="saveMode">
          保存鉴权模式
        </button>
      </div>
    </div>

    <!-- 服务器列表 -->
    <div class="card p-6">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold text-white">服务器注册表</h3>
        <button class="btn-secondary text-sm" @click="load">刷新</button>
      </div>
      <EmptyState v-if="servers.length === 0" text="暂无服务器上报记录（等待插件心跳）" />
      <div v-else class="overflow-x-auto">
        <table class="table w-full text-sm">
          <thead>
            <tr class="text-stone-400 text-left">
              <th class="py-2 pr-4">服务器</th>
              <th class="py-2 pr-4">角色</th>
              <th class="py-2 pr-4">版本</th>
              <th class="py-2 pr-4">在线</th>
              <th class="py-2 pr-4">最后心跳</th>
              <th class="py-2 pr-4">令牌</th>
              <th class="py-2 pr-4">状态</th>
              <th class="py-2 pr-4">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in servers" :key="s.serverId" class="border-t border-white/5">
              <td class="py-3 pr-4">
                <div class="text-white font-medium">{{ s.serverName || s.serverId }}</div>
                <div class="text-xs text-stone-500">{{ s.serverId }}</div>
              </td>
              <td class="py-3 pr-4 text-stone-300">{{ s.role || '-' }}</td>
              <td class="py-3 pr-4 text-stone-300">{{ s.version || '-' }}</td>
              <td class="py-3 pr-4 text-stone-300">{{ s.onlinePlayers }} / {{ s.maxPlayers }}</td>
              <td class="py-3 pr-4 text-stone-400">{{ timeAgo(s.lastHeartbeat) }}</td>
              <td class="py-3 pr-4">
                <span :class="s.hasToken ? 'text-emerald-400' : 'text-stone-500'">
                  {{ s.hasToken ? '已签发' : '未签发' }}
                </span>
              </td>
              <td class="py-3 pr-4">
                <span class="inline-flex items-center gap-1.5">
                  <span class="w-2 h-2 rounded-full inline-block"
                    :class="s.live ? 'bg-emerald-400' : 'bg-stone-600'"></span>
                  <span :class="s.enabled ? 'text-stone-300' : 'text-rose-400'">
                    {{ s.enabled ? (s.live ? '在线' : '离线') : '已停用' }}
                  </span>
                </span>
              </td>
              <td class="py-3 pr-4">
                <div class="flex gap-2">
                  <button class="btn-secondary text-xs px-3 py-1.5" @click="askIssue(s)">
                    {{ s.hasToken ? '轮换令牌' : '签发令牌' }}
                  </button>
                  <button v-if="s.enabled" class="btn-secondary text-xs px-3 py-1.5 !text-rose-300"
                    @click="toggle(s, false)">停用</button>
                  <button v-else class="btn-secondary text-xs px-3 py-1.5 !text-emerald-300"
                    @click="toggle(s, true)">启用</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 签发确认 -->
    <AppModal :open="issueTarget != null" title="签发/轮换按服令牌" size="sm" @close="issueTarget = null">
      <p class="text-stone-300 text-sm">
        将为 <span class="text-white font-medium">{{ issueTarget?.serverId }}</span> 生成新令牌，
        旧令牌立即失效（per_server 模式下该服需更新 config.yml 后重启插件才能重连）。
      </p>
      <template #footer>
        <button class="btn-secondary" @click="issueTarget = null">取消</button>
        <button class="btn-primary" @click="doIssue">生成令牌</button>
      </template>
    </AppModal>

    <!-- 令牌展示（仅一次） -->
    <AppModal :open="issuedToken != null" title="新令牌（仅本次显示）" size="sm" @close="issuedToken = null">
      <p class="text-sm text-stone-400 mb-3">
        请立即复制并粘贴到「{{ issuedServerId }}」的插件 config.yml → backend.server-token，关闭后无法再次查看。
      </p>
      <div class="flex gap-2">
        <code class="flex-1 break-all bg-black/30 rounded-lg p-3 text-xs text-orange-300 select-all">{{ issuedToken }}</code>
        <button class="btn-secondary text-xs px-3" @click="copyToken">复制</button>
      </div>
      <template #footer>
        <button class="btn-primary" @click="issuedToken = null">我已保存</button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject } from 'vue'
import api from '@/services/api'
import AppModal from '@/components/ui/AppModal.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const notify = inject('notify') as any

const mode = ref('shared')
const originalMode = ref('shared')
const servers = ref<any[]>([])
const issueTarget = ref<any>(null)
const issuedToken = ref<string | null>(null)
const issuedServerId = ref('')

onMounted(load)

async function load() {
  try {
    const r: any = await api.getAdminServers()
    if (r.success) {
      servers.value = r.data?.servers || []
      mode.value = r.data?.mode || 'shared'
      originalMode.value = mode.value
    }
  } catch (e) {
    console.error(e)
  }
}

async function saveMode() {
  try {
    const r: any = await api.setTokenMode(mode.value)
    if (r.success) {
      originalMode.value = mode.value
      notify.success('鉴权模式已保存')
      load()
    } else {
      notify.error(r.message || '保存失败')
    }
  } catch (e: any) {
    notify.error(e.message || '保存失败')
  }
}

function askIssue(s: any) {
  issueTarget.value = s
}

async function doIssue() {
  const target = issueTarget.value
  issueTarget.value = null
  if (!target) return
  try {
    const r: any = await api.issueServerToken(target.serverId)
    if (r.success) {
      issuedServerId.value = target.serverId
      issuedToken.value = r.data?.token || ''
      load()
    } else {
      notify.error(r.message || '签发失败')
    }
  } catch (e: any) {
    notify.error(e.message || '签发失败')
  }
}

async function toggle(s: any, enabled: boolean) {
  try {
    const r: any = await api.setServerEnabled(s.serverId, enabled)
    if (r.success) {
      notify.success(enabled ? '已启用' : '已停用')
      load()
    } else {
      notify.error(r.message || '操作失败')
    }
  } catch (e: any) {
    notify.error(e.message || '操作失败')
  }
}

async function copyToken() {
  try {
    await navigator.clipboard.writeText(issuedToken.value || '')
    notify.success('已复制')
  } catch {
    notify.error('复制失败，请手动选择复制')
  }
}

function timeAgo(ts: number): string {
  if (!ts) return '从未'
  const diff = Date.now() - ts
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return Math.floor(diff / 60_000) + ' 分钟前'
  if (diff < 86400_000) return Math.floor(diff / 3600_000) + ' 小时前'
  return Math.floor(diff / 86400_000) + ' 天前'
}
</script>
