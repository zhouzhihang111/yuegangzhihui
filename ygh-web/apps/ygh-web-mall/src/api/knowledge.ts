import { apiData } from '@ygh/web-shared';import { useHttp } from './client'
export interface KnowledgeDocument{id:string;title:string;category:string;fileName:string;mediaType:string;sizeBytes:number;sha256:string;status:string;version:number;updatedAt:string}
export interface KnowledgeMetadata{documentId:string;issuingAuthority?:string;effectiveFrom?:string;expiresAt?:string;region?:string;classification:string;sourceName?:string;tags:string[];metadataVersion:number}
export interface KnowledgeSearchHit{documentId:string;chunkId:string;title:string;excerpt:string;documentVersion:number;sourceUpdatedAt?:string;lexicalScore:number;vectorScore:number;finalScore:number}
export async function listKnowledge(category?:string,limit=100):Promise<KnowledgeDocument[]>{return apiData(await useHttp().get('/api/v1/knowledge/documents',{params:{category:category||undefined,limit}}))}
export async function searchKnowledge(query:string,category?:string,limit=50):Promise<KnowledgeSearchHit[]>{return apiData(await useHttp().get('/api/v1/knowledge/search',{params:{query,category:category||undefined,limit}}))}
export async function getKnowledge(id:string):Promise<KnowledgeDocument>{return apiData(await useHttp().get(`/api/v1/knowledge/documents/${id}`))}
export async function getKnowledgeMetadata(id:string):Promise<KnowledgeMetadata>{return apiData(await useHttp().get(`/api/v1/knowledge/documents/${id}/metadata`))}
export async function getKnowledgeContent(id:string):Promise<Blob>{return (await useHttp().get<Blob>(`/api/v1/knowledge/documents/${id}/content`,{params:{inline:true},responseType:'blob'})).data}
