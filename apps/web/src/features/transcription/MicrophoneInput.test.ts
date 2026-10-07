import {afterEach,describe,it,expect,vi} from 'vitest'
import {MicrophoneInput} from './MicrophoneInput'
import {wav,LocalSpeechSession,type SpeechProvider} from './LocalSpeechSession'
afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals()})
function environment(){
  const track={stop:vi.fn(),onended:null as null|(()=>void)};const stream={getTracks:()=>[track]}
  const getUserMedia=vi.fn().mockResolvedValue(stream)
  vi.stubGlobal('window',{isSecureContext:true});vi.stubGlobal('navigator',{mediaDevices:{getUserMedia}});vi.stubGlobal('cancelAnimationFrame',vi.fn());vi.stubGlobal('requestAnimationFrame',vi.fn(()=>1))
  vi.stubGlobal('AudioContext',class {sampleRate=48000;destination={};resume=vi.fn().mockResolvedValue(undefined);close=vi.fn().mockResolvedValue(undefined);createMediaStreamSource=vi.fn(()=>({connect:vi.fn(),disconnect:vi.fn()}));createAnalyser(){return {fftSize:512,getFloatTimeDomainData:vi.fn()}}})
  return {track,stream,getUserMedia}
}
describe('selected microphone',()=>{
  it('requests supported processing as preferences and reports actual settings',async()=>{
    const {stream,getUserMedia,track}=environment()
    Object.assign(navigator.mediaDevices,{getSupportedConstraints:()=>({noiseSuppression:true,echoCancellation:true,autoGainControl:true})})
    Object.assign(track,{getSettings:()=>({noiseSuppression:true,echoCancellation:false,autoGainControl:false})});Object.assign(stream,{getAudioTracks:()=>[track]})
    const mic=new MicrophoneInput(vi.fn());await mic.start('usb')
    expect(getUserMedia).toHaveBeenCalledWith({audio:{deviceId:{exact:'usb'},noiseSuppression:{ideal:true},echoCancellation:{ideal:true},autoGainControl:{ideal:false}},video:false})
    expect(mic.audioProcessing).toEqual({noiseSuppression:true,echoCancellation:false,autoGainControl:false});mic.stop()
    await mic.start('',false);expect(getUserMedia.mock.lastCall![0].audio).toEqual({noiseSuppression:{ideal:false},echoCancellation:{ideal:false},autoGainControl:{ideal:false}});mic.stop()
  })
  it('omits unsupported processing and does not report it as active',async()=>{
    const {stream,getUserMedia,track}=environment();Object.assign(stream,{getAudioTracks:()=>[track]})
    Object.assign(navigator.mediaDevices,{getSupportedConstraints:()=>({})});const mic=new MicrophoneInput(vi.fn());await mic.start()
    expect(getUserMedia).toHaveBeenCalledWith({audio:true,video:false});expect(mic.audioProcessing.noiseSuppression).toBeUndefined();mic.stop()
  })
  it('opens the exact device and exposes the same stream for transcription',async()=>{const {stream,getUserMedia}=environment();const mic=new MicrophoneInput(vi.fn());expect(await mic.start('usb-mic')).toBe(true);expect(getUserMedia).toHaveBeenCalledWith({audio:{deviceId:{exact:'usb-mic'}},video:false});expect(mic.mediaStream).toBe(stream);mic.stop()})
  it('stops a stream returned after permission request was cancelled',async()=>{const {stream,track,getUserMedia}=environment();let resolve!:(stream:unknown)=>void;getUserMedia.mockImplementation(()=>new Promise(r=>resolve=r));const mic=new MicrophoneInput(vi.fn());const pending=mic.start('usb');mic.stop();resolve(stream);expect(await pending).toBe(false);expect(track.stop).toHaveBeenCalled();expect(mic.mediaStream).toBeUndefined()})
  it('releases input and reports device disconnection',async()=>{const {track}=environment();const change=vi.fn();const mic=new MicrophoneInput(change);await mic.start('usb');track.onended!();expect(track.stop).toHaveBeenCalled();expect(change.mock.lastCall![0].status).toBe('error')})
  it('never silently falls back when exact device is unavailable',async()=>{const {getUserMedia}=environment();getUserMedia.mockRejectedValue(Object.assign(new Error(),{name:'OverconstrainedError'}));const change=vi.fn();expect(await new MicrophoneInput(change).start('missing')).toBe(false);expect(getUserMedia).toHaveBeenCalledTimes(1);expect(change.mock.lastCall![0].error).toContain('다시 선택')})
})
describe('selected-provider transcription',()=>{
  it.each<SpeechProvider>(['local','azure','groq'])('uploads the exact selected stream with explicit provider %s, preserves sequence, and flushes on stop',async provider=>{
    vi.useFakeTimers()
    const externalApproved=provider!=='local'
    environment();const stream={} as MediaStream,source=vi.fn(()=>({connect:vi.fn(),disconnect:vi.fn()})),processor={onaudioprocess:null as null|((e:any)=>void),connect:vi.fn(),disconnect:vi.fn()}
    vi.stubGlobal('AudioContext',class {sampleRate=16000;destination={};resume=async()=>{};close=async()=>{};createMediaStreamSource=source;createScriptProcessor=()=>processor})
    const fetchMock=vi.fn().mockResolvedValueOnce({ok:true,json:async()=>({headerName:'csrf',token:'test'})}).mockResolvedValueOnce({ok:true,json:async()=>({text:'첫 문장',sequence:0})}).mockResolvedValueOnce({ok:true,json:async()=>({text:'마지막 문장',sequence:1})});vi.stubGlobal('fetch',fetchMock)
    const final=vi.fn(),session=new LocalSpeechSession('team',vi.fn(),final,externalApproved,provider);await session.start(stream);expect(source).toHaveBeenCalledWith(stream)
    processor.onaudioprocess!({inputBuffer:{getChannelData:()=>new Float32Array(64000).fill(.1)}});await vi.advanceTimersByTimeAsync(0);expect(final).toHaveBeenCalledWith('첫 문장')
    processor.onaudioprocess!({inputBuffer:{getChannelData:()=>new Float32Array(16000).fill(.1)}});session.stop();await vi.advanceTimersByTimeAsync(3999);expect(final).toHaveBeenCalledTimes(1);await vi.advanceTimersByTimeAsync(1);expect(final).toHaveBeenCalledWith('마지막 문장')
    expect(fetchMock.mock.calls[1]![1].headers['X-Speech-Sequence']).toBe('0');expect(fetchMock.mock.calls[2]![1].headers['X-Speech-Sequence']).toBe('1');expect(fetchMock.mock.calls[1]![1].headers['X-Speech-External-Approved']).toBe(String(externalApproved));expect(fetchMock.mock.calls[1]![1].headers['X-Speech-Provider']).toBe(provider);expect(processor.disconnect).toHaveBeenCalled();session.dispose()
  })
  it('stops on Groq rate limit without retrying or changing providers',async()=>{
    environment();const processor={onaudioprocess:null as null|((e:any)=>void),connect:vi.fn(),disconnect:vi.fn()}
    vi.stubGlobal('AudioContext',class {sampleRate=16000;destination={};resume=async()=>{};close=async()=>{};createMediaStreamSource=()=>({connect:vi.fn(),disconnect:vi.fn()});createScriptProcessor=()=>processor})
    const fetchMock=vi.fn().mockResolvedValueOnce({ok:true,json:async()=>({headerName:'csrf',token:'test'})}).mockResolvedValueOnce({ok:false,status:429,json:async()=>({message:'Groq 요청 한도에 도달했어요.'})});vi.stubGlobal('fetch',fetchMock)
    const final=vi.fn(),change=vi.fn(),session=new LocalSpeechSession('team',change,final,true,'groq');await session.start({} as MediaStream)
    processor.onaudioprocess!({inputBuffer:{getChannelData:()=>new Float32Array(128000).fill(.1)}})
    await vi.waitFor(()=>expect(change.mock.lastCall![0].status).toBe('error'))
    expect(change.mock.lastCall![0].error).toContain('Groq');expect(fetchMock).toHaveBeenCalledTimes(2);expect(final).not.toHaveBeenCalled();expect(processor.disconnect).toHaveBeenCalled()
  })
  it('encodes mono PCM WAV at 16kHz',()=>{const output=wav(new Float32Array(48000).fill(.5),48000),data=new DataView(output);expect(output.byteLength).toBe(32044);expect(data.getUint32(24,true)).toBe(16000);expect(data.getInt16(44,true)).toBe(16383)})
  it('cancels setup without opening an audio context or delivering text',async()=>{environment();let resolve!:(value:unknown)=>void;vi.stubGlobal('fetch',vi.fn(()=>new Promise(r=>resolve=r)));const final=vi.fn(),session=new LocalSpeechSession('team',vi.fn(),final);const pending=session.start({} as MediaStream);session.dispose();resolve({ok:true,json:async()=>({headerName:'csrf',token:'test'})});await pending;expect(final).not.toHaveBeenCalled()})
})
