<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-4xl mx-auto">
      <!-- 标题 + Tab -->
      <div class="card p-6 mb-6">
        <h1 class="text-2xl font-bold text-white">社区</h1>
        <p class="text-stone-400">论坛 · 投票 · 反馈工单</p>
        <div class="flex gap-2 mt-4">
          <button v-for="t in tabs" :key="t.key" @click="active = t.key"
            class="px-4 py-2 rounded-xl text-sm font-medium transition-colors inline-flex items-center gap-1.5"
            :class="active === t.key
              ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20'
              : 'text-stone-400 hover:text-white hover:bg-white/5 border border-transparent'">
            <AppIcon :name="t.icon" class="w-4 h-4" />{{ t.label }}
          </button>
        </div>
      </div>

      <ForumPanel v-if="active === 'forum'" />
      <PollsPanel v-else-if="active === 'polls'" />
      <FeedbackPanel v-else />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import ForumPanel from '@/components/community/ForumPanel.vue'
import PollsPanel from '@/components/community/PollsPanel.vue'
import FeedbackPanel from '@/components/community/FeedbackPanel.vue'

const tabs = [
  { key: 'forum', icon: 'chat-bubble', label: '论坛' },
  { key: 'polls', icon: 'dot-circle', label: '投票' },
  { key: 'feedback', icon: 'envelope', label: '反馈工单' }
]
const active = ref('forum')
</script>
