<template>
  <img v-if="src" :src="src" :alt="alt" :class="sizeClass" class="object-cover shrink-0" loading="lazy"
    decoding="async" @error="onError" />
  <div v-else :class="[sizeClass, 'bg-gradient-to-br from-orange-400 to-amber-400 flex items-center justify-center text-white font-bold shrink-0']">
    {{ fallbackText }}
  </div>
</template>

<script setup lang="ts">
/**
 * 统一头像组件:
 * - avatarUrl(用户上传)优先
 * - 其余按 Minecraft 头像渲染(crafthead):uuid 优先,其次 minecraftName/name
 *   —— 未绑定 MC 的账号 crafthead 返回默认 Steve 皮肤(刚注册 = 史蒂夫头像),
 *      验证 ID 后 name/uuid 更新即自动变为真实头像
 * - 图片加载失败回退首字母圆徽
 */
import { computed, ref } from 'vue'

const props = withDefaults(defineProps<{
  name: string
  uuid?: string | null
  avatarUrl?: string | null
  sizeClass?: string
  alt?: string
}>(), { sizeClass: 'w-9 h-9', alt: '' })

const broken = ref(false)

const head = computed(() => props.uuid || props.name || 'Steve')
const src = computed(() => {
  if (broken.value) return ''
  if (props.avatarUrl) return props.avatarUrl
  return `https://crafthead.net/avatar/${encodeURIComponent(head.value)}/${sizePx.value}`
})

const sizePx = computed(() => {
  const m = /w-(\d+)/.exec(props.sizeClass)
  return m ? Number(m[1]) * 4 : 64
})

const fallbackText = computed(() => (props.name || '?').charAt(0).toUpperCase())
const alt = computed(() => props.alt || props.name)

const onError = () => { broken.value = true }
</script>
