export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}

export interface PageResult<T> {
  records: T[]
  page: number
  size: number
  total: number
}

export interface SessionUser {
  id: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn: number
}

export interface AuthenticationResult {
  userId: string
  tokens: TokenPair
}

export interface CaptchaChallenge {
  challengeId: string
  mimeType: string
  imageBase64: string
  expiresAt: string
}

export interface JwtIdentity {
  subject: string
  roles: string[]
  permissions: string[]
}

export interface ProductSummary {
  id: string
  spuCode: string
  name: string
  categoryName: string
  origin: string
  price: string
  marketPrice?: string
  imageUrl?: string
  traceabilityCode?: string
  status: string
  stock?: number
  specifications?: Record<string, string>
}

export interface CartLine {
  skuId: string
  name: string
  imageUrl?: string
  specifications: Record<string, string>
  price: string
  quantity: number
  selected: boolean
  stock: number
}

export interface OrderSummary {
  id: string
  orderNo: string
  status: string
  totalAmount: string
  payableAmount: string
  createdAt: string
  items: Array<{ skuId: string; name: string; quantity: number; price: string }>
}

export interface Citation {
  sourceType?: 'KNOWLEDGE' | 'WEB'
  documentId?: string
  chunkId: string
  title: string
  excerpt: string
  url?: string
  finalScore?: number
}

export interface ChatMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
  citations?: Citation[]
  refused?: boolean
}

export interface TrainingAssignment {
  assignmentId: string
  userId: string
  courseId: string
  courseTitle?: string
  status: string
  dueAt?: string
  progressPercent?: string
}
