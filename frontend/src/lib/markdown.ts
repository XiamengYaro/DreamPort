import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 配置 marked
marked.setOptions({
  gfm: true,
  breaks: true
})

export function renderMarkdown(content: string): string {
  const rawHtml = marked.parse(content) as string
  // 使用 DOMPurify 消毒 HTML，防止 XSS 攻击
  return DOMPurify.sanitize(rawHtml, {
    ALLOWED_TAGS: ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'br', 'hr', 'ul', 'ol', 'li', 
                   'blockquote', 'pre', 'code', 'a', 'img', 'strong', 'em', 'del', 'table', 
                   'thead', 'tbody', 'tr', 'th', 'td'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class']
  })
}

export function stripMarkdown(content: string): string {
  return content
    .replace(/#{1,6}\s/g, '')
    .replace(/\*\*/g, '')
    .replace(/\*/g, '')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\n/g, ' ')
    .trim()
}
