<script setup lang="ts">
import { ref,reactive,computed,onMounted,onBeforeUnmount,watch } from 'vue'
import { SpeechSession,type RecognitionEngine,type SpeechState } from './SpeechSession'
import { MicrophoneInput,type MicrophoneState } from './MicrophoneInput'
import { LocalSpeechSession,type SpeechProvider,type SpeechDiagnostic } from './LocalSpeechSession'
import { api } from '../../shared/api/ApiClient'
import { DeepgramSpeechSession } from './DeepgramSpeechSession'
import type { Segment } from '../meeting/MeetingService'
import AppIcon from '../../shared/ui/AppIcon.vue'
import AppDialog from '../../shared/ui/AppDialog.vue'
import TranscriptFlowView from './TranscriptFlowView.vue'
const props=defineProps<{modelValue:Segment[];disabled:boolean;teamId:string}>()
const emit=defineEmits<{'update:modelValue':[Segment[]];active:[boolean];status:[string];draft:[boolean]}>()
const state=reactive<SpeechState>({status:'idle',interim:'',error:'',audio:false})
const mic=reactive<MicrophoneState>({status:'idle',level:0,error:''})
const consent=ref(false),supported=ref(false),manual=ref(''),limit=ref(''),setup=ref(false),showManual=ref(false)
let session:SpeechSession|undefined,request=0,preview=false
let local:LocalSpeechSession|undefined
let deepgram:DeepgramSpeechSession|undefined
const deepgramReady=ref(false),deepgramMessage=ref('Deepgram 설정을 확인해 주세요.')
async function checkDeepgram(){deepgramReady.value=false;try{const result=await api.request<{ready:boolean;message:string}>(`/teams/${props.teamId}/speech/deepgram/status`);deepgramReady.value=result.ready;deepgramMessage.value=result.message}catch{deepgramMessage.value='Deepgram 연결 설정이 필요해요. 서버 재시작 후 다시 확인해 주세요.'}}
const mode=ref<'local'|'browser'|'deepgram'>('local'),device=ref(''),devices=ref<MediaDeviceInfo[]>([]),localReady=ref(false),healthMessage=ref('전사 연결을 확인하고 있어요.'),checking=ref(false)
const provider=ref<SpeechProvider>('local')
const providerName=computed(()=>({local:'로컬 전사 서버',azure:'Azure Speech',groq:'Groq Whisper'})[provider.value])
const external=computed(()=>provider.value!=='local')
const processing=ref(true)
const chunkSeconds=ref<4|8>(4)
const diagnostics=ref<SpeechDiagnostic[]>([])
function recordDiagnostic(value:SpeechDiagnostic){diagnostics.value=[...diagnostics.value.slice(-19),value]}
const latestDiagnostic=computed(()=>diagnostics.value.at(-1))
const diagnosticHint=computed(()=>{const d=latestDiagnostic.value;if(!d)return '';if(d.clippedPercent>1)return '입력이 너무 클 수 있어요. 마이크 입력 크기를 낮춰 비교해 보세요.';if(d.rms<.01)return '입력 음량이 작을 수 있어요. 마이크와 거리를 좁혀 비교해 보세요.';if(d.queueWaitMs>4000)return '전사 요청이 밀리고 있어요. 네트워크와 처리 지연을 확인해 주세요.';if(d.split==='limit')return '말하는 중에 구간이 나뉘었어요. 설정에서 문맥 유지 모드와 비교해 보세요.';return '수치는 연결·입력 상태 참고용이며, 인식 정확도를 뜻하지 않아요.'})
const processingStatus=ref('마이크 테스트 후 적용 상태를 확인할 수 있어요.')
const liveMessage=computed(()=>state.status==='stopping'?'남은 음성을 문장으로 바꾸고 있어요. 잠시 기다려 주세요.':state.phase==='processing'?'문장으로 바꾸고 있어요. 음성 입력은 계속돼요.':'음성을 듣고 있어요. 잠깐 말을 멈추면 문장을 먼저 처리해요.')
const canStart=computed(()=>mode.value==='deepgram'?deepgramReady.value:mode.value==='local'?localReady.value&&!checking.value:supported.value)
const selectedLabel=computed(()=>mode.value==='browser'?'시스템 기본 마이크':devices.value.find(d=>d.deviceId===device.value)?.label||'마이크 선택')
async function refreshDevices(){try{devices.value=(await navigator.mediaDevices?.enumerateDevices()||[]).filter(d=>d.kind==='audioinput');if(device.value&&!devices.value.some(d=>d.deviceId===device.value)){stop();device.value='';mic.error='선택한 마이크가 분리됐어요. 다른 입력을 선택해 주세요.'}}catch{mic.error='입력 장치 목록을 읽지 못했어요. 사이트 권한을 확인해 주세요.'}}
async function checkHealth(){checking.value=true;try{const result=await api.request<{ready:boolean;message:string;provider:SpeechProvider}>(`/teams/${props.teamId}/speech/status`);if(!['local','azure','groq'].includes(result.provider))throw new Error('지원하지 않는 전사 설정이에요. 서버 설정을 확인해 주세요.');if(provider.value!==result.provider)consent.value=false;provider.value=result.provider;localReady.value=result.ready;healthMessage.value=result.message}catch(e){localReady.value=false;healthMessage.value=e instanceof Error?e.message:'전사 서버 상태를 확인하지 못했어요.'}finally{checking.value=false}}
const microphone=new MicrophoneInput(next=>Object.assign(mic,next))
const running=computed(()=>['starting','listening','reconnecting','stopping'].includes(state.status))
const busy=computed(()=>running.value||mic.status==='requesting'||mic.status==='ready')
const labels={idle:'전사 대기',starting:'연결 중',listening:'전사 중',reconnecting:'다시 연결 중',stopping:'마지막 문장 처리 중',error:'연결 확인 필요',unsupported:'음성 인식 미지원'}
function append(text:string){if(!text.trim())return;if(props.modelValue.length>=500){limit.value='기록이 가득 찼습니다. 저장한 뒤 새 회의를 열어 주세요.';stop();return}emit('update:modelValue',[...props.modelValue,{id:crypto.randomUUID(),receivedAt:new Date().toISOString(),text:text.trim().slice(0,4000)}])}
onMounted(()=>{const w=window as unknown as {SpeechRecognition?:new()=>RecognitionEngine;webkitSpeechRecognition?:new()=>RecognitionEngine};const Ctor=w.SpeechRecognition||w.webkitSpeechRecognition;supported.value=!!Ctor;if(Ctor)session=new SpeechSession(new Ctor(),s=>Object.assign(state,s),append);void refreshDevices();void checkHealth();navigator.mediaDevices?.addEventListener('devicechange',refreshDevices)})
async function start(onlyMic=false){if(running.value||mic.status==='requesting'||props.disabled||!onlyMic&&(!consent.value||!canStart.value))return;preview=onlyMic;const current=++request;const selectedProvider=provider.value;const externalApproved=mode.value==='local'&&external.value&&consent.value;if(await microphone.start(mode.value==='browser'?'':device.value,processing.value)&&current===request){const settings=microphone.audioProcessing;const label=(value:boolean|undefined)=>value===undefined?'확인 불가':value?'켜짐':'꺼짐';processingStatus.value=`잡음 억제 ${label(settings.noiseSuppression)} · 울림 감소 ${label(settings.echoCancellation)}`;await refreshDevices();if(current!==request)return;if(!onlyMic){diagnostics.value=[];if(mode.value==='deepgram'){deepgram?.dispose();deepgram=new DeepgramSpeechSession(props.teamId,s=>Object.assign(state,s),append);void deepgram.start(microphone.mediaStream!)}else if(mode.value==='local'){local?.dispose();local=new LocalSpeechSession(props.teamId,s=>Object.assign(state,s),append,externalApproved,selectedProvider,recordDiagnostic,chunkSeconds.value);void local.start(microphone.mediaStream!)}else session?.start();setup.value=false;consent.value=false}}}
function requestStart(){if(props.disabled)return;consent.value=false;setup.value=true;void checkHealth()}
function stop(){request++;preview=false;session?.stop();local?.stop();deepgram?.stop();microphone.stop()}
function closeSetup(){if(preview||mic.status==='requesting')stop();setup.value=false}
watch(()=>state.status,status=>{emit('status',labels[status]);if(!preview&&['idle','error'].includes(status))microphone.stop()},{immediate:true})
watch(()=>mic.status,status=>{if(status==='error'){session?.stop();local?.dispose();deepgram?.dispose();Object.assign(state,{status:'error',audio:false,interim:''})}})
watch(mode,()=>{stop();deepgram?.dispose();consent.value=false;state.error='';state.status='idle';state.interim='';if(mode.value==='deepgram')void checkDeepgram()})
watch(device,()=>{if(preview)stop()})
watch(processing,()=>{if(preview)stop();processingStatus.value='설정을 바꿨어요. 마이크 테스트로 적용 상태를 확인해 주세요.'})
watch(busy,value=>emit('active',value))
watch(manual,value=>emit('draft',!!value.trim()))
watch(()=>props.disabled,value=>{if(value)stop()})
onBeforeUnmount(()=>{request++;session?.dispose();local?.dispose();deepgram?.dispose();microphone.stop();navigator.mediaDevices?.removeEventListener('devicechange',refreshDevices);emit('active',false)})
function edit(id:string,text:string){emit('update:modelValue',props.modelValue.map(s=>s.id===id?{...s,text}:s))}
defineExpose({requestStart,stop,openSetup:()=>setup.value=true})
</script>
<template>
  <section class="transcript-stage" aria-label="회의 전사">
    <div class="transcript-heading"><h2>대화 기록</h2><span>{{modelValue.length}}개 문장</span><button class="quiet-button" :disabled="disabled||running" @click="requestStart">{{selectedLabel}}</button><button class="quiet-button" :disabled="disabled" @click="showManual=!showManual"><AppIcon name="note" :size="16"/>직접 입력</button></div>
    <p v-if="mic.error||state.error||limit" role="alert" class="notice error">{{mic.error||state.error||limit}}</p>
    <div v-if="state.status==='error'&&!disabled" class="actions"><span class="muted">이미 표시된 문장은 남아 있어요. 처리되지 않은 음성은 다시 말하거나 직접 입력해 주세요.</span><button class="secondary" @click="requestStart">전사 다시 연결</button></div>
    <div v-if="running&&mode==='local'" class="transcription-live" role="status"><span>{{liveMessage}}</span><meter v-if="state.audio" min="0" max="100" :value="mic.level" aria-label="현재 마이크 입력 크기"/></div>
    <details v-if="latestDiagnostic" class="speech-diagnostics"><summary>전사 상태 확인</summary><p>{{diagnosticHint}}</p><p class="muted">최근 구간 {{latestDiagnostic.audioSeconds.toFixed(1)}}초 · 전송 대기 {{(latestDiagnostic.queueWaitMs/1000).toFixed(1)}}초 · 요청 응답 {{(latestDiagnostic.responseMs/1000).toFixed(1)}}초 · 대기 {{latestDiagnostic.queued}}개</p><p class="muted">구간 분리: {{({pause:'말 사이 쉼',limit:'최대 길이 도달',stop:'사용자 중지'})[latestDiagnostic.split]}} · 빈 응답: {{latestDiagnostic.empty?'예':'아니요'}} · 입력 RMS {{latestDiagnostic.rms.toFixed(3)}} · 포화 샘플 {{latestDiagnostic.clippedPercent.toFixed(1)}}%</p><p class="muted">이 탭에서만 확인하는 처리 지표예요. 음성·원문을 진단 로그로 저장하지 않아요. 요청 응답 시간에는 네트워크와 서버 처리가 포함돼요.</p></details>
    <div v-if="!modelValue.length&&!state.interim" class="transcript-empty">
      <h2>{{running?'첫 문장을 기다리고 있어요':'아직 대화 기록이 없어요'}}</h2>
      <p>{{disabled?'이 회의에는 저장된 대화가 없어요.':running?'인식된 문장부터 여기에 표시해요.':'상단에서 전사를 시작하거나 직접 입력해 주세요.'}}</p>
      <button v-if="!disabled&&!running" class="quiet-button" @click="showManual=true">직접 입력하기</button>
    </div>
    <TranscriptFlowView :segments="modelValue" :interim="state.interim" :disabled="disabled" :active="running" @edit="edit"/>
    <form v-if="showManual" class="manual-composer" @submit.prevent="append(manual);manual=''"><label for="manual-note">직접 입력</label><div class="actions"><input id="manual-note" v-model="manual" required maxlength="4000" :disabled="disabled" placeholder="기록할 내용을 입력하세요"><button :disabled="disabled||!manual.trim()">추가</button></div></form>
  </section>
  <AppDialog :open="setup" title="전사를 시작하기 전에" @close="closeSetup">
    <label for="speech-mode">전사 방식</label><select id="speech-mode" v-model="mode" :disabled="running"><option value="local">{{providerName}} · 선택한 마이크</option><option value="deepgram">Deepgram · 실시간 전사 테스트</option><option value="browser" :disabled="!supported">브라우저 인식 · 기본 마이크만</option></select>
    <template v-if="mode==='deepgram'"><p class="muted">{{deepgramMessage}} <button class="quiet-button" @click="checkDeepgram">다시 확인</button></p><p class="muted">한국어 Nova-3로 음성을 계속 보내고, 중간 문장을 수정한 뒤 확정해요. 영어 약어·제품명이 섞인 발화의 정확도는 검증 중이에요. 사용량에 따라 비용이 발생할 수 있으며 Groq로 자동 전환하지 않아요.</p><label class="check"><input v-model="consent" type="checkbox">참석자에게 안내했고, 이번 전사의 음성을 Deepgram으로 보내는 데 동의해요.</label></template>
    <p v-if="mode==='local'" class="muted">{{healthMessage}} <button class="quiet-button" :disabled="checking" @click="checkHealth">다시 확인</button></p>
    <template v-if="mode==='local'"><label for="speech-chunk">음성 구간 처리</label><select id="speech-chunk" v-model="chunkSeconds" :disabled="running"><option :value="4">빠른 표시 · 최대 4초</option><option :value="8">문맥 유지 비교 · 최대 8초</option></select><p class="muted">문맥 유지 모드는 말하는 중간에 잘리는 횟수를 줄이지만 첫 표시가 늦어질 수 있어요. 정확도 개선은 실제 발화로 비교해야 해요.</p></template>
    <p v-if="mode!=='deepgram'" class="muted">{{mode==='local'?`선택한 마이크의 음성을 ${providerName}에서 처리해요. 짧은 쉼이나 최대 약 ${chunkSeconds}초 단위로 나눠 보내며, 문장은 서버 응답 후 표시돼요. 요청 간격과 처리 시간에 따라 더 늦어질 수 있고, 단어 단위 실시간 전사는 아니에요.`:'브라우저 인식 서비스로 음성이 전송될 수 있어요. 장치 선택은 지원하지 않으며, 네트워크 오류가 생기면 직접 입력을 이용해 주세요.'}}</p>
    <template v-if="mode==='local'"><label class="check"><input v-model="processing" type="checkbox" :disabled="running||mic.status==='requesting'">잡음과 스피커 울림 줄이기</label><p class="muted">{{processingStatus}} · 브라우저와 장치에 따라 지원이 달라요. 목소리가 잘리면 꺼서 비교해 보세요.</p></template>
    <p v-if="mode==='local'&&provider==='azure'" class="muted">실제 Azure 리소스가 F0여야 무료 한도가 적용돼요. 한도 초과 시 중단하며 유료 전사로 자동 전환하지 않아요.</p>
    <p v-if="mode==='local'&&provider==='groq'" class="muted">무료 플랜 여부와 남은 한도는 Groq 콘솔에서 확인해 주세요. 요청 제한 시 전사를 멈추며 다른 업체로 자동 전환하지 않아요.</p>
    <label v-if="mode!=='deepgram'" class="check"><input v-model="consent" type="checkbox">{{mode==='local'&&external?`참석자에게 안내했고, 이번 전사의 음성을 ${provider==='groq'?'Groq':'Microsoft Azure'}로 보내는 데 동의해요.`:'참석자에게 안내했고, 이번 전사의 음성 처리에 동의해요.'}}</label>
    <div class="mic-check"><label for="microphone-device">입력 마이크</label><select id="microphone-device" v-model="device" :disabled="running||mic.status==='requesting'||mode==='browser'"><option value="">시스템 기본 마이크</option><option v-for="(input,index) in devices" :key="input.deviceId" :value="input.deviceId">{{input.label||'마이크 '+(index+1)}}</option></select><p class="muted">마이크 테스트를 허용하면 장치 이름이 표시돼요. 테스트 음성은 전송하지 않아요.</p><div class="section-head"><span>{{selectedLabel}}</span><span role="status">{{mic.status==='ready'?'입력 확인 중':mic.status==='requesting'?'권한을 허용해 주세요':'아직 연결하지 않음'}}</span></div><meter min="0" max="100" :value="mic.level" aria-label="마이크 입력 크기"/><button class="quiet-button" :disabled="running||mic.status==='requesting'" @click="start(true)">마이크 테스트</button></div>
    <p v-if="mic.error||state.error" role="alert" class="notice error">{{mic.error||state.error}}</p>
    <div class="dialog-actions"><button class="secondary" @click="closeSetup">취소</button><button :disabled="!consent||!canStart||mic.status==='requesting'||running" @click="start()">전사 시작</button></div>
  </AppDialog>
</template>
<style scoped>
.transcription-live{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 0;color:var(--muted,#575a60);font-size:14px}
.transcription-live meter{width:80px;flex-shrink:0}
.speech-diagnostics{padding:12px 0;font-size:14px;line-height:1.6}.speech-diagnostics summary{cursor:pointer}.speech-diagnostics p{margin:8px 0}
@media(max-width:600px){.transcription-live{align-items:flex-start}.transcription-live span{max-width:32ch}}
</style>
