import {afterEach,describe,it,expect,vi} from 'vitest'
import {DeepgramSpeechSession} from './DeepgramSpeechSession'
import {api} from '../../shared/api/ApiClient'
afterEach(()=>{vi.restoreAllMocks();vi.unstubAllGlobals();vi.useRealTimers()})
describe('Deepgram pilot streaming',()=>{
  it('uses Korean and temporary bearer auth; revises partials, deduplicates final results',async()=>{
    vi.useFakeTimers();vi.spyOn(api,'request').mockResolvedValue({accessToken:'temporary-test-token'})
    let socket:any;
    vi.stubGlobal('WebSocket',class {static OPEN=1;readyState=1;bufferedAmount=0;onopen:any;onmessage:any;onerror:any;onclose:any;send=vi.fn();close=vi.fn();constructor(public url:string,public protocols:string[]){socket=this}})
    const change=vi.fn(),final=vi.fn();const session=new DeepgramSpeechSession('team',change,final);await session.start({} as MediaStream)
    expect(socket.url).toContain('language=ko');expect(socket.url).not.toContain('language=multi');expect(socket.protocols).toEqual(['bearer','temporary-test-token'])
    const result=(text:string,is_final=false)=>socket.onmessage({data:JSON.stringify({type:'Results',start:0,is_final,channel:{alternatives:[{transcript:text}]}})})
    result('API 응답');expect(final).not.toHaveBeenCalled();expect(change.mock.lastCall![0].interim).toBe('API 응답')
    result('API 응답 latency를 확인해요.',true);result('API 응답 latency를 확인해요.',true);result('오래된 중간 결과')
    expect(final).toHaveBeenCalledTimes(1);expect(change.mock.lastCall![0].interim).toBe('');session.stop();expect(socket.send).toHaveBeenCalledWith('{"type":"CloseStream"}');session.dispose()
  })
  it('does not connect when disposed while acquiring token',async()=>{
    let resolve!:(v:unknown)=>void;vi.spyOn(api,'request').mockImplementation(()=>new Promise(r=>{resolve=r}) as any)
    const Socket=vi.fn();vi.stubGlobal('WebSocket',Socket);const session=new DeepgramSpeechSession('team',vi.fn(),vi.fn());const pending=session.start({} as MediaStream);session.dispose();resolve({accessToken:'temporary'});await pending;expect(Socket).not.toHaveBeenCalled()
  })
})
