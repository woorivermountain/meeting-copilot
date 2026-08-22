export default defineEventHandler(() => ({
  status: 'ok',
  service: 'meeting-copilot',
  transcriptPersistence: false,
  now: new Date().toISOString()
}))
