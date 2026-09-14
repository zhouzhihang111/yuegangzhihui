import { createHttpClient, useSessionStore } from '@ygh/web-shared'
import router from '@/router'

let client: ReturnType<typeof createHttpClient> | undefined
let clientBaseUrl = ''

export function useHttp() {
  const session = useSessionStore()
  const baseUrl = import.meta.env.VITE_GATEWAY_URL || ''
  if (!client || clientBaseUrl !== baseUrl) {
    clientBaseUrl = baseUrl
    client = createHttpClient(baseUrl, {
    accessToken: () => session.bearer,
    refreshToken: () => session.renewal,
    updateTokens: tokens => session.rotate(tokens),
    clearSession: () => session.clear(),
    onForbidden: () => { void router.push('/403') },
  })
  }
  return client
}
