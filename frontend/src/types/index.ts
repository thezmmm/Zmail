export interface ApiResponse<T> {
  data: T | null
  error: string | null
  timestamp: string
}

export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export type EmailProvider = 'GMAIL' | 'MSGRAPH'
export type SyncStatus = 'RUNNING' | 'DONE' | 'FAILED'
export type DraftStatus = 'PENDING_REVIEW' | 'SENT' | 'REJECTED'
export type Priority = 'HIGH' | 'MEDIUM' | 'LOW'
export type Sentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
export type Category = 'WORK' | 'PERSONAL' | 'FINANCE' | 'PROMOTIONS' | 'OTHER'
export type ActionTaken = 'REPLY' | 'ARCHIVE' | 'FLAG' | 'NONE'

export interface UserDto {
  id: string
  email: string
  name: string
  createdAt: string
}

export interface AccountDto {
  id: string
  provider: EmailProvider
  accountEmail: string
  needsReauth: boolean
  createdAt: string
}

export interface ReauthDto {
  provider: EmailProvider
  loginPath: string
}

export interface ProcessingResult {
  id: string
  userId: string
  accountId: string
  emailProviderId: string
  subject: string
  sender: string
  processedAt: string
  category: Category
  priority: Priority
  sentiment: Sentiment
  summary: string
  actionItems: string[] | null
  requiresResponse: boolean
  actionTaken: ActionTaken
  draftStatus: DraftStatus | null
  replyDraft: string | null
}

export interface AgentSession {
  id: string
  userId: string
  title: string
  summary: string | null
  compressedUntil: number
  createdAt: string
  updatedAt: string
}

export interface AgentMessage {
  id: string
  sessionId: string
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface EmailRef {
  providerId: string
  accountId: string
}

export interface ChatRequest {
  sessionId: string
  message: string
  emails?: EmailRef[]
}