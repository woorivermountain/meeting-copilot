const forbiddenKeys = new Set(['transcript', 'recentTranscript', 'password', 'token', 'secret'])

export function safeOperationalLog(event: string, data: Record<string, unknown>) {
  const safe = Object.fromEntries(
    Object.entries(data).filter(([key]) => !forbiddenKeys.has(key))
  )
  console.info(JSON.stringify({ event, ...safe, at: new Date().toISOString() }))
}
