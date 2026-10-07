# 부서 자료함 및 읽기 전용 에이전트

## 목적

자료함은 부서가 검토한 텍스트를 모으는 팀 공유 공간이다. 에이전트는 접근하도록 배정된 자료함에서 근거를 찾고, 공통 질문에 대한 부서별 검토 초안을 제시한다. 담당 부서 이름은 역할 설명이지 인증 정보가 아니다. 현재 보안 경계는 팀이며, 같은 팀의 사용자는 모든 자료함을 읽을 수 있다.

## 5분 데모

1. `.env.example`을 참고해 DB 없이 `NUXT_LLM_MODE=mock`으로 `npm run dev`를 실행한다.
2. `/knowledge`에서 제품기획·개발의 합성 예시 자료를 확인한다.
3. 두 에이전트를 선택하고 기본 질문을 전송한다. API 키가 없으므로 생성형 답변 대신 검색 원문이 표시된다.
4. 근거를 펼치고 `전체 원문 보기`로 자료를 확인한다.
5. 자료함 생성 → 에이전트와 자료함 배정 → 자료 검토 후 등록을 시험한다. 질문에 자료에 실제 포함된 단어를 넣는다.
6. 일치하지 않는 질문을 보내면 확인 불가로 응답한다. 새로고침 시 데모 변경 내용은 사라진다.

기존 회의 화면에서 자료함은 새 탭으로 열린다. 현재 회의 맥락을 자동 전송하지 않으며, 자료함의 선택 입력란에 필요한 맥락만 붙여 넣는다.

## DB 연결

개발용 PostgreSQL에 `db/migrations/001_init.sql`을 먼저, `002_knowledge.sql`을 다음으로 적용한다. 운영 DB에 자동 실행하지 않는다. 두 파일은 재실행을 위한 마이그레이션 추적기를 포함하지 않는다.

```sh
psql "$NUXT_DATABASE_URL" -v ON_ERROR_STOP=1 -1 -f db/migrations/001_init.sql
psql "$NUXT_DATABASE_URL" -v ON_ERROR_STOP=1 -1 -f db/migrations/002_knowledge.sql
```

이미 001이 적용된 개발 DB에는 002만 적용한다. `NUXT_DATABASE_URL`과 32자 이상의 `NUXT_SESSION_SECRET`을 설정한 후 기존 `/api/auth/signup` 또는 `/api/auth/login`으로 로그인하고, `/api/teams`로 팀을 생성·조회한다. 쿠키가 있는 브라우저에서 자료함의 팀 ID 입력란에 해당 UUID를 입력한다. 통합 인증·팀 선택 UI는 아직 구현하지 않았다.

실제 생성형 응답은 기존 공급자 설정인 `NUXT_LLM_MODE=openai`, `NUXT_OPENAI_API_KEY`와 모델 환경 변수를 사용한다. API 키는 서버 환경 변수로만 전달한다. 키 없는 mock 모드는 검색만 수행한다. DB 없이 실제 모델을 사용하려는 구성은 이 기능의 지원 범위가 아니다.

## 저장 구조

| 테이블 | 책임 |
| --- | --- |
| knowledge_boxes | 팀·담당 부서·자료함 이름 |
| knowledge_agents | 팀에 속한 읽기 전용 검토 에이전트 |
| knowledge_agent_boxes | 에이전트의 접근 자료함. 복합 FK로 타 팀 연결 차단 |
| knowledge_documents | 승인된 제목·원문·버전·등록 승인자·시각 |
| knowledge_audit | 변경 주체·작업 종류·대상 ID·시각. 원문·질의·답변은 기록하지 않음 |

현재 문서는 등록/삭제만 지원하며 버전은 1이다. 수정 이력 보존을 지원하는 것처럼 해석하지 않는다. 최대 팀당 자료함 30개, 에이전트 30개, 문서 100개, 문서당 30,000자다. 한도 초과는 409로 거절한다.

## API 계약

| Method | Path | 입력 | 권한·결과 |
| --- | --- | --- | --- |
| GET | `/api/knowledge?teamId={UUID}` | 팀 ID | 팀원: 자료함·에이전트·승인 문서. mock 데모는 빈 자료 구조와 demo 모드 |
| POST | `/api/knowledge` | operation, teamId 및 작업 필드 | 소유자만 변경. 생성된 대상 ID 반환 |
| POST | `/api/knowledge/query` | teamId, agentIds, question, meetingContext? | 팀원만, 최대 5개 에이전트. answers, persisted:false, retrieval:keyword-chunks |

변경 작업:

```json
{ "operation": "box", "teamId": "UUID", "name": "제품 정책", "department": "제품기획" }
{ "operation": "agent", "teamId": "UUID", "name": "제품 검토", "department": "제품기획", "boxIds": ["UUID"] }
{ "operation": "document", "teamId": "UUID", "boxId": "UUID", "title": "파일럿 기준", "content": "검토한 원문", "approved": true }
{ "operation": "delete-document", "teamId": "UUID", "documentId": "UUID" }
```

위 네 줄은 각각 독립된 요청 예시이며 `UUID`는 실제 UUID로 교체한다. 런타임 스키마는 `shared/knowledge.ts`가 기준이다. demoWorkspace는 DB 없는 mock 세션에서만 사용한다. DB 모드에서는 클라이언트가 보낸 자료·배정을 무시하고 서버에서 다시 조회한다.

401은 로그인 필요, 403은 팀/소유자/에이전트 접근 거절, 400은 입력 오류, 404는 접근 가능한 범위에서 대상을 찾지 못함, 409는 자료함 한도 초과, 503은 DB 미설정이다. 에이전트별 모델 실패는 전체 요청을 중단하지 않고 해당 answers 항목을 error로 반환한다.

## 검색·모델·검증 경계

1. 사용자의 팀 소속 확인 → 해당 팀의 에이전트와 자료만 로드한다.
2. 에이전트별 배정 자료함 필터를 적용한다.
3. 원문을 600자 청크, 120자 중첩으로 분할한다. 제목·본문에 질문 단어가 포함된 청크를 최대 6개 선택한다.
4. 모델 미연결이면 정확한 원문을 표시한다. 연결되면 선택한 청크와 질문·선택적 회의 맥락만 서버에서 전달한다.
5. 답변을 Zod로 검증하고 sourceId가 검색 결과에 있는지, 인용문이 그 청크에 정확히 존재하는지 확인한다.
6. 문자 범위는 서버가 다시 계산한다. JavaScript UTF-16 기준 시작 포함·끝 제외 인덱스이며 시간 정보가 아니다.
7. 근거가 없거나 인용 검증에 실패하면 insufficient로 반환한다. 답변은 DB에 저장하지 않는다.

현재는 단어 포함 여부에 의존하므로 한국어 조사·유의어·의미적 검색에 약하다. 인용문 존재 확인은 주장의 사실성·함의 검증과 다르다. 출처가 맞는 환각도 가능하므로 사람이 검토해야 한다. 시스템 프롬프트로 문서 내 지시를 데이터로 취급하지만 인젝션 차단을 보장하지 않는다. 안전 경계는 도구 권한 부재와 서버 자료 필터에 있다.

## 재현 테스트

```sh
npm test
npm run typecheck
npm run build
```

DB 통합 테스트는 기본적으로 건너뛴다. 비어 있는 일회용 로컬 DB 이름을 `copilot_test`로 시작하게 만든 후 다음을 실행한다. 테스트가 001·002를 직접 적용하므로 기존 DB에 재실행하지 않는다. 초기화/삭제 명령은 포함하지 않는다.

```sh
COPILOT_TEST_DATABASE_URL=postgres://USER@127.0.0.1:PORT/copilot_test npm test
```

Chrome과 Playwright가 설치된 개발 환경에서는 `node scripts/smoke-knowledge.mjs`로 DB 없는 mock 서버의 등록·승인·삭제·근거 이동·반응형 화면을 검증한다. 서버 기본 주소는 `http://127.0.0.1:3100`이며 `COPILOT_SMOKE_URL`로 변경할 수 있다. Playwright가 프로젝트 밖에 설치되어 있으면 `PLAYWRIGHT_MODULE_PATH`에 해당 모듈 경로를 지정한다.

일회용 DB에 연결된 로컬 mock 서버에서는 `COPILOT_ALLOW_TEST_WRITES=1 node scripts/smoke-knowledge-api.mjs`로 테스트 계정·팀을 생성하여 실제 쿠키 인증, 타 팀 접근 차단, 승인 필수, 클라이언트 자료 위조 무시, DB 자료함 생성 후 선택 상태를 검증한다. 이 스크립트는 테스트 데이터를 생성하므로 운영 또는 개인 개발 데이터가 있는 DB에 실행하지 않는다.

## 후속 확장

사내 운영 전에는 로그인·팀 선택 UX, 문서 버전과 철회, 부서/문서별 ACL, 보존·삭제 정책, 공급자 전송 동의, SSO, rate limit/비용 제한을 추가해야 한다. 이후 파일·표 파싱과 벡터 검색을 별도 인덱서로 추가하고, 동일한 팀/문서 권한 필터를 검색 전에 유지한다. 다중 에이전트 토론은 부서별 주장·근거·반론을 구분하는 읽기 전용 단계부터 시작하며 최종 합의 및 외부 실행 권한은 사람에게 남긴다.
