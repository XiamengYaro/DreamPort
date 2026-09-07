<template>
  <div class="min-h-screen">
    <div class="bg-animated"></div>
    <!-- 顶部导航 -->
    <TopNavigation v-if="showNav" />
    <!-- 页面内容 -->
    <main :class="showNav ? 'pb-12' : ''">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <!-- 底部页脚 -->
    <AppFooter :icp="icp" />

    <!-- 通知弹窗 -->
    <Teleport to="body">
      <div class="fixed top-20 right-4 md:right-6 z-50 space-y-2" style="pointer-events: none;">
        <div
          v-for="n in notifications"
          :key="n.id"
          class="px-4 py-3 rounded-2xl min-w-[280px] max-w-[400px] shadow-lg border animate-slide-down flex items-start gap-3"
          :class="{
            'bg-emerald-900/80 border-emerald-700 text-emerald-200': n.type === 'success',
            'bg-rose-900/80 border-rose-700 text-rose-200': n.type === 'error',
            'bg-orange-900/80 border-orange-700 text-orange-200': n.type === 'info'
          }"
          style="pointer-events: auto;"
        >
          <svg v-if="n.type === 'success'" class="w-5 h-5 text-emerald-400 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          <svg v-else-if="n.type === 'error'" class="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
          <svg v-else class="w-5 h-5 text-orange-400 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span class="text-sm font-medium flex-1">{{ n.message }}</span>
          <button @click="removeNotification(n.id)" class="text-white/50 hover:text-white flex-shrink-0">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, provide, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopNavigation from './components/TopNavigation.vue'
import AppFooter from './components/AppFooter.vue'
import { api } from './services/api'

const route = useRoute()
const router = useRouter()
const config = ref<any>(null)
const icp = ref('')

// 控制登录/注册页面不显示顶部导航
const showNav = computed(() => {
  const hiddenRoutes = ['/login', '/register']
  return !hiddenRoutes.includes(route.path)
})

provide('config', config)

onMounted(async () => {
  // 监听路由守卫的通知事件
  window.addEventListener('app-notify', ((e: CustomEvent) => {
    if (e.detail?.type && e.detail?.message) {
      addNotification(e.detail.type, e.detail.message)
    }
  }) as EventListener)

  // 验证 token 有效性（插件重启后 token 会失效）
  const token = localStorage.getItem('token')
  if (token) {
    try {
      const r: any = await api.validateToken()
      if (!r.success || !r.data?.valid) {
        api.setToken(null)
        localStorage.removeItem('isAdmin')
        localStorage.removeItem('username')
        router.replace('/login')
      }
    } catch {
      api.setToken(null)
      localStorage.removeItem('isAdmin')
      localStorage.removeItem('username')
      router.replace('/login')
    }
  }

  try {
    const response = await fetch('/api/config')
    const data = await response.json()
    if (data.success) {
      config.value = data.data
      applyBackground({ ...(data.data || {}), ...((data.data && data.data.background) || {}) })
      if (data.data.portal?.icp) {
        icp.value = data.data.portal.icp
      }
    }
  } catch (error) {
    console.error('Failed to load config:', error)
  }

  // 等待背景图加载完成后移除加载画面（最短 0.5 秒）
  const startTime = Date.now()
  const minDuration = 500
  let bgLoaded = false

  const hideLoading = () => {
    if (!bgLoaded) return
    const elapsed = Date.now() - startTime
    const remaining = Math.max(0, minDuration - elapsed)
    setTimeout(() => {
      const el = document.getElementById('loading-screen')
      if (el) {
        el.classList.add('fade-out')
        setTimeout(() => el.remove(), 500)
      }
    }, remaining)
  }

  const bgImg = new Image()
  bgImg.onload = () => { bgLoaded = true; hideLoading() }
  bgImg.onerror = () => { bgLoaded = true; hideLoading() }
  bgImg.src = '/bg.webp'
  setTimeout(() => { bgLoaded = true; hideLoading() }, 5000)
})

const applyBackground = (cfg: any) => {
  const root = document.documentElement
  const imagePath = cfg.image || cfg.backgroundImage
  if (cfg.opacity !== undefined || cfg.backgroundOpacity !== undefined) {
    root.style.setProperty('--bg-opacity', String(cfg.opacity ?? cfg.backgroundOpacity))
  }
  if (cfg.blur !== undefined || cfg.backgroundBlur !== undefined) {
    root.style.setProperty('--bg-blur', (cfg.blur ?? cfg.backgroundBlur) + 'px')
  }
  if (imagePath && imagePath !== '/bg.webp') {
    // 自定义背景图：优先尝试 WebP，失败则加载原图
    const webpPath = imagePath.replace(/\.(png|jpg|jpeg)$/i, '.webp')
    const webpImg = new Image()
    webpImg.onload = () => {
      root.style.setProperty('--bg-image', `url('${webpPath}')`)
    }
    webpImg.onerror = () => {
      const img = new Image()
      img.onload = () => {
        root.style.setProperty('--bg-image', `url('${imagePath}')`)
      }
      img.src = imagePath
    }
    webpImg.src = webpPath
  }
}

const refreshBackground = async () => {
  try {
    const response = await fetch('/api/config')
    const data = await response.json()
    if (data.success) {
      config.value = data.data
      applyBackground({ ...(data.data || {}), ...((data.data && data.data.background) || {}) })
    }
  } catch (error) {
    console.error('Failed to refresh config:', error)
  }
}

provide('refreshBackground', refreshBackground)

const notifications = ref<Array<{ id: number; type: string; message: string }>>([])
let nextId = 0

const addNotification = (type: string, message: string) => {
  const id = nextId++
  notifications.value.push({ id, type, message })
  setTimeout(() => {
    removeNotification(id)
  }, 5000)
}

const removeNotification = (id: number) => {
  notifications.value = notifications.value.filter(n => n.id !== id)
}

const notify = {
  success: (message: string) => addNotification('success', message),
  error: (message: string) => addNotification('error', message),
  info: (message: string) => addNotification('info', message)
}

provide('notify', notify)
</script>
