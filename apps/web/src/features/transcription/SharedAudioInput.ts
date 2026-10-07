export type SharedAudioFailure='UNSUPPORTED'|'DENIED'|'NO_SOURCE'|'NO_AUDIO'|'UNAVAILABLE'

export class SharedAudioError extends Error {
  constructor(public code:SharedAudioFailure,message:string){super(message);this.name='SharedAudioError'}
}

type DisplayCaptureOptions=Omit<DisplayMediaStreamOptions,'audio'>&{
  audio?:boolean|(MediaTrackConstraints&{suppressLocalAudioPlayback?:boolean})
  preferCurrentTab?:boolean
  selfBrowserSurface?:'include'|'exclude'
  surfaceSwitching?:'include'|'exclude'
  systemAudio?:'include'|'exclude'
  monitorTypeSurfaces?:'include'|'exclude'
}

/**
 * Requests an explicitly user-selected display surface and verifies that the
 * browser actually returned audio. The video track is required by the Web API,
 * but callers must never read or upload it and must stop every track on exit.
 */
export async function requestSharedAudio(devices:MediaDevices=navigator.mediaDevices):Promise<MediaStream>{
  if(!window.isSecureContext||typeof devices?.getDisplayMedia!=='function')throw new SharedAudioError('UNSUPPORTED','이 브라우저에서는 공유한 탭 소리를 받을 수 없어요. 최신 Chrome의 HTTPS 또는 localhost에서 시도해 주세요.')
  const options:DisplayCaptureOptions={video:{displaySurface:'browser'},audio:{suppressLocalAudioPlayback:false},preferCurrentTab:false,selfBrowserSurface:'exclude',surfaceSwitching:'include',systemAudio:'include',monitorTypeSurfaces:'include'}
  let stream:MediaStream
  try{stream=await devices.getDisplayMedia(options)}catch(error){
    const name=(error as Error).name
    if(name==='NotAllowedError')throw new SharedAudioError('DENIED','탭 소리 공유가 취소되었어요. 공유할 탭과 오디오 공유를 선택해 주세요.')
    if(name==='NotFoundError')throw new SharedAudioError('NO_SOURCE','공유할 탭이나 화면을 찾지 못했어요.')
    throw new SharedAudioError('UNAVAILABLE','공유한 소리를 열지 못했어요. 브라우저와 macOS 화면 기록 권한을 확인해 주세요.')
  }
  const audio=stream.getAudioTracks()[0]
  if(!audio||audio.readyState==='ended'){
    stream.getTracks().forEach(track=>track.stop())
    throw new SharedAudioError('NO_AUDIO','선택한 화면에서 오디오를 받지 못했어요. 가능하면 Chrome 탭을 고르고 ‘탭 오디오 공유’를 켜 주세요.')
  }
  return stream
}

/**
 * Owns the complete display-capture stream. Ending either returned track means
 * the selected surface is no longer a valid audio source, so every track is
 * stopped and the caller is notified exactly once.
 */
export function bindSharedAudioLifecycle(stream:MediaStream,onEnded:()=>void):()=>void{
  const tracks=stream.getTracks();let finished=false
  const cleanup=(notify:boolean)=>{if(finished)return;finished=true;tracks.forEach(track=>track.removeEventListener('ended',ended));tracks.forEach(track=>track.stop());if(notify)onEnded()}
  const ended=()=>cleanup(true)
  tracks.forEach(track=>track.addEventListener('ended',ended,{once:true}))
  return ()=>cleanup(false)
}
