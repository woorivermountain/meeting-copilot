import postgres from 'postgres'

const clients = new Map<string, ReturnType<typeof postgres>>()

export function database(url?: string) {
  if (!url) throw new Error('DATABASE_NOT_CONFIGURED')
  const existing = clients.get(url)
  if (existing) return existing
  const client = postgres(url, {
    max: 5,
    idle_timeout: 20,
    connect_timeout: 10,
    prepare: false,
    transform: { undefined: null }
  })
  clients.set(url, client)
  return client
}
