<template>
  <div>
    <!-- Hero 轮播 -->
    <HeroBanner :slides="portalConfig.carousel" :logoUrl="logoUrl" />

    <!-- 服务器介绍 -->
    <section class="py-10 px-4 sm:py-16">
      <div class="max-w-4xl mx-auto text-center">
        <h2 class="text-2xl sm:text-3xl font-bold text-white mb-6">{{ portalConfig.server_name }}</h2>
        <p class="text-lg text-stone-400 mb-8">{{ portalConfig.description }}</p>

        <!-- 服务器状态 -->
        <div class="card p-6 inline-block">
          <div class="flex items-center gap-8">
            <div class="text-center">
              <div class="text-3xl font-bold text-white">{{ serverStatus.totalOnline || serverStatus.onlinePlayers || 0 }}</div>
              <div class="text-sm text-stone-400">在线玩家</div>
            </div>
            <div class="w-px h-12 bg-stone-700"></div>
            <div class="text-center">
              <div class="text-3xl font-bold text-white">{{ serverStatus.maxPlayers || 0 }}</div>
              <div class="text-sm text-stone-400">最大人数</div>
            </div>
            <div class="w-px h-12 bg-stone-700"></div>
            <div class="text-center">
              <div class="text-3xl font-bold text-orange-400">{{ portalConfig.version }}</div>
              <div class="text-sm text-stone-400">游戏版本</div>
            </div>
          </div>
        </div>

        <!-- 快速信息 -->
        <div class="mt-8 flex flex-wrap justify-center gap-4 text-sm">
          <div class="flex items-center gap-2 text-stone-400">
            <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            <span>{{ portalConfig.server_ip }}:{{ portalConfig.server_port }}</span>
          </div>
          <div v-if="portalConfig.social?.wiki" class="flex items-center gap-2 text-stone-400">
            <svg class="w-5 h-5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
            </svg>
            <a :href="portalConfig.social.wiki" target="_blank" class="hover:text-orange-400">服务器 Wiki</a>
          </div>
        </div>
      </div>
    </section>

    <!-- 管理团队 -->
    <TeamSection v-if="portalConfig.team?.length" :team="portalConfig.team" />

    <!-- 服务器特色 -->
    <FeaturesSection v-if="portalConfig.features?.length" :features="portalConfig.features" />

    <!-- 历史时刻（垂直时间轴） -->
    <TimelineSection :timeline="portalConfig.timeline" :types="portalConfig.photo_types" />

    <!-- 底部 CTA -->
    <section class="py-10 px-4 bg-stone-800/30 sm:py-16">
      <div class="max-w-4xl mx-auto text-center">
        <h2 class="text-2xl sm:text-3xl font-bold text-white mb-4">准备好加入我们了吗？</h2>
        <p class="text-stone-400 mb-8">申请白名单，开始你在 {{ portalConfig.server_name }} 的冒险之旅</p>
        <router-link to="/whitelist" class="btn-primary text-lg px-8 py-4">
          立即申请白名单
        </router-link>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import HeroBanner from '@/components/HeroBanner.vue'
import TeamSection from '@/components/TeamSection.vue'
import FeaturesSection from '@/components/FeaturesSection.vue'
import TimelineSection from '@/components/TimelineSection.vue'

const serverStatus = ref<any>({})
const logoUrl = ref('/Logo111.png')

const portalConfig = ref({
  server_name: '夏日小镇★XMCraft',
  subtitle: 'Minecraft Java生存服务器',
  description: '一个有趣、友好的 Minecraft 生存服务器，欢迎每一位玩家加入！',
  server_ip: 'play.xmcraft.cn',
  server_port: 25565,
  version: '1.20.4',
  // 修复审计：移除假管理员/假轮播/示例内容——配置缺失时展示空区块而非示例数据
  carousel: [],
  social: { wiki: 'https://wiki.xmcraft.cn' },
  team: [],
  features: [],
  timeline: []
})

/** 应用门户配置(种子与拉取共用) */
const applyPortal = (portal: any) => {
  portalConfig.value = {
    ...portalConfig.value,
    ...portal,
    social: { ...portalConfig.value.social, ...(portal.social || {}) },
    team: Array.isArray(portal.team) ? portal.team : portalConfig.value.team,
    features: Array.isArray(portal.features) ? portal.features : portalConfig.value.features,
    carousel: Array.isArray(portal.carousel) ? portal.carousel : portalConfig.value.carousel,
    timeline: Array.isArray(portal.timeline) ? portal.timeline : portalConfig.value.timeline,
    photo_types: Array.isArray(portal.photo_types)
      ? portal.photo_types : ['announcement', 'event', 'milestone'],
  }
  if (portal.logo) {
    logoUrl.value = portal.logo
  }
}

// SWR 种子:上次会话的配置先行渲染(刷新零等待,消除内容蹦出),下方拉取最新后覆盖
try {
  const cached = localStorage.getItem('portal.config.cache')
  if (cached) {
    const data = JSON.parse(cached)
    if (data?.portal) applyPortal(data.portal)
  }
} catch { /* 缓存损坏按无缓存处理 */ }

onMounted(async () => {
  // 拉取最新配置并写回 SWR 缓存(App.vue 消费同一份)
  try {
    const configRes = await fetch('/api/config')
    const configData = await configRes.json()
    if (configData.success) {
      try { localStorage.setItem('portal.config.cache', JSON.stringify(configData.data)) } catch { /* 存储满忽略 */ }
      if (configData.data.portal) {
        applyPortal(configData.data.portal)
      }
    }
  } catch (e) {
    console.error('Failed to load config:', e)
  }

  // 加载服务器状态
  try {
    const res = await fetch('/api/server/status')
    const data = await res.json()
    if (data.success) {
      serverStatus.value = data.data
    }
  } catch (e) {
    console.error('Failed to load server status:', e)
  }
})
</script>
