import type { KnowledgeWorkspace } from './knowledge'

export function createKnowledgeDemo(): KnowledgeWorkspace {
  const product = '10000000-0000-4000-8000-000000000001'
  const engineering = '10000000-0000-4000-8000-000000000002'
  return {
    boxes: [
      { id: product, teamId: 'demo', name: '제품 정책', department: '제품기획' },
      { id: engineering, teamId: 'demo', name: '기술 검토', department: '개발' }
    ],
    agents: [
      { id: '20000000-0000-4000-8000-000000000001', teamId: 'demo', name: '제품 검토 에이전트', department: '제품기획', boxIds: [product] },
      { id: '20000000-0000-4000-8000-000000000002', teamId: 'demo', name: '개발 검토 에이전트', department: '개발', boxIds: [engineering] }
    ],
    documents: [
      { id: '30000000-0000-4000-8000-000000000001', boxId: product, title: '파일럿 승인 기준 (예시)', content: '파일럿 출시 전 사용자가 결정과 후속 행동 후보를 검토하고 승인해야 합니다. 담당자와 기한이 명시되지 않으면 미정으로 남깁니다. 파일럿 출시 일정은 아직 합의되지 않았습니다.', version: 1, updatedAt: '2026-10-07', approvedBy: 'demo-owner' },
      { id: '30000000-0000-4000-8000-000000000002', boxId: engineering, title: '파일럿 기술 점검 (예시)', content: '파일럿 출시 전에 근거 인용, 접근 권한, 실패 시 재시도 동작을 검증합니다. 부서별 자료함은 에이전트에 명시적으로 배정합니다. 다른 팀의 자료는 검색하지 않습니다. 자동 업무 실행은 포함하지 않습니다.', version: 1, updatedAt: '2026-10-07', approvedBy: 'demo-owner' }
    ]
  }
}
