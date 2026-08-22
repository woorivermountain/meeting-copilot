export default defineNuxtConfig({
  compatibilityDate: '2026-08-01',
  devtools: { enabled: true },
  css: ['~/assets/css/main.css'],
  runtimeConfig: {
    openaiApiKey: '',
    databaseUrl: '',
    sessionSecret: '',
    stripeSecretKey: '',
    stripeWebhookSecret: '',
    stripeTeamPriceId: '',
    contextModel: 'gpt-5.6-luna',
    reasoningModel: 'gpt-5.6-sol',
    llmMode: 'mock',
    public: {
      appUrl: 'http://localhost:3000'
    }
  },
  nitro: {
    routeRules: {
      '/api/**': { cors: false },
      '/api/health': { cache: false }
    }
  },
  typescript: {
    strict: true,
    // CI runs `nuxt typecheck` separately. Vite's production transform should not
    // invoke a second checker with source files and a tsconfig simultaneously.
    typeCheck: false
  },
  app: {
    head: {
      title: '모이다 — 회의가 실행으로 이어지는 순간',
      meta: [
        { name: 'description', content: '오프라인 회의를 검토 가능한 결정과 실행으로 바꾸는 공유형 AI 워크스페이스' },
        { name: 'theme-color', content: '#f3f0e8' }
      ],
      link: [
        { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: 'anonymous' },
        { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Noto+Sans+KR:wght@400;500;600;700&display=swap' }
      ]
    }
  }
})
