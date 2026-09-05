<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()
const current = ref(localStorage.getItem('locale') || 'zh')

function toggle() {
  const next = current.value === 'zh' ? 'en' : 'zh'
  current.value = next
  locale.value = next
  localStorage.setItem('locale', next)
}
</script>

<template>
  <button @click="toggle"
    class="px-2.5 py-1.5 text-xs font-semibold rounded-xl text-white/90 hover:text-white hover:bg-white/10 transition-all border border-white/10"
    :title="current === 'zh' ? 'Switch to English' : '切换为中文'">
    {{ current === 'zh' ? 'EN' : '中' }}
  </button>
</template>
