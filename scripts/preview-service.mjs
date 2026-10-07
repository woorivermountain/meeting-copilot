console.error([
  '서비스 프로덕션 미리보기는 아직 구성되지 않았습니다.',
  '로컬 Spring/Vue 서비스는 `npm run dev`로 실행하세요.',
  '레거시 Nuxt 미리보기는 보안 감사 항목을 해결하기 전 배포하지 마세요.',
  '정말 레거시 검증이 필요할 때만 `npm run preview:legacy`를 사용하세요.'
].join('\n'))
process.exitCode = 1
