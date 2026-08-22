# 모이다 (Meeting Copilot)

오프라인 회의실의 공용 화면을 위한 AI 워크스페이스입니다. 사용자가 명시적으로 부른 순간에만 최근 회의 맥락을 분석하고, 회의 종료 후에는 사람이 승인한 결정·할 일·미결 쟁점만 저장합니다.

## 지금 구현된 것

- 브라우저 Web Speech 기반 실시간 전사와 자동 재시작, 수동 입력 폴백
- 로컬 정규식 호출 게이트 (`일반 발화 → API 비용 0`)
- 맥락 모델 → 고성능 추론 모델의 선택적 승격 하네스
- Structured Outputs + 런타임 스키마 + 근거 범위 검증
- 맥락 스냅샷, 회의 중 응답, 종료 후보, 사용자 승인 API
- 담당자 명단 서버 검증과 미배정 폴백
- Stripe Checkout/Subscription 웹훅 골격
- 전사·오디오 컬럼이 없는 PostgreSQL 스키마
- 수익화 가설과 고정 평가 세트

## 실행

```bash
npm install
cp .env.example .env
npm run dev
```

기본 `NUXT_LLM_MODE=mock`에서는 API 키 없이 전체 흐름이 동작합니다. 실제 모델을 사용하려면 다음 값을 설정합니다.

```dotenv
NUXT_LLM_MODE=openai
NUXT_OPENAI_API_KEY=...
NUXT_CONTEXT_MODEL=gpt-5.6-luna
NUXT_REASONING_MODEL=gpt-5.6-sol
```

## 검증

```bash
npm test
npm run typecheck
npm run build
npm run eval
```

## 핵심 엔드포인트

| Method | Path | 역할 | 영속화 |
| --- | --- | --- | --- |
| GET | `/api/health` | 상태·데이터 정책 확인 | 없음 |
| POST | `/api/analyze` | 명시적 호출에 대한 근거 기반 카드 | 없음 |
| POST | `/api/context-snapshot` | 세션 맥락 압축 | 없음 |
| POST | `/api/meetings/:id/wrapup-preview` | 종료 산출물 후보 | 없음 |
| POST | `/api/meetings/:id/wrapup-confirm` | 승인 산출물 저장 | 승인 항목만 |
| POST | `/api/billing/checkout` | 좌석 구독 Checkout | Stripe |
| POST | `/api/webhooks/stripe` | 구독 상태 동기화 | 구독 메타데이터 |

인증·워크스페이스 API도 포함합니다: `/api/auth/signup`, `/api/auth/login`, `/api/auth/logout`, `/api/auth/check-email`, `/api/teams`, `/api/meetings`. 운영 모드에서는 HttpOnly 서명 세션과 팀 소속/소유자 권한을 서버에서 다시 검사합니다.

## 데이터 경계

전사 원문은 브라우저 메모리와 요청 처리 중 서버 메모리에만 존재하며 우리 DB와 애플리케이션 로그에 저장하지 않습니다. Web Speech와 LLM 공급자의 일시 처리는 각 공급자 정책의 적용을 받습니다. 자세한 구조는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), 가격·판매 가설은 [docs/PRODUCT_STRATEGY.md](docs/PRODUCT_STRATEGY.md)를 참고하세요.
