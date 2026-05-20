const TOKEN_KEY = 'zmail_token'

export function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function isAuthenticated(): boolean {
  return getToken() !== null
}

/** Decode JWT payload without verifying signature (client-side only). */
export function getTokenExpiry(): number | null {
  const token = getToken()
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

/** Returns true when the stored token expires within the given threshold (ms). */
export function isTokenExpiringSoon(thresholdMs = 5 * 60 * 1000): boolean {
  const expiry = getTokenExpiry()
  if (expiry === null) return false
  return expiry - Date.now() < thresholdMs
}