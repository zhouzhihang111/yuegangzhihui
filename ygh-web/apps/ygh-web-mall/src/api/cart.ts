import { apiData } from '@ygh/web-shared'
import { useHttp } from './client'
export interface CartItem { id:string;skuId:string;quantity:number;selected:boolean;version:number }
export async function listCartItems():Promise<CartItem[]>{return apiData(await useHttp().get('/api/v1/cart/items'))}
export async function saveCartItem(command:{skuId:string;quantity:number;selected:boolean;version:number}):Promise<CartItem>{return apiData(await useHttp().put('/api/v1/cart/items',command))}
export async function removeCartItem(item:CartItem):Promise<void>{await useHttp().delete(`/api/v1/cart/items/${item.id}`,{params:{version:item.version}})}
export async function addCartItem(skuId:string,quantity:number):Promise<CartItem>{const items=await listCartItems();const current=items.find(item=>item.skuId===skuId);return saveCartItem({skuId,quantity:(current?.quantity||0)+quantity,selected:true,version:current?.version||0})}
