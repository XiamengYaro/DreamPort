<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/services/api'

const router = useRouter()
const checking = ref(true)
const setupRequired = ref(false)
const submitting = ref(false)
const error = ref('')
const done = ref(false)
const doneMessage = ref('')

const username = ref('')
const password = ref('')
const passwordConfirm = ref('')
const email = ref('')
const mode = ref<'fresh' | 'import'>('fresh')
const fileInput = ref<HTMLInputElement | null>(null)
const fileName = ref('')

onMounted(async () => {
  try {
    const res: any = await api.getSetupStatus()
    setupRequired.value = !!res.setupRequired
    if (!setupRequired.value) {
      router.replace('/login')
    }
  } catch {
    setupRequired.value = true
  } finally {
    checking.value = false
  }
})

const onFileChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  fileName.value = input.files && input.files[0] ? input.files[0].name : ''
}

const submit = async () => {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请填写管理员用户名与密码'
    return
  }
  if (password.value !== passwordConfirm.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  if (mode.value === 'import' && (!fileInput.value || !fileInput.value.files?.[0])) {
    error.value = '导入模式需要选择旧库 .sql 文件'
    return
  }
  submitting.value = true
  try {
    const form = new FormData()
    form.append('username', username.value)
    form.append('password', password.value)
    if (email.value) form.append('email', email.value)
    form.append('mode', mode.value)
    if (mode.value === 'import' && fileInput.value?.files?.[0]) {
      form.append('file', fileInput.value.files[0])
    }
    const res: any = await api.submitSetup(form)
    done.value = true
    const report = res.data || {}
    if (report.importReport) {
      const rows = report.importReport.importedRows || {}
      const total = Object.values(rows).reduce((a: any, b: any) => Number(a) + Number(b), 0)
      doneMessage.value = report.adminClaimedExisting
        ? `已认领旧账号 ${username.value}（密码已重置为刚才设置的密码），共导入 ${total} 行历史数据。`
        : `管理员已创建，共导入 ${total} 行历史数据。`
    } else {
      doneMessage.value = report.adminCreated ? '管理员账号已创建，系统为全新部署。' : '初始化完成。'
    }
    setTimeout(() => router.push('/login'), 2500)
  } catch (err: any) {
    error.value = err.message || '初始化失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center px-4 pt-20 pb-10">
    <div class="w-full max-w-xl">
      <div class="text-center mb-8">
        <h1 class="text-3xl font-bold text-white drop-shadow-lg">系统初始化</h1>
        <p class="text-stone-300 mt-2">检测到这是首次启动，请创建管理员账号并选择部署方式</p>
      </div>

      <div v-if="checking" class="card p-8 text-center text-stone-300">检测中…</div>

      <div v-else-if="done" class="card p-8 text-center space-y-4">
        <div class="text-4xl">🎉</div>
        <div class="text-lg font-semibold text-white">初始化完成</div>
        <div class="text-sm text-stone-300">{{ doneMessage }}</div>
        <router-link to="/login" class="inline-block px-6 py-2.5 rounded-xl bg-orange-500 text-white text-sm font-medium hover:bg-orange-600 transition-all">
          前往登录
        </router-link>
      </div>

      <div v-else class="card p-8 space-y-5">
        <!-- 管理员账号 -->
        <div>
          <div class="text-sm font-medium text-stone-300 mb-1">管理员玩家名</div>
          <input v-model="username" placeholder="3-16 位字母数字_-"
            class="w-full px-4 py-2.5 rounded-xl bg-stone-800/70 border border-stone-700 text-white text-sm focus:outline-none focus:border-orange-500/50" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="text-sm font-medium text-stone-300 mb-1">密码</div>
            <input v-model="password" type="password" placeholder="至少 8 位"
              class="w-full px-4 py-2.5 rounded-xl bg-stone-800/70 border border-stone-700 text-white text-sm focus:outline-none focus:border-orange-500/50" />
          </div>
          <div>
            <div class="text-sm font-medium text-stone-300 mb-1">确认密码</div>
            <input v-model="passwordConfirm" type="password"
              class="w-full px-4 py-2.5 rounded-xl bg-stone-800/70 border border-stone-700 text-white text-sm focus:outline-none focus:border-orange-500/50" />
          </div>
        </div>
        <div>
          <div class="text-sm font-medium text-stone-300 mb-1">邮箱（可选，用于找回密码）</div>
          <input v-model="email" type="email"
            class="w-full px-4 py-2.5 rounded-xl bg-stone-800/70 border border-stone-700 text-white text-sm focus:outline-none focus:border-orange-500/50" />
        </div>

        <!-- 部署模式 -->
        <div>
          <div class="text-sm font-medium text-stone-300 mb-2">部署方式</div>
          <div class="grid grid-cols-2 gap-3">
            <button @click="mode = 'fresh'"
              class="p-4 rounded-xl border text-left transition-all"
              :class="mode === 'fresh' ? 'border-orange-500/50 bg-orange-500/10' : 'border-stone-700 hover:border-stone-600'">
              <div class="text-white font-medium text-sm">✨ 全新部署</div>
              <div class="text-xs text-stone-400 mt-1">从零开始，不导入历史数据</div>
            </button>
            <button @click="mode = 'import'"
              class="p-4 rounded-xl border text-left transition-all"
              :class="mode === 'import' ? 'border-orange-500/50 bg-orange-500/10' : 'border-stone-700 hover:border-stone-600'">
              <div class="text-white font-medium text-sm">📦 导入旧版数据库</div>
              <div class="text-xs text-stone-400 mt-1">上传旧版 XMWhitelist 的 .sql 导出</div>
            </button>
          </div>
        </div>

        <div v-if="mode === 'import'">
          <div class="text-sm font-medium text-stone-300 mb-1">旧库导出文件（mysqldump 的 .sql）</div>
          <input ref="fileInput" type="file" accept=".sql" @change="onFileChange"
            class="w-full text-sm text-stone-300 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0
                   file:bg-orange-500/15 file:text-orange-400 hover:file:bg-orange-500/25 file:cursor-pointer" />
          <p class="text-xs text-stone-500 mt-2">
            将导入旧版 9 张表：用户（旧密码保留，可无感登录）、审计、邀请、通知、进服记录、申诉、村谱、公共机器。
            若管理员玩家名与旧数据同名，将自动认领该账号。
          </p>
        </div>

        <div v-if="error" class="text-sm text-red-400">{{ error }}</div>

        <button @click="submit" :disabled="submitting"
          class="w-full py-3 rounded-xl bg-orange-500 text-white font-medium hover:bg-orange-600 disabled:opacity-40 disabled:cursor-not-allowed transition-all">
          {{ submitting ? '初始化中…' : '完成初始化' }}
        </button>
      </div>
    </div>
  </div>
</template>
