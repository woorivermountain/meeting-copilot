import {computed,onMounted,ref} from 'vue'
import {api} from './ApiClient'

interface LlmStatus {
  provider?:string;model:string;configured?:boolean;externalConsentRequired?:boolean;
  localReachable?:boolean;modelAvailable?:boolean;externalFallbackAllowed?:boolean;externalFallbackConfigured?:boolean
}
/** A settings check never claims a successful model generation. */
export function useLlmConsent(){
  const status=ref<LlmStatus|null>(null),statusError=ref('')
  const external=computed(()=>status.value?.provider==='deepseek'||status.value?.externalConsentRequired===true)
  const ready=computed(()=>!!status.value&&status.value.configured!==false)
  const consentText=computed(()=>!status.value?'AI 전송 설정을 확인하고 있어요.':status.value.provider==='deepseek'?'질문과 필요한 대화·자료 발췌문을 DeepSeek에 전송하는 데 동의해요.':external.value?'대화를 AI로 분석하고, 로컬 처리 실패 시 설정된 외부 API로 전송하는 데 동의해요.':'대화와 허용된 자료를 로컬 AI로 분석하는 데 동의해요.')
  const label=computed(()=>!status.value?'AI 설정 확인 필요':status.value.provider==='deepseek'?`DeepSeek · ${status.value.configured?'API 키 설정됨':'API 키 설정 필요'}`:`${status.value.model} · ${status.value.localReachable&&status.value.modelAvailable?'연결 확인':'연결 확인 필요'}`)
  const details=computed(()=>status.value?.provider==='deepseek'?'외부 API를 사용해요. 키 설정 여부만 확인했으며, 실제 연결·잔액·응답 성공은 요청 결과로 확인해요. 다른 업체로 자동 전환하지 않아요.':'로컬 모델 실행과 설치 상태를 확인해 주세요. 연결 확인은 실제 답변 생성 성공과 달라요.')
  async function refresh(){statusError.value='';try{status.value=await api.request<LlmStatus>('/llm/status')}catch{status.value=null;statusError.value='AI 전송 설정을 확인하지 못했어요. 설정을 다시 확인해 주세요.'}}
  onMounted(refresh)
  return {external,ready,consentText,label,details,statusError,refresh}
}
