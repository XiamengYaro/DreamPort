<template>
  <div class="min-h-screen flex items-center justify-center p-4 overflow-y-auto pb-24">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <img :src="logoUrl" :alt="brand.short" class="w-20 h-20 rounded-2xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
        <h1 class="text-3xl font-bold tracking-tight text-white">注册 {{ brand.short }} 账户</h1>
        <p class="mt-1 text-stone-400">{{ brand.tagline }}</p>
      </div>

      <div class="card p-8">
        <form @submit.prevent="handleRegister">
          <div class="mb-4">
            <label class="block text-sm font-medium mb-2 text-stone-300">Minecraft ID</label>
            <input v-model="form.minecraftName" type="text" class="input" placeholder="请输入你的 Minecraft 游戏名" />
            <p class="mt-1 text-xs text-stone-500">正版游戏名（3-16 位字母数字下划线），必须与你的 Minecraft 游戏名完全一致</p>
          </div>

          <div class="mb-4">
            <label class="block text-sm font-medium mb-2 text-stone-300">邮箱</label>
            <input v-model="form.email" type="email" class="input" placeholder="请输入邮箱地址" required />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-2 text-stone-300">密码</label>
            <div class="relative">
              <input :type="showPassword ? 'text' : 'password'" v-model="form.password" class="input pr-10" placeholder="至少6位" required />
              <button type="button" @click="showPassword = !showPassword" class="absolute right-3 top-1/2 -translate-y-1/2 text-stone-400 hover:text-white">
                <svg v-if="!showPassword" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                </svg>
              </button>
            </div>
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-2 text-stone-300">确认密码</label>
            <div class="relative">
              <input :type="showConfirmPassword ? 'text' : 'password'" v-model="form.confirmPassword" class="input pr-10" placeholder="请再次输入密码" required />
              <button type="button" @click="showConfirmPassword = !showConfirmPassword" class="absolute right-3 top-1/2 -translate-y-1/2 text-stone-400 hover:text-white">
                <svg v-if="!showConfirmPassword" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                </svg>
              </button>
            </div>
          </div>
          <div class="mb-6">
            <label class="block text-sm font-medium mb-2 text-stone-300">邮箱验证码</label>
            <div class="flex gap-2">
              <input v-model="form.verifyCode" type="text" class="input flex-1" placeholder="请输入验证码" required />
              <button type="button" class="btn-secondary whitespace-nowrap text-sm" @click="sendVerifyCode" :disabled="codeCooldown > 0">
                {{ codeCooldown > 0 ? `${codeCooldown}s` : '发送验证码' }}
              </button>
            </div>
          </div>

          <!-- 服务器守则(注册强制阅读→勾选同意,否则无法提交) -->
          <div class="mb-6 p-4 rounded-xl border-2 border-orange-500/40 bg-stone-900/40">
            <h2 class="text-sm font-semibold text-white mb-2 flex items-center gap-2">
              <AppIcon name="shield-check" class="w-4 h-4 text-orange-400" /> 服务器守则
            </h2>
            <p class="text-xs mb-2" :class="countdown > 0 ? 'text-orange-400' : 'text-emerald-400'">
              <template v-if="countdown > 0">请仔细阅读以下守则——<span class="font-bold">{{ countdown }}</span> 秒后可勾选同意</template>
              <template v-else>阅读时间到——请勾选后提交注册</template>
            </p>
            <div class="max-h-48 overflow-y-auto bg-stone-800/60 border border-stone-700/60 rounded-lg p-3 text-xs text-stone-300 leading-relaxed" v-html="rulesHtml"></div>
            <label class="flex items-center gap-2 mt-3 text-xs" :class="countdown > 0 ? 'opacity-40 cursor-not-allowed text-stone-500' : 'cursor-pointer text-stone-300'">
              <input v-model="rulesAgreed" type="checkbox" class="accent-orange-500 w-4 h-4" :disabled="countdown > 0" />
              我已完整阅读并同意遵守服务器守则
            </label>
          </div>

          <button type="submit" class="btn-primary w-full" :disabled="loading || !rulesAgreed">{{ loading ? '注册中...' : '提交注册' }}</button>
        </form>
        <div class="mt-6 text-center">
          <router-link to="/login" class="text-sm text-orange-500 hover:text-orange-400 transition-colors">已有 {{ brand.short }} 账号？立即登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useBrand } from '@/lib/brand'
const brand = useBrand()
import { ref, inject, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/services/api'
import { renderMarkdown } from '@/lib/markdown'
import AppIcon from '@/components/AppIcon.vue'

const router = useRouter()
const notify = inject('notify') as any
const loading = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const codeCooldown = ref(0)
const logoUrl = ref('/logo.png')
const form = ref({
  minecraftName: '',
  email: '',
  password: '',
  confirmPassword: '',
  verifyCode: ''
})

// ── 服务器守则(注册强制阅读→勾选同意,随注册提交)──
const rulesCfg = ref({ doc: '', seconds: 15 })
const rulesContent = ref('')
const countdown = ref(0)
const rulesAgreed = ref(false)
const rulesHtml = computed(() => renderMarkdown(rulesContent.value))
let rulesCountdownTimer: ReturnType<typeof setInterval> | null = null

const startRulesCountdown = () => {
  if (rulesCountdownTimer) {
    clearInterval(rulesCountdownTimer)
    rulesCountdownTimer = null
  }
  countdown.value = Number(rulesCfg.value.seconds) || 15
  rulesAgreed.value = false
  rulesCountdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && rulesCountdownTimer) {
      clearInterval(rulesCountdownTimer)
      rulesCountdownTimer = null
    }
  }, 1000)
}

// 守则内容:后台指定文档优先,未配置用内置默认
const loadRulesContent = async () => {
  const doc = String(rulesCfg.value.doc || '')
  if (doc) {
    try {
      const parts = doc.split('/')
      const filename = parts.pop() as string
      const category = parts.length ? parts.join('/') : null
      const r: any = await api.readDoc(category, filename)
      if (r.success && r.data && r.data.content) {
        rulesContent.value = r.data.content
        return
      }
    } catch (e) {
      console.error('守则文档读取失败', e)
    }
  }
  rulesContent.value = DEFAULT_RULES
}

const DEFAULT_RULES = [
  '## 服务器守则',
  '',
  '1. **尊重他人**——禁止辱骂、歧视、骚扰或任何形式的恶意攻击;',
  '2. **禁止作弊**——不得使用外挂、作弊客户端或利用漏洞牟利;',
  '3. **保护环境**——禁止恶意破坏他人建筑、窃取物品或破坏地形;',
  '4. **遵守秩序**——服从管理员管理,不发布违法违规信息与广告;',
  '5. **账号安全**——妥善保管账号密码,账号行为由本人负责。',
  '',
  '违反守则将视情节轻重予以警告、临时封禁或永久封禁处理。'
].join('\n')

onMounted(async () => {
  try {
    const res = await fetch('/api/config');
    const data = await res.json();
    if (data.success) {
      if (data.data.portal?.logo) logoUrl.value = data.data.portal.logo
      if (data.data.rules) {
        rulesCfg.value = {
          doc: data.data.rules.doc || '',
          seconds: Number(data.data.rules.seconds) || 15
        }
      }
    }
  } catch (e) {}
  await loadRulesContent()
  startRulesCountdown()
})

const sendVerifyCode = async () => {
  if (!form.value.email) { notify?.error('请先输入邮箱'); return }
  try {
    await api.sendVerifyCode(form.value.email);
    notify?.success('验证码已发送');
    codeCooldown.value = 60;
    const timer = setInterval(() => {
      codeCooldown.value--;
      if (codeCooldown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (error: any) {
    notify?.error(error.message || '发送失败')
  }
}

const handleRegister = async () => {
  if (form.value.password !== form.value.confirmPassword) { notify?.error('两次输入的密码不一致'); return }
  if (form.value.password.length < 6) { notify?.error('密码长度至少6位'); return }

  if (!form.value.minecraftName) { notify?.error('请输入 Minecraft ID'); return }
  if (!/^[A-Za-z0-9_]{3,16}$/.test(form.value.minecraftName)) {
    notify?.error('游戏名不合法（3-16 位字母数字下划线）'); return
  }
  if (!rulesAgreed.value) { notify?.error('请先阅读并勾选同意服务器守则'); return }

  loading.value = true
  try {
    const response: any = await api.register({
      minecraftName: form.value.minecraftName,
      email: form.value.email,
      password: form.value.password,
      verifyCode: form.value.verifyCode,
      rulesAccepted: true
    })
    if (response.success) {
      notify?.success('注册成功，请进行 ID 验证')

      // 自动登录
      if (response.data.token) {
        api.setToken(response.data.token)
        localStorage.setItem('username', response.data.username)
      }

      router.push('/verify?platform=java')
    }
  } catch (error: any) {
    notify?.error(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>
