export default defineEventHandler((event) => {
  deleteCookie(event, 'moida_session', { path: '/' })
  return { ok: true }
})
