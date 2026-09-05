<template>
  <div class="min-h-screen flex items-center justify-center p-4 overflow-y-auto pb-24">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <img :src="logoUrl" alt="XMCraft" class="w-20 h-20 rounded-2xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
        <h1 class="text-3xl font-bold tracking-tight text-white">注册 XMCraft 账户</h1>
        <p class="mt-1 text-stone-400">XMCraft 用户账户系统</p>
      </div>

      <div class="card p-8">
        <form @submit.prevent="handleRegister">
          <!-- 注册模式选择 -->
          <div class="mb-4">
            <label class="block text-sm font-medium mb-2 text-stone-300">游戏平台</label>
            <div class="grid grid-cols-1 gap-2">
              <label class="flex items-center gap-3 cursor-pointer p-3 rounded-xl border transition-all"
                :class="form.mode === 'java' ? 'border-orange-500 bg-orange-500/10' : 'border-stone-600 hover:border-stone-500'">
                <input v-model="form.mode" type="radio" value="java" class="accent-orange-500" />
                <div>
                  <span class="text-stone-300 font-medium">仅 Java 版</span>
                  <p class="text-xs text-stone-500">适用于 PC 端 Minecraft</p>
                </div>
              </label>
              <label class="flex items-center gap-3 cursor-pointer p-3 rounded-xl border transition-all"
                :class="form.mode === 'bedrock' ? 'border-blue-500 bg-blue-500/10' : 'border-stone-600 hover:border-stone-500'">
                <input v-model="form.mode" type="radio" value="bedrock" class="accent-blue-500" />
                <div>
                  <span class="text-stone-300 font-medium">仅基岩版</span>
                  <p class="text-xs text-stone-500">适用于手机/主机端 Minecraft</p>
                </div>
              </label>
              <label class="flex items-center gap-3 cursor-pointer p-3 rounded-xl border transition-all"
                :class="form.mode === 'both' ? 'border-purple-500 bg-purple-500/10' : 'border-stone-600 hover:border-stone-500'">
                <input v-model="form.mode" type="radio" value="both" class="accent-purple-500" />
                <div>
                  <span class="text-stone-300 font-medium">两者都注册</span>
                  <p class="text-xs text-stone-500">同时注册 Java 版和基岩版</p>
                </div>
              </label>
            </div>
          </div>

          <!-- Minecraft ID（Java 版必填） -->
          <div v-if="form.mode === 'java' || form.mode === 'both'" class="mb-4">
            <label class="block text-sm font-medium mb-2 text-stone-300">Minecraft ID</label>
            <input v-model="form.minecraftName" type="text" class="input" placeholder="请输入你的 Minecraft 游戏名" />
            <p class="mt-1 text-xs text-stone-500">Java 版用户名，必须与你的 Minecraft 游戏名完全一致</p>
          </div>

          <!-- 基岩版 ID（基岩版必填） -->
          <div v-if="form.mode === 'bedrock' || form.mode === 'both'" class="mb-4">
            <label class="block text-sm font-medium mb-2 text-stone-300">基岩版 ID</label>
            <input v-model="form.bedrockName" type="text" class="input" placeholder="Xbox/Gamertag 用户名" />
            <p class="mt-1 text-xs text-blue-400">系统会自动添加 "{{ bedrockPrefix }}" 前缀</p>
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
          <button type="submit" class="btn-primary w-full" :disabled="loading">{{ loading ? '注册中...' : '提交注册' }}</button>
        </form>
        <div class="mt-6 text-center">
          <router-link to="/login" class="text-sm text-orange-500 hover:text-orange-400 transition-colors">已有 XMCraft 账号？立即登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/services/api'

const router = useRouter()
const notify = inject('notify') as any
const loading = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const codeCooldown = ref(0)
const bedrockEnabled = ref(false)
const bedrockPrefix = ref('.')
const logoUrl = ref('/logo.png')
const form = ref({ 
  mode: 'java',
  minecraftName: '', 
  bedrockName: '', 
  email: '', 
  password: '', 
  confirmPassword: '', 
  verifyCode: '' 
})

onMounted(async () => {
  try { 
    const res = await fetch('/api/config'); 
    const data = await res.json(); 
    if (data.success) { 
      bedrockEnabled.value = data.data.bedrockEnabled || false; 
      bedrockPrefix.value = data.data.bedrockPrefix || '.'; 
      if (data.data.portal?.logo) logoUrl.value = data.data.portal.logo 
    } 
  } catch (e) {}
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

  // 验证必填字段
  if (form.value.mode === 'java' || form.value.mode === 'both') {
    if (!form.value.minecraftName) { notify?.error('请输入 Minecraft ID'); return }
  }
  if (form.value.mode === 'bedrock' || form.value.mode === 'both') {
    if (!form.value.bedrockName) { notify?.error('请输入基岩版 ID'); return }
  }

  loading.value = true
  try {
    const response: any = await api.register({ 
      minecraftName: form.value.mode === 'java' || form.value.mode === 'both' ? form.value.minecraftName : undefined,
      bedrockName: form.value.mode === 'bedrock' || form.value.mode === 'both' ? form.value.bedrockName : undefined,
      email: form.value.email, 
      password: form.value.password,
      verifyCode: form.value.verifyCode
    })
    if (response.success) {
      notify?.success('注册成功，请进行 ID 验证')
      
      // 自动登录
      if (response.data.token) {
        api.setToken(response.data.token)
        localStorage.setItem('username', response.data.username)
      }
      
      // 根据注册模式跳转到对应的验证页面
      if (form.value.mode === 'java') {
        router.push('/verify?platform=java')
      } else if (form.value.mode === 'bedrock') {
        router.push('/verify?platform=bedrock')
      } else if (form.value.mode === 'both') {
        router.push('/verify?platform=both')
      } else {
        router.push('/verify?platform=java')
      }
    }
  } catch (error: any) { 
    notify?.error(error.message || '注册失败') 
  } finally { 
    loading.value = false 
  }
}
</script>
