// 统一的状态映射工具
export const statusConfig = {
  pending: { text: '待答题', class: 'badge-warning', color: 'amber' },
  pending_review: { text: '待审核', class: 'badge-info', color: 'sky' },
  approved: { text: '已通过', class: 'badge-success', color: 'emerald' },
  rejected: { text: '未通过', class: 'badge-danger', color: 'rose' },
  banned: { text: '已封禁', class: 'badge-danger', color: 'rose' }
} as const

export type UserStatus = keyof typeof statusConfig

export function getStatusText(status: UserStatus | string): string {
  return statusConfig[status as UserStatus]?.text || status
}

export function getStatusClass(status: UserStatus | string): string {
  return statusConfig[status as UserStatus]?.class || 'badge-info'
}

export function getStatusColor(status: UserStatus | string): string {
  return statusConfig[status as UserStatus]?.color || 'stone'
}
