import { apiData, type ProductSummary } from '@ygh/web-shared'
import { useHttp } from './client'

export interface Product {
  spuId: string
  skuId: string
  categoryId: string
  brandId: string
  name: string
  skuCode: string
  price: string
  currency: string
  status: string
  images: string[]
  traceabilityCode: string
  version: number
  specifications: Record<string, string>
}
export interface Category { id:string;parentId?:string;code:string;name:string;sortOrder:number;enabled:boolean;version:number }
export interface TraceEvent { id?:string;eventType?:string;location?:string;description?:string;occurredAt?:string }
export interface ProductBatch { id:string;skuId:string;batchNo:string;origin:string;proofUrl?:string;producedOn?:string;expiresOn?:string;traceDescription?:string }

export async function listProducts(params: Record<string, string | number | undefined> = {}): Promise<Product[]> {
  return apiData(await useHttp().get('/api/v1/products', { params }))
}
export async function getProduct(skuId: string): Promise<Product> { return apiData(await useHttp().get(`/api/v1/products/${skuId}`)) }
export async function listCategories(): Promise<Category[]> { return apiData(await useHttp().get('/api/v1/product-categories')) }
export async function listTraceEvents(skuId: string): Promise<TraceEvent[]> { return apiData(await useHttp().get(`/api/v1/products/${skuId}/trace-events`)) }
export async function listProductBatches(skuId: string): Promise<ProductBatch[]> { return apiData(await useHttp().get(`/api/v1/products/${skuId}/batches`)) }
export function productSummary(product: Product, categoryName = '跨境甄选'): ProductSummary {
  return { id:product.skuId,spuCode:product.skuCode,name:product.name,categoryName,origin:product.specifications['产地']||'跨境甄选',price:product.price,imageUrl:product.images[0],traceabilityCode:product.traceabilityCode,status:product.status,specifications:product.specifications }
}
