import { defineStore } from 'pinia'
import type { SessionUser, TokenPair } from './types'
import { identityFromAccessToken } from './auth'

const SESSION_REFRESH_KEY = 'ygh.refresh-token'
const USER_KEY = 'ygh.session-user'

function readUser(): SessionUser | null {
  try {
    const value = sessionStorage.getItem(USER_KEY)
    return value ? JSON.parse(value) as SessionUser : null
  } catch {
    return null
  }
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    // Access tokens deliberately remain memory-only. A page reload must use the
    // refresh-token flow instead of restoring a bearer token from Web Storage.
    bearer: '',
    renewal: localStorage.getItem(SESSION_REFRESH_KEY) ?? '',
    user: readUser() as SessionUser | null,
  }),
  getters: {
    authenticated: (state) => Boolean(state.bearer && state.user),
    isAdmin: (state) => state.user?.roles.includes('ADMIN') ?? false,
  },
  actions: {
    establish(tokens: TokenPair, user: SessionUser) {
      this.bearer = tokens.accessToken
      this.renewal = tokens.refreshToken
      this.user = user
      localStorage.setItem(SESSION_REFRESH_KEY, tokens.refreshToken)
      sessionStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    rotate(tokens: TokenPair) {
      this.bearer = tokens.accessToken
      this.renewal = tokens.refreshToken
      localStorage.setItem(SESSION_REFRESH_KEY, tokens.refreshToken)
      if (this.user) {
        const identity = identityFromAccessToken(tokens.accessToken)
        this.user = { ...this.user, id: identity.subject, roles: identity.roles, permissions: identity.permissions }
        sessionStorage.setItem(USER_KEY, JSON.stringify(this.user))
      }
    },
    restore(tokens: TokenPair) {
      const identity = identityFromAccessToken(tokens.accessToken)
      const previous = this.user?.id === identity.subject ? this.user : null
      this.establish(tokens, {
        id: identity.subject,
        username: previous?.username ?? identity.subject,
        displayName: previous?.displayName ?? '平台用户',
        roles: identity.roles,
        permissions: identity.permissions,
      })
    },
    clear() {
      this.bearer = ''
      this.renewal = ''
      this.user = null
      sessionStorage.removeItem(USER_KEY)
      localStorage.removeItem(SESSION_REFRESH_KEY)
    },
    can(permission: string) {
      return this.isAdmin || Boolean(this.user?.permissions.includes(permission))
    },
  },
})
