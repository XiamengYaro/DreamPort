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
 * - 其余走后端本地渲染端点 /api/avatar/{name}(双层皮肤,正版 Mojang/非正版皮肤站,两级缓存)
 *   —— 未绑定 MC 的账号返回程序绘制的默认脸(Steve/Alex 按名字 hash),验证 ID 后即变真实头像
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

const head = computed(() => props.name || props.uuid || 'Steve')
const src = computed(() => {
  if (broken.value) return ''
  if (props.avatarUrl) return props.avatarUrl
  return `/api/avatar/${encodeURIComponent(head.value)}?size=${sizePx.value}`
})

const sizePx = computed(() => {
  const m = /w-(\d+)/.exec(props.sizeClass)
  return m ? Number(m[1]) * 4 : 64
})

const fallbackText = computed(() => (props.name || '?').charAt(0).toUpperCase())
const alt = computed(() => props.alt || props.name)

const onError = () => { broken.value = true }
</script>
