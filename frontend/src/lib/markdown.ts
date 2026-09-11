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

export interface TocItem {
  /** 锚点 id(注入在 DOMPurify 消毒之后,序号制 toc-N,中文标题无需转写) */
  id: string
  text: string
  level: 2 | 3
}

/**
 * 渲染 markdown 并收集 h2/h3 目录:
 * marked 渲染 → DOMPurify 消毒 → 对结果 HTML 的 h2/h3 注入序号锚点 id(消毒后注入,不受白名单影响)。
 * 原 renderMarkdown 保持不变,其它调用方零影响。
 */
export function renderMarkdownWithToc(content: string): { html: string; toc: TocItem[] } {
  const html = renderMarkdown(content)
  const toc: TocItem[] = []
  let seq = 0
  const injected = html.replace(/<h([23])>([\s\S]*?)<\/h\1>/g, (_, levelRaw, inner) => {
    const level = Number(levelRaw) as 2 | 3
    const text = inner.replace(/<[^>]+>/g, '').trim()
    if (!text) return _ as unknown as string
    const id = `toc-${seq++}`
    toc.push({ id, text, level })
    return `<h${level} id="${id}">${inner}</h${level}>`
  })
  return { html: injected, toc }
}
