import { apiData } from '@ygh/web-shared'
import { useHttp } from './client'
import { getWallet } from './wallet'

export interface Order {
  orderId: string
  orderNo: string
  userId: string
  status: string
  totalAmount: string
  currency: string
  version: number
  createdAt: string
}
export interface OrderItemCommand { skuId:string;skuCode:string;productName:string;unitPrice:string;quantity:number }
export interface AddressSnapshot { recipientName:string;recipientPhone:string;countryCode:string;provinceCode?:string;provinceName:string;cityName:string;districtName:string;addressDetail:string;postalCode?:string }
export interface CreateOrderCommand { requestId:string;items:OrderItemCommand[];address:AddressSnapshot;remark?:string }
export interface OrderPreview { items:OrderItemCommand[];totalAmount:string;currency:string;address:AddressSnapshot;warnings:string[] }

export async function listMyOrders(status?: string, limit = 50): Promise<Order[]> {
  return apiData(await useHttp().get('/api/v1/orders', { params: { status, limit } }))
}

export async function getOrder(id: string): Promise<Order> {
  return apiData(await useHttp().get(`/api/v1/orders/${id}`))
}

export async function cancelOrder(order: Order): Promise<Order> {
  return apiData(await useHttp().post(`/api/v1/orders/${order.orderId}/cancel`, undefined, { params: { version: order.version } }))
}

export async function requestOrderRefund(order: Order): Promise<Order> {
  return apiData(await useHttp().post(`/api/v1/orders/${order.orderId}/refund`, undefined, { params: { version: order.version } }))
}

export async function payOrder(order: Order): Promise<Order> {
  try {
    return apiData(await useHttp().post(`/api/v1/orders/${order.orderId}/confirm-wallet-payment`))
  } catch {
    // No successful wallet transaction exists yet; continue with a simulated wallet payment.
  }
  const wallet = await getWallet()
  if (wallet.currency !== order.currency || Number(wallet.availableBalance) < Number(order.totalAmount)) {
    throw new Error('模拟钱包余额不足，请先完成虚拟充值')
  }
  const requestId = crypto.randomUUID()
  await useHttp().post('/api/v1/wallet/payments', { requestId, referenceId: order.orderId, amount: order.totalAmount, currency: order.currency })
  return apiData(await useHttp().post(`/api/v1/orders/${order.orderId}/confirm-wallet-payment`))
}
export async function previewOrder(command:CreateOrderCommand):Promise<OrderPreview>{return apiData(await useHttp().post('/api/v1/orders/preview',command))}
export async function createOrder(command:CreateOrderCommand):Promise<Order>{return apiData(await useHttp().post('/api/v1/orders',command))}
