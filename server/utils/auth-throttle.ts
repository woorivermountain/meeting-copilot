interface Attempt { failures: number; lockedUntil: number }
const attempts = new Map<string, Attempt>()

export function authThrottleKey(ip: string, email: string) {
  return `${ip}:${email.trim().toLowerCase()}`
}

export function assertLoginAllowed(key: string) {
  const state = attempts.get(key)
  if (state && state.lockedUntil > Date.now()) {
    throw createError({ statusCode: 429, statusMessage: 'AUTH_TEMPORARILY_LOCKED' })
  }
}

export function recordLoginFailure(key: string) {
  const current = attempts.get(key)
  const failures = (current?.lockedUntil && current.lockedUntil < Date.now() ? 0 : current?.failures || 0) + 1
  attempts.set(key, { failures, lockedUntil: failures >= 5 ? Date.now() + 60_000 : 0 })
}

export function clearLoginFailures(key: string) { attempts.delete(key) }
