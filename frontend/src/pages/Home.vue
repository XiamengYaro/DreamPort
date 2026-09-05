<template>
  <div class="min-h-screen flex items-center justify-center p-4">
    <div class="w-full max-w-lg text-center">
      <div class="mb-6">
        <img :src="logoUrl" alt="XMCraft" class="w-24 h-24 rounded-3xl mx-auto mb-4 shadow-lg shadow-orange-900/20 object-cover" />
      </div>

      <h1 class="text-4xl font-bold text-white mb-2 tracking-tight">
        <span class="text-orange-400">夏日小镇</span> XMCraft
      </h1>
      <p class="text-xl text-stone-400 mb-6">白名单管理系统</p>

      <div class="card p-6 mb-6 text-left">
        <!-- Wiki 按钮 -->
        <div class="mb-5 text-center">
          <a href="https://wiki.xmcraft.cn" target="_blank" rel="noopener noreferrer"
            class="inline-flex items-center gap-2 px-5 py-3 rounded-xl bg-orange-500/15 border border-orange-500/30 text-orange-400 hover:bg-orange-500/25 transition-all duration-200 group">
            <svg class="w-5 h-5 group-hover:scale-110 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
            </svg>
            <span class="font-medium">阅读服务器公约 Wiki</span>
            <svg class="w-4 h-4 opacity-60" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
            </svg>
          </a>
        </div>

        <!-- 说明文字 -->
        <div class="space-y-3 text-sm text-stone-400 leading-relaxed">
          <p>请前往服务器文档阅读服务器公约，阅读完后进行账号注册。</p>
          <p>请认真完成注册与审核流程，自觉遵守服务器各项规则。我们致力于维护有序、文明的游玩环境，禁止恶意破坏、违规作弊等行为。期待守规的你加入我们的大家庭。</p>
          <p class="text-orange-400/80">注册完成后将进入问卷入服审核流程，请认真作答，完成作答后，将由 AI 进行自动审核。</p>
        </div>
      </div>

      <!-- 未登录：显示登录/注册按钮 -->
      <div v-if="!isLoggedIn" class="flex gap-4 justify-center">
        <router-link to="/register" class="btn-primary">注册 XMCraft 账号</router-link>
        <router-link to="/login" class="btn-secondary">已有账号？登录</router-link>
      </div>

      <!-- 已登录：显示控制台按钮 -->
      <div v-else class="flex gap-4 justify-center">
        <router-link to="/dashboard" class="btn-primary">进入控制台</router-link>
      </div>

      <router-link to="/status" class="inline-block mt-6 text-sm text-stone-500 hover:text-orange-400 transition-colors">
        查询申请状态
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

const isLoggedIn = computed(() => !!localStorage.getItem('token'))
const logoUrl = ref('/logo.png')

onMounted(async () => {
  try { const res = await fetch('/api/config'); const data = await res.json(); if (data.success && data.data.portal?.logo) logoUrl.value = data.data.portal.logo } catch (e) {}
})
</script>
