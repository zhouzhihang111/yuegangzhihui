import { apiData } from '@ygh/web-shared'
import { useHttp } from './client'

export interface Wallet {
  userId: string
  availableBalance: string
  frozenBalance: string
  currency: string
  version: number
}

export interface WalletTransaction {
  transactionId: string
  type: string
  status: string
  amount: string
  currency: string
  referenceId: string
  createdAt: string
}

export async function getWallet(): Promise<Wallet> {
  return apiData(await useHttp().get('/api/v1/wallet'))
}

export async function listWalletTransactions(limit = 50): Promise<WalletTransaction[]> {
  return apiData(await useHttp().get('/api/v1/wallet/transactions', { params: { limit } }))
}

export async function rechargeWallet(amount: string): Promise<WalletTransaction> {
  const requestId = crypto.randomUUID()
  return apiData(await useHttp().post('/api/v1/wallet/recharges', {
    requestId,
    referenceId: `WEB-RECHARGE-${requestId}`,
    amount,
    currency: 'CNY',
  }))
}
