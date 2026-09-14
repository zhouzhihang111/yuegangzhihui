import { apiData } from '@ygh/web-shared'
import { useHttp } from './client'

export interface Notification {
  id: string
  title: string
  content: string
  status: string
  read: boolean
  createdAt: string
}

export async function listNotifications(): Promise<Notification[]> {
  return apiData(await useHttp().get('/api/v1/notifications'))
}

export async function markNotificationRead(id: string): Promise<void> {
  await useHttp().put(`/api/v1/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<number> {
  const result = apiData<{ updated: number }>(await useHttp().put('/api/v1/notifications/read-all'))
  return result.updated
}

export async function getUnreadNotificationCount(): Promise<number> {
  const result = apiData<{ count: number }>(await useHttp().get('/api/v1/notifications/unread-count'))
  return result.count
}
