import { reactive } from 'vue'
import { api } from '@/services/api'

/**
 * 全局品牌状态:一次加载 /api/config,全站共享。
 * 品牌字段存于门户配置 portal.config(管理后台「门户管理 → 基础信息」),
 * 更换品牌 = 改配置,前端名称/标题/主题色/favicon 全部跟随。
 */
const state = reactive({
  loaded: false,
  full: '夏日小镇XMCraft',   // 品牌全名(portal.server_name)
  short: 'XMCraft',          // 短名(登录/注册大标题、页脚;portal.brand_short)
  tagline: '玩家账户系统',    // 副标语(portal.brand_tagline)
  logo: '/Logo111.png',      // portal.logo
  favicon: '',               // portal.favicon
  accent: '#f97316',         // 品牌主色(portal.accent)
  description: '',
})

/** Tailwind orange 默认色阶(每个档位的目标亮度,按 HSL L 值) */
const SHADES: Array<[string, string, number]> = [
  ['50', '#fff7ed', 0.97], ['100', '#ffedd5', 0.94], ['200', '#fed7aa', 0.87],
  ['300', '#fdba74', 0.78], ['400', '#fb923c', 0.67], ['500', '#f97316', 0.58],
  ['600', '#ea580c', 0.51], ['700', '#c2410c', 0.44], ['800', '#9a3412', 0.36],
  ['900', '#7c2d12', 0.29], ['950', '#431407', 0.18],
]

function hexToHsl(hex: string): [number, number] {
  const m = hex.replace('#', '')
  const r = parseInt(m.slice(0, 2), 16) / 255
  const g = parseInt(m.slice(2, 4), 16) / 255
  const b = parseInt(m.slice(4, 6), 16) / 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b)
  const l = (max + min) / 2
  if (max === min) return [0, 0]
  const d = max - min
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
  let h = 0
  if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6
  else if (max === g) h = ((b - r) / d + 2) / 6
  else h = ((r - g) / d + 4) / 6
  return [h * 360, s]
}

function hslToRgbTriplet(h: number, s: number, l: number): string {
  const f = (n: number) => {
    const k = (n + h / 30) % 12
    const a = s * Math.min(l, 1 - l)
    const v = l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))
    return Math.round(v * 255)
  }
  return `${f(0)} ${f(8)} ${f(4)}`
}

function applyAccent(hex: string) {
  if (!/^#[0-9a-fA-F]{6}$/.test(hex)) return
  const [h, s] = hexToHsl(hex)
  const root = document.documentElement
  for (const [step, def, l] of SHADES) {
    const dH = hexToHsl(def)[0]
    const dS = hexToHsl(def)[1]
    // 主色(500/600/700)完全采用品牌 hue/sat;浅深档保留品牌 hue、轻度混合默认饱和度,避免浅档发灰
    const use = ['500', '600', '700'].includes(step) ? [h, s] : [h, (s + dS) / 2]
    root.style.setProperty(`--brand-${step}`, hslToRgbTriplet(use[0], use[1], l))
  }
}

function applyFavicon(path: string) {
  if (!path) return
  const url = path.startsWith('http') ? path : path
  let link = document.querySelector<HTMLLinkElement>("link[rel~='icon']")
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.href = url
}

function applyPortal(p: Record<string, unknown>) {
  if (p.server_name) state.full = String(p.server_name)
  if (p.brand_short) state.short = String(p.brand_short)
  if (p.brand_tagline) state.tagline = String(p.brand_tagline)
  if (p.logo) state.logo = String(p.logo)
  if (p.favicon) state.favicon = String(p.favicon)
  if (p.accent) state.accent = String(p.accent)
  if (p.description) state.description = String(p.description)
  applyAccent(state.accent)
  applyFavicon(state.favicon)
  document.title = state.full
  state.loaded = true
}

export async function initBrand() {
  try {
    const r: any = await api.getConfig()
    if (r.success && r.data?.portal) applyPortal(r.data.portal)
  } catch { /* 后端不可达时用默认品牌 */ }
}

/** App.vue 已拉取 /api/config 时复用同一响应应用品牌 */
export function applyBrandFromConfig(portal: Record<string, unknown>) {
  applyPortal(portal || {})
}

/** 返回响应式品牌对象(勿展开,展开会丢失响应性) */
export function useBrand() {
  return state
}
