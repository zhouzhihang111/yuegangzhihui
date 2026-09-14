import { apiData } from '@ygh/web-shared'
import { useHttp } from './client'

export interface UserProfile {
  userId: string
  displayName: string
  avatarUrl?: string
  phone?: string
  email?: string
  locale: string
  timezone: string
  version: number
}

export interface UpdateUserProfileCommand {
  displayName: string
  avatarUrl?: string
  phone?: string
  email?: string
  locale: string
  timezone: string
  version: number
}

export interface Address {
  id: string
  label?: string
  recipientName: string
  recipientPhone: string
  countryCode: string
  provinceCode?: string
  provinceName: string
  cityName: string
  districtName: string
  addressDetail: string
  postalCode?: string
  defaultAddress: boolean
  version: number
  updatedAt: string
}

export interface CreateAddressCommand {
  label?: string
  recipientName: string
  recipientPhone: string
  countryCode: string
  provinceCode?: string
  provinceName: string
  cityName: string
  districtName: string
  addressDetail: string
  postalCode?: string
  defaultAddress: boolean
}

export type UpdateAddressCommand = CreateAddressCommand & { version: number }

export async function getMyProfile(): Promise<UserProfile> {
  return apiData(await useHttp().get('/api/v1/users/me'))
}

export async function updateMyProfile(command: UpdateUserProfileCommand): Promise<UserProfile> {
  return apiData(await useHttp().put('/api/v1/users/me', command))
}

export async function listMyAddresses(): Promise<Address[]> {
  return apiData(await useHttp().get('/api/v1/users/me/addresses'))
}

export async function createAddress(command: CreateAddressCommand): Promise<Address> {
  return apiData(await useHttp().post('/api/v1/users/me/addresses', command))
}

export async function updateAddress(id: string, command: UpdateAddressCommand): Promise<Address> {
  return apiData(await useHttp().put(`/api/v1/users/me/addresses/${id}`, command))
}

export async function deleteAddress(id: string, version: number): Promise<void> {
  await useHttp().delete(`/api/v1/users/me/addresses/${id}`, { params: { version } })
}

export async function makeDefaultAddress(id: string, version: number): Promise<Address> {
  return apiData(await useHttp().put(`/api/v1/users/me/addresses/${id}/default`, undefined, { params: { version } }))
}
