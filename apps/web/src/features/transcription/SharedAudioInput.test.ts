import {afterEach,describe,expect,it,vi} from 'vitest'
import {bindSharedAudioLifecycle,requestSharedAudio,SharedAudioError} from './SharedAudioInput'

afterEach(()=>vi.unstubAllGlobals())
function track(kind:'audio'|'video',readyState:MediaStreamTrackState='live'){
  const listeners=new Set<()=>void>()
  return {kind,readyState,stop:vi.fn(),addEventListener:vi.fn((_name:string,listener:()=>void)=>listeners.add(listener)),removeEventListener:vi.fn((_name:string,listener:()=>void)=>listeners.delete(listener)),end:()=>listeners.forEach(listener=>listener())} as unknown as MediaStreamTrack&{end:()=>void}
}
function environment(audio=true){
  const audioTrack=track('audio'),videoTrack=track('video'),tracks=audio?[audioTrack,videoTrack]:[videoTrack]
  const stream={getAudioTracks:()=>tracks.filter(value=>value.kind==='audio'),getTracks:()=>tracks} as unknown as MediaStream
  const getDisplayMedia=vi.fn().mockResolvedValue(stream);vi.stubGlobal('window',{isSecureContext:true});vi.stubGlobal('navigator',{mediaDevices:{getDisplayMedia}})
  return {audioTrack,videoTrack,stream,getDisplayMedia}
}

describe('explicit shared-tab audio capture',()=>{
  it('requests user-selected audio while excluding the current app tab',async()=>{
    const t=environment();expect(await requestSharedAudio()).toBe(t.stream)
    expect(t.getDisplayMedia).toHaveBeenCalledWith(expect.objectContaining({audio:{suppressLocalAudioPlayback:false},selfBrowserSurface:'exclude',systemAudio:'include',surfaceSwitching:'include'}))
    expect(t.audioTrack.stop).not.toHaveBeenCalled();expect(t.videoTrack.stop).not.toHaveBeenCalled()
  })
  it('stops every returned track when the selected surface has no audio',async()=>{
    const t=environment(false);await expect(requestSharedAudio()).rejects.toMatchObject({code:'NO_AUDIO'});expect(t.videoTrack.stop).toHaveBeenCalledOnce()
  })
  it('maps user cancellation without retrying or falling back to the microphone',async()=>{
    const t=environment();t.getDisplayMedia.mockRejectedValue(Object.assign(new Error(),{name:'NotAllowedError'}))
    await expect(requestSharedAudio()).rejects.toEqual(expect.objectContaining<Partial<SharedAudioError>>({code:'DENIED'}));expect(t.getDisplayMedia).toHaveBeenCalledTimes(1)
  })
  it('fails closed outside a secure supported browser',async()=>{
    vi.stubGlobal('window',{isSecureContext:false});vi.stubGlobal('navigator',{mediaDevices:{}})
    await expect(requestSharedAudio()).rejects.toMatchObject({code:'UNSUPPORTED'})
  })
  it('stops the complete capture and reports a browser-ended share once',()=>{
    const t=environment(),ended=vi.fn(),dispose=bindSharedAudioLifecycle(t.stream,ended)
    ;(t.videoTrack as MediaStreamTrack&{end:()=>void}).end();dispose()
    expect(ended).toHaveBeenCalledOnce();expect(t.audioTrack.stop).toHaveBeenCalledOnce();expect(t.videoTrack.stop).toHaveBeenCalledOnce()
  })
  it('stops a caller-ended share without reporting an unexpected end',()=>{
    const t=environment(),ended=vi.fn(),dispose=bindSharedAudioLifecycle(t.stream,ended)
    dispose();dispose()
    expect(ended).not.toHaveBeenCalled();expect(t.audioTrack.stop).toHaveBeenCalledOnce();expect(t.videoTrack.stop).toHaveBeenCalledOnce()
  })
})
