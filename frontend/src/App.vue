<template>
  <div class="min-h-screen">
    <div class="bg-animated"></div>
    <!-- 顶部导航 -->
    <TopNavigation v-if="showNav" />
    <!-- 页面内容 -->
    <main :class="showNav ? 'pb-12' : ''">
      <!-- 路由切换不加 transition:过渡机制在特定时序下会永久卡住视图
           (leave-active 不释放,后续导航全部无渲染 —— 偶发白屏根因)。
           页面间视觉衔接由各页自身的入场动画(card/cardIn)承担。 -->
      <router-view />
      <!-- 历史:曾用 <transition name="page" mode="out-in"> 包装,因偶发卡死已移除(见 git) -->
    </main>
    <!-- 底部页脚 -->
    <AppFooter :icp="icp" />

    <!-- 通知弹窗(TransitionGroup:进场上滑/出场右移,堆叠重排走 FLIP) -->
    <Teleport to="body">
      <TransitionGroup tag="div" name="toast" class="fixed top-20 right-4 md:right-6 z-50 space-y-2" style="pointer-events: none;">
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
      </TransitionGroup>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, provide, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopNavigation from './components/TopNavigation.vue'
import AppFooter from './components/AppFooter.vue'
import { api } from './services/api'
import { initBrand, applyBrandFromConfig } from './lib/brand'
import { setSeoConfig } from './lib/seo'

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

  // SWR 种子:上次会话的配置先同步渲染(刷新零闪烁),随后 /api/config 刷新覆盖
  let bgPromise: Promise<void> = Promise.resolve()
  try {
    const cached = localStorage.getItem('portal.config.cache')
    if (cached) {
      const data = JSON.parse(cached)
      if (data) {
        config.value = data
        applyBrandFromConfig(data.portal || {})
        setSeoConfig(data.seo, typeof data.serverName === 'string' ? data.serverName : undefined)
        if (data.portal?.icp) {
          icp.value = data.portal.icp
        }
        bgPromise = applyBackground({ ...(data || {}), ...((data && data.background) || {}) })
      }
    }
  } catch { /* 缓存损坏按无缓存处理 */ }

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
      } else {
        // 修复审计(前端)：以服务端返回为准刷新本地 isAdmin,避免残留的旧值绕开 UI 门禁
        if (r.data.isAdmin) localStorage.setItem('isAdmin', 'true')
        else localStorage.removeItem('isAdmin')
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
      try { localStorage.setItem('portal.config.cache', JSON.stringify(data.data)) } catch { /* 存储满忽略 */ }
      applyBrandFromConfig(data.data.portal || {})
      setSeoConfig(data.data.seo, typeof data.data.serverName === 'string' ? data.data.serverName : undefined)
      if (data.data.portal?.icp) {
        icp.value = data.data.portal.icp
      }
      // 背景探针 Promise 化:揭幕前确保最终背景图已解码完成
      bgPromise = applyBackground({ ...(data.data || {}), ...((data.data && data.data.background) || {}) })
    }
  } catch (error) {
    console.error('Failed to load config:', error)
  }

  // 揭幕时机 = 真实背景图解码完成(修复刷新闪烁:此前只等默认 bg.webp,自定义背景揭幕后才突然出现),
  // 最短展示 0.5 秒、5 秒兜底防挂死
  const startTime = Date.now()
  const minDuration = 500
  Promise.race([bgPromise, new Promise<void>(resolve => setTimeout(resolve, 5000))]).then(() => {
    const elapsed = Date.now() - startTime
    const remaining = Math.max(0, minDuration - elapsed)
    setTimeout(() => {
      const el = document.getElementById('loading-screen')
      if (el) {
        el.classList.add('fade-out')
        setTimeout(() => el.remove(), 500)
      }
    }, remaining)
  })
})

/** 应用背景配置;返回 Promise,resolve 于最终背景图加载完成(供启动揭幕等待) */
const applyBackground = (cfg: any) => {
  const root = document.documentElement
  const imagePath = cfg.image || cfg.backgroundImage
  if (cfg.opacity !== undefined || cfg.backgroundOpacity !== undefined) {
    root.style.setProperty('--bg-opacity', String(cfg.opacity ?? cfg.backgroundOpacity))
  }
  if (cfg.blur !== undefined || cfg.backgroundBlur !== undefined) {
    root.style.setProperty('--bg-blur', String(cfg.blur ?? cfg.backgroundBlur) + 'px')
  }
  return new Promise<void>((resolve) => {
    if (imagePath && imagePath !== '/bg.webp') {
      // 自定义背景图：优先尝试 WebP，失败则加载原图
      const webpPath = imagePath.replace(/\.(png|jpg|jpeg)$/i, '.webp')
      const webpImg = new Image()
      webpImg.onload = () => {
        root.style.setProperty('--bg-image', `url('${webpPath}')`)
        resolve()
      }
      webpImg.onerror = () => {
        const img = new Image()
        img.onload = () => {
          root.style.setProperty('--bg-image', `url('${imagePath}')`)
          resolve()
        }
        img.onerror = () => resolve()
        img.src = imagePath
      }
      webpImg.src = webpPath
    } else {
      // 默认背景(index.css 已引用 /bg.webp),等它解码完再揭幕
      const img = new Image()
      img.onload = () => resolve()
      img.onerror = () => resolve()
      img.src = '/bg.webp'
    }
  })
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
