<template>
  <div>
    <!-- Hero 轮播 -->
    <HeroBanner :slides="portalConfig.carousel" :logoUrl="logoUrl" />

    <!-- 服务器介绍 -->
    <section class="py-16 px-4">
      <div class="max-w-4xl mx-auto text-center">
        <h2 class="text-3xl font-bold text-white mb-6">{{ portalConfig.server_name }}</h2>
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
    <TimelineSection :timeline="portalConfig.timeline" />

    <!-- 底部 CTA -->
    <section class="py-16 px-4 bg-stone-800/30">
      <div class="max-w-4xl mx-auto text-center">
        <h2 class="text-3xl font-bold text-white mb-4">准备好加入我们了吗？</h2>
        <p class="text-stone-400 mb-8">申请白名单，开始你在夏日小镇的冒险之旅</p>
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
  server_name: '夏日小镇',
  subtitle: 'XMCraft Minecraft 服务器',
  description: '一个有趣、友好的 Minecraft 生存服务器，欢迎每一位玩家加入！',
  server_ip: 'play.xmcraft.cn',
  server_port: 25565,
  version: '1.20.4',
  carousel: [
    { image: '/images/banner1.jpg', title: '欢迎来到夏日小镇', subtitle: '开始你的冒险之旅' },
    { image: '/images/banner2.jpg', title: '社区活动', subtitle: '参与精彩活动赢取奖励' },
    { image: '/images/banner3.jpg', title: '建筑展示', subtitle: '展示你的创意建筑' }
  ],
  social: { wiki: 'https://wiki.xmcraft.cn' },
  team: [
    { name: '管理员A', role: '服主', avatar: '' },
    { name: '管理员B', role: '管理员', avatar: '' },
    { name: '管理员C', role: '管理员', avatar: '' },
    { name: '管理员D', role: '技术支持', avatar: '' }
  ],
  features: [
    { icon: '<svg class="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" /><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>', title: '多样玩法', description: '生存、创造、红石、PVP 等多种游戏模式' },
    { icon: '<svg class="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" /></svg>', title: '友好社区', description: '活跃的玩家社区，定期举办精彩活动' },
    { icon: '<svg class="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" /></svg>', title: '安全稳定', description: '专业的反作弊系统，24小时稳定运行' }
  ],
  timeline: [
    { date: '2024-01-01', title: '服务器创立', description: '夏日小镇正式开服，欢迎各位玩家加入！', image: '' },
    { date: '2024-06-15', title: '第一届建筑大赛', description: '成功举办首届建筑大赛，玩家们展示了惊人的创造力', image: '' }
  ]
})

onMounted(async () => {
  // 直接加载配置
  try {
    const configRes = await fetch('/api/config')
    const configData = await configRes.json()
    if (configData.success) {
      if (configData.data.portal) {
        portalConfig.value = {
          ...portalConfig.value,
          ...configData.data.portal,
          social: { ...portalConfig.value.social, ...(configData.data.portal.social || {}) },
          team: Array.isArray(configData.data.portal.team) ? configData.data.portal.team : portalConfig.value.team,
          features: Array.isArray(configData.data.portal.features) ? configData.data.portal.features : portalConfig.value.features,
          carousel: Array.isArray(configData.data.portal.carousel) ? configData.data.portal.carousel : portalConfig.value.carousel,
          timeline: Array.isArray(configData.data.portal.timeline) ? configData.data.portal.timeline : portalConfig.value.timeline
        }
        // 从 portal.logo 读取 Logo，不使用 logoUrl
        if (configData.data.portal.logo) {
          logoUrl.value = configData.data.portal.logo
        }
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
