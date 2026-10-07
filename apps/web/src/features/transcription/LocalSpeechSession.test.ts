import {afterEach,describe,expect,it,vi} from 'vitest'
import {LocalSpeechSession} from './LocalSpeechSession'

afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals()})
async function setup(pending=false,maxSeconds:4|8=4){
  vi.useFakeTimers()
  const processor={onaudioprocess:null as null|((event:any)=>void),connect:vi.fn(),disconnect:vi.fn()}
  vi.stubGlobal('AudioContext',class {sampleRate=16000;destination={};resume=async()=>{};close=async()=>{};createMediaStreamSource=()=>({connect:vi.fn(),disconnect:vi.fn()});createScriptProcessor=()=>processor})
  let sequence=0,finish!:()=>void
  const fetchMock=vi.fn().mockResolvedValueOnce({ok:true,json:async()=>({headerName:'csrf',token:'test'})}).mockImplementation(()=>{
    const response={ok:true,json:async()=>({text:'문장',sequence:sequence++})}
    return pending?new Promise(resolve=>{finish=()=>resolve(response)}):Promise.resolve(response)
  });vi.stubGlobal('fetch',fetchMock)
  const change=vi.fn(),final=vi.fn(),diagnostic=vi.fn(),session=new LocalSpeechSession('team',change,final,true,'groq',diagnostic,maxSeconds)
  await session.start({} as MediaStream)
  const feed=(seconds:number,value=.1)=>processor.onaudioprocess?.({inputBuffer:{getChannelData:()=>new Float32Array(Math.round(16000*seconds)).fill(value)}})
  return {session,change,final,diagnostic,fetchMock,processor,feed,finish:()=>finish()}
}
describe('pause-aware serial transcription',()=>{
  it('allows longer continuous context without exceeding eight seconds and reports text-free diagnostics',async()=>{
    const t=await setup(false,8);t.feed(4);await vi.advanceTimersByTimeAsync(0);expect(t.fetchMock).toHaveBeenCalledTimes(1)
    t.feed(4);await vi.advanceTimersByTimeAsync(0);expect(t.fetchMock.mock.calls[1]![1].body.byteLength).toBe(256044)
    expect(t.diagnostic.mock.lastCall![0]).toMatchObject({audioSeconds:8,split:'limit',empty:false,clippedPercent:0,queued:0})
    expect(Object.keys(t.diagnostic.mock.lastCall![0]).sort()).toEqual(['audioSeconds','clippedPercent','empty','queueWaitMs','queued','responseMs','rms','split'].sort());t.session.dispose()
  })
  it('still flushes at a pause in context mode and reports clipped input',async()=>{
    const t=await setup(false,8);t.feed(.4,1);t.feed(.6,0);await vi.advanceTimersByTimeAsync(0)
    expect(t.diagnostic.mock.lastCall![0]).toMatchObject({audioSeconds:1,split:'pause',clippedPercent:40});t.session.dispose()
  })
  it('flushes after 600 ms silence and keeps capturing while processing',async()=>{
    const t=await setup(true);expect(t.change.mock.lastCall![0]).toMatchObject({status:'listening',phase:'capturing',audio:true})
    t.feed(.4);t.feed(.58,0);await vi.advanceTimersByTimeAsync(0);expect(t.fetchMock).toHaveBeenCalledTimes(1)
    t.feed(.02,0);await vi.advanceTimersByTimeAsync(0)
    expect(t.fetchMock).toHaveBeenCalledTimes(2);expect(t.change.mock.lastCall![0]).toMatchObject({status:'listening',phase:'processing',audio:true,interim:''})
    expect(t.fetchMock.mock.calls[1]![1].body.byteLength).toBe(32044)
    t.finish();await vi.advanceTimersByTimeAsync(0);expect(t.change.mock.lastCall![0].phase).toBe('capturing');t.session.dispose()
  })
  it('skips silence, limits continuous speech to four seconds, and paces stop tails',async()=>{
    const t=await setup();t.feed(12,0);await vi.advanceTimersByTimeAsync(0);expect(t.fetchMock).toHaveBeenCalledTimes(1)
    t.feed(4);await vi.advanceTimersByTimeAsync(0);expect(t.fetchMock.mock.calls[1]![1].body.byteLength).toBe(128044)
    t.feed(.1);t.session.stop();await vi.advanceTimersByTimeAsync(3999);expect(t.fetchMock).toHaveBeenCalledTimes(2);expect(t.change.mock.lastCall![0]).toMatchObject({status:'stopping',audio:false})
    await vi.advanceTimersByTimeAsync(1);expect(t.fetchMock).toHaveBeenCalledTimes(3);expect(t.change.mock.lastCall![0].status).toBe('idle');t.session.dispose()
  })
  it('never overlaps slow requests and bounds the pending backlog',async()=>{
    const t=await setup(true);t.feed(4);await vi.advanceTimersByTimeAsync(0)
    t.feed(12);await vi.advanceTimersByTimeAsync(4000);expect(t.fetchMock).toHaveBeenCalledTimes(2)
    t.feed(4);expect(t.change.mock.lastCall![0].status).toBe('error');expect(t.change.mock.lastCall![0].error).toContain('따라가지');expect(t.processor.disconnect).toHaveBeenCalled()
    t.finish();await vi.advanceTimersByTimeAsync(0);expect(t.final).not.toHaveBeenCalled();expect(t.fetchMock).toHaveBeenCalledTimes(2)
  })
  it('does not send a queued tail after disposal during pacing',async()=>{
    const t=await setup();t.feed(4);await vi.advanceTimersByTimeAsync(0);t.feed(.2);t.session.stop();t.session.dispose()
    await vi.advanceTimersByTimeAsync(10000);expect(t.fetchMock).toHaveBeenCalledTimes(2)
  })
})
