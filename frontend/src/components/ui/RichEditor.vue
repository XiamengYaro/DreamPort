<template>
  <div class="rich-editor rounded-xl overflow-hidden ring-1 bg-stone-900/40 transition-shadow"
    :class="focused ? 'ring-orange-400/50' : 'ring-white/10'">
    <!-- 工具栏(类飞书:分级标题/粗斜删/颜色/列表/引用/代码/链接/图片/清除) -->
    <div v-if="editor" class="flex flex-wrap items-center gap-0.5 px-2 py-1.5 border-b border-white/10 bg-stone-900/60">
      <button type="button" @click="editor.chain().focus().toggleHeading({ level: 1 }).run()"
        :class="toolbarBtn(editor.isActive('heading', { level: 1 }))" title="一级标题">H1</button>
      <button type="button" @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"
        :class="toolbarBtn(editor.isActive('heading', { level: 2 }))" title="二级标题">H2</button>
      <button type="button" @click="editor.chain().focus().toggleHeading({ level: 3 }).run()"
        :class="toolbarBtn(editor.isActive('heading', { level: 3 }))" title="三级标题">H3</button>
      <span class="w-px h-5 bg-white/10 mx-1"></span>
      <button type="button" @click="editor.chain().focus().toggleBold().run()"
        :class="toolbarBtn(editor.isActive('bold'))" title="粗体"><span class="font-bold">B</span></button>
      <button type="button" @click="editor.chain().focus().toggleItalic().run()"
        :class="toolbarBtn(editor.isActive('italic'))" title="斜体"><span class="italic font-serif">I</span></button>
      <button type="button" @click="editor.chain().focus().toggleStrike().run()"
        :class="toolbarBtn(editor.isActive('strike'))" title="删除线"><span class="line-through">S</span></button>
      <button type="button" @click="editor.chain().focus().unsetColor().run()"
        :class="toolbarBtn(false)" title="恢复默认颜色"><span class="text-xs">A×</span></button>
      <input type="color" class="w-6 h-6 rounded cursor-pointer bg-transparent border-0 p-0"
        title="文字颜色" @input="editor.chain().focus().setColor(($event.target as HTMLInputElement).value).run()" />
      <span class="w-px h-5 bg-white/10 mx-1"></span>
      <button type="button" @click="editor.chain().focus().toggleBulletList().run()"
        :class="toolbarBtn(editor.isActive('bulletList'))" title="无序列表">•≡</button>
      <button type="button" @click="editor.chain().focus().toggleOrderedList().run()"
        :class="toolbarBtn(editor.isActive('orderedList'))" title="有序列表">1≡</button>
      <button type="button" @click="editor.chain().focus().toggleBlockquote().run()"
        :class="toolbarBtn(editor.isActive('blockquote'))" title="引用">❝</button>
      <button type="button" @click="editor.chain().focus().toggleCodeBlock().run()"
        :class="toolbarBtn(editor.isActive('codeBlock'))" title="代码块">{ }</button>
      <span class="w-px h-5 bg-white/10 mx-1"></span>
      <button type="button" @click="setLink" :class="toolbarBtn(editor.isActive('link'))" title="链接">🔗</button>
      <button type="button" @click="editor.chain().focus().unsetLink().run()"
        :class="toolbarBtn(false)" title="移除链接">🔗×</button>
      <button type="button" @click="pickImage" :class="toolbarBtn(false)" title="插入图片(≤5MB)">🖼</button>
      <button type="button" @click="editor.chain().focus().unsetAllMarks().clearNodes().run()"
        :class="toolbarBtn(false)" title="清除格式">⌫</button>
      <input ref="imageInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp" class="hidden" @change="uploadImage" />
    </div>

    <editor-content :editor="editor" class="px-3 py-2 text-sm" :style="{ minHeight: minHeight + 'px' }" @keydown="onKeydown" />
  </div>
</template>

<script setup lang="ts">
/**
 * 富文本编辑器(Tiptap + tiptap-markdown):
 * - v-model 进出均为 **markdown 字符串**(存储层保持 markdown,存量文档/帖子零迁移)
 * - 工具栏:分级标题/粗斜删/文字颜色/列表/引用/代码块/链接/图片上传(走 /api/upload/image)/清除格式
 * -论坛与后台文档管理共用。
 */
import { ref, watch, onBeforeUnmount } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import { Image } from '@tiptap/extension-image'
import { Link } from '@tiptap/extension-link'
import { Markdown } from 'tiptap-markdown'
import api from '@/services/api'

const props = withDefaults(defineProps<{
  modelValue: string
  minHeight?: number
  placeholder?: string
}>(), { minHeight: 140, placeholder: '' })

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const focused = ref(false)
const imageInput = ref<HTMLInputElement | null>(null)
let uploading = false

const editor = useEditor({
  content: props.modelValue || '',
  extensions: [
    StarterKit.configure({ heading: { levels: [1, 2, 3] } }),
    TextStyle,
    Color,
    Image.configure({ inline: false }),
    Link.configure({ openOnClick: false, autolink: true }),
    Markdown.configure({ html: false, linkify: true, breaks: true })
  ],
  editorProps: {
    attributes: {
      class: 'prose-sm outline-none min-h-full text-stone-200',
      'data-placeholder': props.placeholder
    },
    handleKeyDown: () => {
      focused.value = true
      return false
    }
  },
  onUpdate: () => {
    if (!uploading) emit('update:modelValue', editor.value?.storage.markdown.getMarkdown() ?? '')
  },
  onFocus: () => { focused.value = true },
  onBlur: () => { focused.value = false }
})

// 外部值变更 → 同步进编辑器(避免光标跳动的循环更新)
watch(() => props.modelValue, (v) => {
  const current = editor.value?.storage.markdown.getMarkdown() ?? ''
  if (editor.value && v !== current) {
    editor.value.commands.setContent(v || '', false)
  }
})

onBeforeUnmount(() => editor.value?.destroy())

function toolbarBtn(active: boolean) {
  return [
    'px-2 py-1 rounded-md text-xs font-medium transition-colors',
    active ? 'bg-orange-500/25 text-orange-300' : 'text-stone-400 hover:text-white hover:bg-white/10'
  ]
}

function setLink() {
  const url = window.prompt('链接地址(https://…)')
  if (!url) return
  if (!/^https?:\/\//i.test(url)) {
    alert('仅支持 http/https 链接')
    return
  }
  editor.value?.chain().focus().setLink({ href: url }).run()
}

function pickImage() {
  imageInput.value?.click()
}

async function uploadImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || uploading) return
  uploading = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await fetch('/api/upload/image', {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
      body: fd
    })
    const data = await res.json()
    if (data.success && data.data?.url) {
      editor.value?.chain().focus().setImage({ src: data.data.url }).run()
    } else {
      alert(data.message || '上传失败')
    }
  } catch (err: any) {
    alert(err.message || '上传失败')
  }
  uploading = false
}

function onKeydown() {
  focused.value = true
}
</script>

<style scoped>
.rich-editor :deep(.tiptap) {
  outline: none;
  min-height: inherit;
}

.rich-editor :deep(.tiptap) :deep(p) {
  margin: 0.35rem 0;
  line-height: 1.7;
}

.rich-editor :deep(.tiptap) :deep(ul),
.rich-editor :deep(.tiptap) :deep(ol) {
  padding-left: 1.4rem;
  margin: 0.35rem 0;
}

.rich-editor :deep(.tiptap) :deep(blockquote) {
  border-left: 3px solid rgba(249, 115, 22, 0.5);
  padding-left: 0.75rem;
  color: rgb(168 162 158);
  font-style: italic;
  margin: 0.5rem 0;
}

.rich-editor :deep(.tiptap) :deep(pre) {
  background: rgba(28, 25, 23, 0.9);
  border-radius: 0.5rem;
  padding: 0.75rem;
  overflow-x: auto;
  font-size: 0.8rem;
}

.rich-editor :deep(.tiptap) :deep(code) {
  background: rgba(249, 115, 22, 0.12);
  color: #fb923c;
  padding: 0.1rem 0.3rem;
  border-radius: 0.25rem;
  font-size: 0.8rem;
}

.rich-editor :deep(.tiptap) :deep(pre code) {
  background: transparent;
  padding: 0;
}

.rich-editor :deep(.tiptap) :deep(img) {
  max-width: 100%;
  border-radius: 0.5rem;
}

.rich-editor :deep(.tiptap) :deep(a) {
  color: #fb923c;
  text-decoration: underline;
}

.rich-editor :deep(.tiptap) :deep(p.is-editor-empty:first-child)::before {
  content: attr(data-placeholder);
  color: rgb(120 113 108);
  float: left;
  height: 0;
  pointer-events: none;
}
</style>
