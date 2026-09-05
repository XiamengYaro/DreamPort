<template>
  <header class="fixed top-0 left-0 right-0 z-50 transition-all duration-300"
    :class="scrolled || mobileMenuOpen ? 'scrolled' : 'transparent'">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex items-center justify-between h-16">
        <!-- Logo -->
        <router-link to="/" class="flex items-center gap-3">
          <img :src="logoUrl" alt="Logo" class="w-9 h-9 rounded-xl object-cover shadow-lg" />
          <span class="font-bold text-lg text-white tracking-tight hidden sm:block drop-shadow-lg">{{ serverName }}</span>
        </router-link>

        <!-- 中间导航 -->
        <div class="hidden md:flex items-center gap-1">
          <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">首页</router-link>
          <router-link to="/docs" class="nav-item" :class="{ active: $route.path.startsWith('/docs') }">文档</router-link>
          <router-link to="/whitelist" class="nav-item" :class="{ active: $route.path === '/whitelist' }">白名单</router-link>
          <router-link to="/leaderboard" class="nav-item" :class="{ active: $route.path === '/leaderboard' }">排行榜</router-link>
          <router-link to="/map" class="nav-item" :class="{ active: $route.path === '/map' }">地图</router-link>
          
          <!-- 更多下拉菜单（样式与其余导航项一致） -->
          <div class="relative group">
            <span class="nav-item cursor-pointer flex items-center gap-1"
              :class="{ active: ['/village', '/players', '/machines'].includes($route.path) }">
              更多
              <svg class="w-3 h-3 transition-transform duration-200 group-hover:rotate-180" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" /></svg>
            </span>
            <div class="absolute top-full left-0 pt-2 w-40 bg-stone-800 border border-stone-700 rounded-xl shadow-lg opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 z-50 overflow-hidden">
              <router-link to="/village" class="block px-4 py-3 text-sm text-stone-300 hover:text-white hover:bg-white/10 transition-all">村民族谱</router-link>
              <router-link to="/players" class="block px-4 py-3 text-sm text-stone-300 hover:text-white hover:bg-white/10 transition-all">玩家</router-link>
              <router-link to="/machines" class="block px-4 py-3 text-sm text-stone-300 hover:text-white hover:bg-white/10 transition-all">公共机器</router-link>
            </div>
          </div>
        </div>

        <!-- 右侧用户信息 -->
        <div class="flex items-center gap-4">
          <LanguageSwitcher />

          <button @click="mobileMenuOpen = !mobileMenuOpen" class="md:hidden p-2 text-white drop-shadow">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path v-if="!mobileMenuOpen" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
              <path v-else stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>

          <!-- 用户信息 -->
          <template v-if="isLoggedIn">
            <div class="flex items-center gap-2">
              <div class="w-8 h-8 rounded-full bg-gradient-to-br from-orange-400 to-amber-400 flex items-center justify-center text-white text-sm font-bold shadow-lg">
                {{ username.charAt(0).toUpperCase() }}
              </div>
              <div class="hidden sm:block">
                <div class="text-white text-sm font-medium drop-shadow">{{ username }}</div>
                <div class="text-white/60 text-xs drop-shadow">{{ isAdmin ? '管理员' : '玩家' }}</div>
              </div>
            </div>
            <router-link to="/dashboard" class="btn-ghost text-sm hidden sm:block">控制台</router-link>
            <router-link v-if="isAdmin" to="/admin" class="btn-ghost text-sm hidden sm:block">管理</router-link>
            <button @click="logout" class="btn-ghost text-sm">退出</button>
          </template>
          <template v-else>
            <router-link to="/login" class="btn-ghost text-sm">登录</router-link>
          </template>
        </div>
      </div>
    </div>

        <!-- 移动端菜单 -->
    <div v-if="mobileMenuOpen" class="md:hidden border-t border-white/10 menu-glass">
      <nav class="px-4 py-3 space-y-1">
        <router-link to="/" class="mobile-nav-item" @click="mobileMenuOpen = false">首页</router-link>
        <router-link to="/docs" class="mobile-nav-item" @click="mobileMenuOpen = false">文档</router-link>
        <router-link to="/whitelist" class="mobile-nav-item" @click="mobileMenuOpen = false">白名单申请</router-link>
        <router-link to="/leaderboard" class="mobile-nav-item" @click="mobileMenuOpen = false">排行榜</router-link>
        <router-link to="/map" class="mobile-nav-item" @click="mobileMenuOpen = false">世界地图</router-link>
        
        <!-- 更多分组 -->
        <button @click="moreGroupOpen = !moreGroupOpen" class="w-full flex items-center justify-between px-4 py-3 text-stone-300 hover:text-white hover:bg-white/10 rounded-xl transition-all">
          <span>更多 ▸</span>
          <svg :class="{'rotate-90': moreGroupOpen}" class="w-4 h-4 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
        </button>
        <div v-if="moreGroupOpen" class="pl-6 space-y-1">
          <router-link to="/village" class="block mobile-nav-item" @click="mobileMenuOpen = false">村民族谱</router-link>
          <router-link to="/players" class="block mobile-nav-item" @click="mobileMenuOpen = false">玩家</router-link>
          <router-link to="/machines" class="block mobile-nav-item" @click="mobileMenuOpen = false">公共机器</router-link>
        </div>
        
        <div class="border-t border-white/10 my-2"></div>
        <template v-if="isLoggedIn">
          <router-link to="/dashboard" class="mobile-nav-item" @click="mobileMenuOpen = false">控制台</router-link>
          <router-link v-if="isAdmin" to="/admin" class="mobile-nav-item" @click="mobileMenuOpen = false">管理后台</router-link>
          <button @click="logout" class="mobile-nav-item w-full text-left">退出登录</button>
        </template>
        <!-- 登录入口 -->
        <template v-else>
          <router-link to="/login" class="mobile-nav-item" @click="mobileMenuOpen = false">登录</router-link>
        </template>
      </nav>
    </div>
  </header>
</template>

<script setup lang="ts">
import LanguageSwitcher from './LanguageSwitcher.vue'
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/services/api'

const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)
const scrolled = ref(false)
const serverName = ref('夏日小镇')
const logoUrl = ref('/Logo111.png')
const moreGroupOpen = ref(false)

watch(() => route.path, () => {
  mobileMenuOpen.value = false
})

const handleScroll = () => {
  scrolled.value = window.scrollY > 50
}

onMounted(async () => {
  window.addEventListener('scroll', handleScroll)

  try {
    const res = await fetch('/api/config')
    const data = await res.json()
    if (data.success && data.data.portal) {
      serverName.value = data.data.portal.server_name || '夏日小镇'
      logoUrl.value = data.data.portal.logo || '/Logo111.png'
    }
  } catch (e) {}
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

const isLoggedIn = computed(() => !!localStorage.getItem('token'))
const isAdmin = computed(() => localStorage.getItem('isAdmin') === 'true')
const username = computed(() => localStorage.getItem('username') || '')

const logout = () => {
  if (confirm('确定要退出登录吗？')) {
    api.setToken(null)
    localStorage.removeItem('username')
    localStorage.removeItem('isAdmin')
    mobileMenuOpen.value = false
    router.push('/login')
  }
}
</script>

<style scoped>
.transparent {
  background: transparent;
  border-bottom: 1px solid transparent;
}

.scrolled {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.15) 0%, rgba(255, 255, 255, 0.05) 100%);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 
    0 4px 30px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.liquid-glass-subtle {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.1) 0%, rgba(255, 255, 255, 0.03) 100%);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
}

.mobile-menu-glass {
  background: linear-gradient(135deg, rgba(40, 40, 40, 0.85) 0%, rgba(30, 30, 30, 0.9) 100%);
  backdrop-filter: saturate(180%) blur(24px);
  -webkit-backdrop-filter: saturate(180%) blur(24px);
  border-color: rgba(255, 255, 255, 0.1);
}

.menu-glass {
  background: rgba(28, 25, 23, 0.88);
  backdrop-filter: saturate(180%) blur(24px);
  -webkit-backdrop-filter: saturate(180%) blur(24px);
}

.nav-item {
  @apply px-3 py-2 text-sm font-medium rounded-xl transition-all duration-200 whitespace-nowrap;
  color: rgba(255, 255, 255, 0.7);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.nav-item:hover {
  color: white;
  background: rgba(255, 255, 255, 0.1);
}

.nav-item.active {
  color: #fb923c;
  background: rgba(249, 115, 22, 0.15);
}

.nav-item-dropdown {
  position: relative;
}

.nav-item-dropdown:hover {
  color: white;
  background: rgba(255, 255, 255, 0.1);
}

.mobile-nav-item {
  @apply block px-4 py-3 rounded-xl transition-all duration-200;
  color: rgba(255, 255, 255, 0.8);
}

.mobile-nav-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.mobile-nav-item.router-link-exact-active {
  color: #fb923c;
  background: rgba(249, 115, 22, 0.15);
}
</style>
