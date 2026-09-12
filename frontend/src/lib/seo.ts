// 站点 SEO 状态与页面标题/元信息应用(路由切换与详情页共用)。
// 配置来源:/api/config 的 seo 块(后台「门户管理 → SEO 搜索优化」可自定义),
// 服务端 SeoMetaInjectionFilter 已为直出 HTML 注入同源 meta,此处负责客户端导航后的动态更新。

interface SeoState {
  template: string
  site: string
  description: string
  keywords: string
  ogImage: string
}

const state: SeoState = {
  template: '{page} - {site}',
  site: '夏日小镇★XMCraft',
  description: '',
  keywords: '',
  ogImage: ''
}

/** /api/config 到达后调用,刷新标题模板/描述/关键词/OG 图与站名 */
export function setSeoConfig(cfg: any = {}, siteName?: string) {
  if (cfg && typeof cfg === 'object') {
    state.template = String(cfg.titleTemplate || '{page} - {site}')
    state.description = String(cfg.description || '')
    state.keywords = String(cfg.keywords || '')
    if (cfg.ogImage) state.ogImage = String(cfg.ogImage)
  }
  if (siteName) state.site = siteName
}

function setMeta(attr: 'name' | 'property', key: string, content: string) {
  if (!content) return
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute(attr, key)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

/** 路径 → 页面名(与后端 SeoSupport.PAGE_TITLES 同源;最长前缀优先) */
const PAGE_TITLES: Array<[string, string]> = [
  ['/announcements', '公告中心'],
  ['/leaderboard', '排行榜'],
  ['/players', '玩家目录'],
  ['/player/', '玩家档案'],
  ['/village', '村民族谱'],
  ['/machines', '公共机器'],
  ['/community', '社区'],
  ['/docs', '文档中心'],
  ['/bans', '封禁公示'],
  ['/chat', '聊天广场'],
  ['/tasks', '任务中心'],
  ['/whitelist', '加入白名单'],
  ['/questionnaire-result', '问卷结果'],
  ['/status', '服务器状态'],
  ['/map', '服务器地图'],
  ['/login', '登录'],
  ['/register', '注册'],
  ['/forgot-password', '找回密码'],
  ['/reset-password', '重置密码']
]

export function pageNameForPath(path: string): string {
  if (!path || path === '/' || path === '/index.html') return ''
  let best = ''
  let name = ''
  for (const [prefix, title] of PAGE_TITLES) {
    if ((path === prefix || path.startsWith(prefix)) && prefix.length > best.length) {
      best = prefix
      name = title
    }
  }
  return name
}

/**
 * 应用页面标题与 meta。page 传路由 meta.title(空串=站点首页);
 * description 可传详情页摘要(如文档标题/玩家名)覆盖站点默认。
 */
export function applySeo(page?: string, description?: string, noindex = false) {
  const t = state.template
    .replace('{page}', page || '')
    .replace('{site}', state.site)
    .replace(/^[\s\-–—·|]+/, '')
    .replace(/[\s\-–—·|]+$/, '')
  document.title = t || state.site
  setMeta('name', 'description', description || state.description)
  setMeta('name', 'keywords', state.keywords)
  setMeta('name', 'robots', noindex ? 'noindex, nofollow' : 'index, follow')
  setMeta('property', 'og:title', document.title)
  setMeta('property', 'og:site_name', state.site)
  setMeta('property', 'og:description', description || state.description)
  setMeta('property', 'og:image', state.ogImage)
}
