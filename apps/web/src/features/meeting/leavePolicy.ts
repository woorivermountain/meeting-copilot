export interface LeaveState {open:boolean;active:boolean;dirty:boolean;draft:boolean;busy:boolean}
export function needsLeaveConfirmation(state:LeaveState){return state.open||state.active||state.dirty||state.draft||state.busy}
export function leaveDescription(state:LeaveState){
  if(state.busy)return '저장 또는 AI 요청을 처리하고 있어요. 완료한 뒤 이동해 주세요.'
  if(state.dirty||state.draft)return '이동하면 저장하지 않은 기록과 작성 중인 내용이 사라져요. 음성 입력도 멈춰요.'
  if(state.active)return '이동하면 이 기기의 음성 입력이 멈춰요. 저장한 기록은 그대로 남아요.'
  return '회의가 종료되지는 않아요. 저장한 기록은 나중에 다시 열 수 있어요.'
}
