export interface User {
  username: string
  email: string
  status: 'pending' | 'pending_review' | 'approved' | 'rejected' | 'banned'
  regTime?: string
  questionnaireScore?: number
  questionnairePassed?: boolean
  questionnaireReviewSummary?: string
  questionnaireScoredAt?: number
  minecraftUuid?: string
  minecraftName?: string
  microsoftVerified?: boolean
  verifiedAt?: number
  verifyType?: string
}

export interface ServerStatus {
  online: boolean
  players: { online: number; max: number }
  version: string
  tps?: number
  motd?: string
}

export interface PortalConfig {
  name: string
  description: string
  version: string
  ip: string
  port: number
  logo?: string
  background?: string
  announcement?: string
  mapUrl?: string
  social?: {
    wiki?: string
  }
  team?: Array<{
    name: string
    role: string
    avatar?: string
  }>
  features?: Array<{
    icon: string
    title: string
    description: string
  }>
  timeline?: Array<{
    date: string
    title: string
    description?: string
    image?: string
  }>
  carousel?: Array<{
    image: string
    title: string
    subtitle?: string
  }>
}

export interface Question {
  id: number
  type: 'single_choice' | 'multiple_choice' | 'text'
  question: string
  options?: Array<{ text: string; score: number }>
  maxScore: number
  required: boolean
  scoringRule?: string
}

export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  message?: string
}

export interface ChatMessage {
  player: string
  message: string
  timestamp: number
}

export interface AuditRecord {
  id: number
  action: string
  operator: string
  target: string
  detail: string
  timestamp: number
}

export interface QuestionnaireResult {
  totalScore: number
  maxScore: number
  passed: boolean
  results: Array<{
    questionId: number
    score: number
    maxScore: number
  }>
}

export interface CmiPlayerData {
  name: string
  timePlayed: number
  balance: number
  lastLogin: number
  banReason?: string
  isBanned?: boolean
}

export interface LeaderboardEntry {
  name: string
  balance?: number
  timePlayed?: number
  rank: number
}
