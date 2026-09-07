<template>
  <div class="min-h-screen p-6 pt-24 pb-20">
    <div class="max-w-4xl mx-auto">
      <div class="text-center mb-8">
        <img :src="logoUrl" :alt="brand.short" class="w-20 h-20 rounded-2xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
        <h1 class="text-3xl font-bold tracking-tight text-white">ID 验证</h1>
        <p class="mt-1 text-stone-400">验证你的 Minecraft 账户</p>
      </div>

      <!-- 双平台验证（both） -->
      <div v-if="platform === 'both'" class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- Java 版验证 -->
        <div class="card p-6">
          <h2 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
            Java 版验证
          </h2>

          <!-- 已验证 -->
          <div v-if="javaStatus.verified && userStatus !== 'pending_verify'" class="p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-center">
            <svg class="w-12 h-12 text-emerald-400 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
            <p class="text-emerald-400 font-medium">验证成功</p>
            <p class="text-stone-400 text-sm mt-1">{{ javaStatus.name }}</p>
            <router-link to="/whitelist" class="btn-primary mt-4 inline-block">前往白名单申请</router-link>
          </div>

          <!-- 验证已设置但未完成 -->
          <div v-else-if="javaStatus.verified && userStatus === 'pending_verify'" class="p-4 bg-amber-500/10 border border-amber-500/30 rounded-xl text-center">
            <svg class="w-12 h-12 text-amber-400 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p class="text-amber-400 font-medium">验证未完成</p>
            <p class="text-stone-400 text-sm mt-1">请登录服务器完成验证</p>
            <button @click="verifyJava" class="btn-primary mt-4" :disabled="javaLoading">
              {{ javaLoading ? '验证中...' : '重新验证' }}
            </button>
          </div>

          <!-- 待验证 -->
          <div v-else class="space-y-4">
            <div class="p-4 bg-stone-800/50 rounded-xl">
              <p class="text-sm text-stone-400 mb-2">请使用 Java 版登录服务器：</p>
              <div class="flex items-center gap-2 p-2 bg-stone-900 rounded-lg">
                <code class="text-orange-400 font-mono flex-1">{{ verifyPageConfig.javaServerAddress }}</code>
                <button @click="copyToClipboard(verifyPageConfig.javaServerAddress)" class="text-stone-400 hover:text-white">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                </button>
              </div>
              <p class="text-xs text-stone-500 mt-2">端口: 25565（默认）</p>
            </div>

            <div v-if="javaMessage" class="p-3 rounded-xl text-sm" :class="javaSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400'">
              {{ javaMessage }}
            </div>

            <button @click="verifyJava" class="btn-primary w-full" :disabled="javaLoading">
              {{ javaLoading ? '验证中...' : '验证 Java 版 ID' }}
            </button>
            <p class="text-xs text-stone-500 text-center">验证有效期 3 分钟，请在登录服务器后尽快点击验证</p>
          </div>
        </div>

        <!-- 基岩版验证 -->
        <div class="card p-6">
          <h2 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
            </svg>
            基岩版验证
          </h2>

          <!-- 已验证 -->
          <div v-if="bedrockStatus.verified" class="p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-center">
            <svg class="w-12 h-12 text-emerald-400 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
            <p class="text-emerald-400 font-medium">验证成功</p>
            <p class="text-stone-400 text-sm mt-1">{{ bedrockStatus.name }}</p>
          </div>

          <!-- 待验证 -->
          <div v-else class="space-y-4">
            <div class="p-4 bg-stone-800/50 rounded-xl">
              <p class="text-sm text-stone-400 mb-2">请使用基岩版登录服务器：</p>
              <div class="flex items-center gap-2 p-2 bg-stone-900 rounded-lg">
                <code class="text-blue-400 font-mono flex-1">{{ verifyPageConfig.bedrockServerAddress }}</code>
                <button @click="copyToClipboard(verifyPageConfig.bedrockServerAddress)" class="text-stone-400 hover:text-white">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                </button>
              </div>
              <p class="text-xs text-stone-500 mt-2">端口: 19132</p>
            </div>

            <div v-if="bedrockMessage" class="p-3 rounded-xl text-sm" :class="bedrockSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400'">
              {{ bedrockMessage }}
            </div>

            <button @click="verifyBedrock" class="btn-primary w-full" :disabled="bedrockLoading">
              {{ bedrockLoading ? '验证中...' : '验证基岩版 ID' }}
            </button>
            <p class="text-xs text-stone-500 text-center">验证有效期 3 分钟，请在登录服务器后尽快点击验证</p>
          </div>
        </div>
      </div>

      <!-- 单平台验证 -->
      <div v-else class="max-w-md mx-auto">
        <!-- Java 版验证 -->
        <div v-if="platform === 'java'" class="card p-8">
          <h2 class="text-xl font-semibold text-white mb-6 text-center flex items-center justify-center gap-2">
            <svg class="w-6 h-6 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
            Java 版 ID 验证
          </h2>

          <!-- 已验证 -->
          <div v-if="javaStatus.verified" class="p-6 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-center">
            <svg class="w-16 h-16 text-emerald-400 mx-auto mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
            <h3 class="text-xl font-bold text-emerald-400 mb-2">验证成功！</h3>
            <p class="text-stone-400">{{ javaStatus.name }}</p>
            <router-link to="/whitelist" class="btn-primary mt-4 inline-block">前往白名单申请</router-link>
          </div>

          <!-- 待验证 -->
          <div v-else class="space-y-4">
            <div class="space-y-3">
              <div class="flex items-start gap-3 p-3 rounded-xl bg-stone-800/50">
                <div class="w-8 h-8 rounded-full bg-orange-500/20 flex items-center justify-center flex-shrink-0">
                  <span class="text-sm font-bold text-orange-400">1</span>
                </div>
                <div>
                  <p class="text-sm text-white">打开 Minecraft Java 版客户端</p>
                  <p class="text-xs text-stone-500">版本 {{ verifyPageConfig.javaVersion }}</p>
                </div>
              </div>
              <div class="flex items-start gap-3 p-3 rounded-xl bg-stone-800/50">
                <div class="w-8 h-8 rounded-full bg-orange-500/20 flex items-center justify-center flex-shrink-0">
                  <span class="text-sm font-bold text-orange-400">2</span>
                </div>
                <div>
                  <p class="text-sm text-white">添加服务器并登录</p>
                  <div class="flex items-center gap-2 mt-1 p-2 bg-stone-900 rounded-lg">
                    <code class="text-orange-400 font-mono flex-1">{{ verifyPageConfig.javaServerAddress }}</code>
                    <button @click="copyToClipboard(verifyPageConfig.javaServerAddress)" class="text-stone-400 hover:text-white">
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
              <div class="flex items-start gap-3 p-3 rounded-xl bg-stone-800/50">
                <div class="w-8 h-8 rounded-full bg-orange-500/20 flex items-center justify-center flex-shrink-0">
                  <span class="text-sm font-bold text-orange-400">3</span>
                </div>
                <div>
                  <p class="text-sm text-white">返回此页面点击验证</p>
                  <p class="text-xs text-stone-500">即使被踢出服务器也可以验证</p>
                </div>
              </div>
            </div>

            <div v-if="javaMessage" class="p-3 rounded-xl text-sm" :class="javaSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400'">
              {{ javaMessage }}
            </div>

            <button @click="verifyJava" class="btn-primary w-full" :disabled="javaLoading">
              {{ javaLoading ? '验证中...' : '验证 Java 版 ID' }}
            </button>
            <p class="text-xs text-stone-500 text-center">验证有效期 3 分钟</p>
          </div>
        </div>

        <!-- 基岩版验证 -->
        <div v-else-if="platform === 'bedrock'" class="card p-8">
          <h2 class="text-xl font-semibold text-white mb-6 text-center flex items-center justify-center gap-2">
            <svg class="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
            </svg>
            基岩版 ID 验证
          </h2>

          <!-- 已验证 -->
          <div v-if="bedrockStatus.verified" class="p-6 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-center">
            <svg class="w-16 h-16 text-emerald-400 mx-auto mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
            <h3 class="text-xl font-bold text-emerald-400 mb-2">验证成功！</h3>
            <p class="text-stone-400">{{ bedrockStatus.name }}</p>
            <router-link to="/whitelist" class="btn-primary mt-4 inline-block">前往白名单申请</router-link>
          </div>

          <!-- 待验证 -->
          <div v-else class="space-y-4">
            <div class="space-y-3">
              <div class="flex items-start gap-3 p-3 rounded-xl bg-stone-800/50">
                <div class="w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center flex-shrink-0">
                  <span class="text-sm font-bold text-blue-400">1</span>
                </div>
                <div>
                  <p class="text-sm text-white">打开 Minecraft 基岩版</p>
                  <p class="text-xs text-stone-500">手机/主机/Windows 10</p>
                </div>
              </div>
              <div class="flex items-start gap-3 p-3 rounded-xl bg-stone-800/50">
                <div class="w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center flex-shrink-0">
                  <span class="text-sm font-bold text-blue-400">2</span>
                </div>
                <div>
                  <p class="text-sm text-white">添加服务器并登录</p>
                  <div class="flex items-center gap-2 mt-1 p-2 bg-stone-900 rounded-lg">
                    <code class="text-blue-400 font-mono flex-1">{{ verifyPageConfig.bedrockServerAddress }}</code>
                    <button @click="copyToClipboard(verifyPageConfig.bedrockServerAddress)" class="text-stone-400 hover:text-white">
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                      </svg>
                    </button>
                  </div>
                  <p class="text-xs text-stone-500 mt-1">端口: 19132</p>
                </div>
              </div>
              <div class="flex items-start gap-3 p-3 rounded-xl bg-stone-800/50">
                <div class="w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center flex-shrink-0">
                  <span class="text-sm font-bold text-blue-400">3</span>
                </div>
                <div>
                  <p class="text-sm text-white">返回此页面点击验证</p>
                  <p class="text-xs text-stone-500">即使被踢出服务器也可以验证</p>
                </div>
              </div>
            </div>

            <div v-if="bedrockMessage" class="p-3 rounded-xl text-sm" :class="bedrockSuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400'">
              {{ bedrockMessage }}
            </div>

            <button @click="verifyBedrock" class="btn-primary w-full" :disabled="bedrockLoading">
              {{ bedrockLoading ? '验证中...' : '验证基岩版 ID' }}
            </button>
            <p class="text-xs text-stone-500 text-center">验证有效期 3 分钟</p>
          </div>
        </div>
      </div>

      <div class="text-center mt-6">
        <button @click="skipVerify" class="text-sm text-stone-500 hover:text-stone-400">跳过验证，稍后再来</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useBrand } from '@/lib/brand'
const brand = useBrand()
import { ref, onMounted, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/services/api'

const route = useRoute()
const router = useRouter()
const notify = inject('notify') as any

const logoUrl = ref('/Logo111.png')
const platform = ref('java')
const userStatus = ref('')

const verifyPageConfig = ref({
  javaServerAddress: 'mc.xmcraft.cn',
  javaVersion: '1.20.4',
  bedrockServerAddress: 'mc.xmcraft.cn'
})

const javaStatus = ref({ name: '', uuid: '', verified: false })
const javaMessage = ref('')
const javaSuccess = ref(false)
const javaLoading = ref(false)

const bedrockStatus = ref({ name: '', uuid: '', verified: false })
const bedrockMessage = ref('')
const bedrockSuccess = ref(false)
const bedrockLoading = ref(false)

onMounted(async () => {
  // 获取平台参数
  platform.value = (route.query.platform as string) || 'java'

  // 加载配置
  try {
    const res = await fetch('/api/config')
    const data = await res.json()
    if (data.success) {
      if (data.data.portal?.logo) {
        logoUrl.value = data.data.portal.logo
      }
      if (data.data.verifyPage) {
        verifyPageConfig.value = { ...verifyPageConfig.value, ...data.data.verifyPage }
      }
    }
  } catch (e) {}

  // 加载验证状态
  await Promise.all([loadJavaStatus(), loadBedrockStatus()])
})

const loadJavaStatus = async () => {
  try {
    const r: any = await api.getMinecraftStatus()
    if (r.success) {
      javaStatus.value = {
        name: r.data.minecraftName || '',
        uuid: r.data.minecraftUuid || '',
        verified: r.data.verified || false
      }
      // 同时更新用户状态
      if (r.data.status) {
        userStatus.value = r.data.status
      }
    }
  } catch (e) {}
}

const loadBedrockStatus = async () => {
  try {
    const r: any = await api.getBedrockStatus()
    if (r.success) {
      bedrockStatus.value = {
        name: r.data.bedrockName || '',
        uuid: r.data.bedrockUuid || '',
        verified: r.data.bedrockVerified || false
      }
      // 同时更新用户状态
      if (r.data.status) {
        userStatus.value = r.data.status
      }
    }
  } catch (e) {}
}

const verifyJava = async () => {
  javaLoading.value = true
  javaMessage.value = ''
  try {
    const r: any = await api.verifyMinecraft()
    if (r.success && r.data?.verified) {
      javaMessage.value = '验证成功！'
      javaSuccess.value = true
      await loadJavaStatus()
    } else {
      javaMessage.value = r.data?.message || r.message || '未找到登录记录，请先登录服务器'
      javaSuccess.value = false
    }
  } catch (e: any) {
    javaMessage.value = e.message || '验证失败'
    javaSuccess.value = false
  } finally {
    javaLoading.value = false
  }
}

const verifyBedrock = async () => {
  bedrockLoading.value = true
  bedrockMessage.value = ''
  try {
    const r: any = await api.verifyMyBedrock()
    if (r.success) {
      if (r.data.verified) {
        bedrockMessage.value = '验证成功！'
        bedrockSuccess.value = true
        await loadBedrockStatus()
      } else {
        bedrockMessage.value = r.data.message || '未找到登录记录，请先登录服务器'
        bedrockSuccess.value = false
      }
    }
  } catch (e: any) {
    bedrockMessage.value = e.message || '验证失败'
    bedrockSuccess.value = false
  } finally {
    bedrockLoading.value = false
  }
}

const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text)
  notify?.success('已复制到剪贴板')
}

const skipVerify = () => {
  notify?.info('后续可前往控制台进行 ID 验证')
  router.push('/whitelist')
}
</script>
