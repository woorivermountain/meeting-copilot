import {describe,it,expect,vi,afterEach} from 'vitest'
import {SpeechSession,type RecognitionEngine,type SpeechState} from './SpeechSession'
function setup(){const engine:RecognitionEngine={lang:'',continuous:false,interimResults:false,onstart:null,onend:null,onaudiostart:null,onaudioend:null,onerror:null,onresult:null,start:vi.fn(),stop:vi.fn(),abort:vi.fn()};let state:SpeechState|undefined;const final=vi.fn();const session=new SpeechSession(engine,s=>state=s,final);return{engine,session,final,state:()=>state}}
afterEach(()=>vi.useRealTimers())
describe('SpeechSession',()=>{
it('does not show listening before actual engine start',()=>{vi.useFakeTimers();const t=setup();t.session.start();expect(t.state()?.status).toBe('starting');t.engine.onstart?.();expect(t.state()?.status).toBe('listening');t.session.dispose()})
it('permission denied stops safely',()=>{vi.useFakeTimers();const t=setup();t.session.start();t.engine.onerror?.({error:'not-allowed'});expect(t.state()?.status).toBe('error');expect(t.state()?.error).toContain('권한');vi.runAllTimers();expect(t.engine.start).toHaveBeenCalledTimes(1);t.session.dispose()})
it('delivers final only once and never promotes interim',()=>{vi.useFakeTimers();const t=setup();t.session.start();t.engine.onstart?.();t.engine.onresult?.({resultIndex:0,results:[{isFinal:false,0:{transcript:'검토'}}]});expect(t.final).not.toHaveBeenCalled();const result={resultIndex:0,results:[{isFinal:true,0:{transcript:'검토합니다'}}]};t.engine.onresult?.(result);t.engine.onresult?.(result);expect(t.final).toHaveBeenCalledExactlyOnceWith('검토합니다');t.session.dispose()})
it('start timeout is actionable',()=>{vi.useFakeTimers();const t=setup();t.session.start();vi.advanceTimersByTime(12000);expect(t.state()?.status).toBe('error');t.session.dispose()})
it('stops after bounded reconnect attempts',()=>{vi.useFakeTimers();const t=setup();t.session.start();for(let i=0;i<4;i++){t.engine.onstart?.();t.engine.onend?.();vi.advanceTimersByTime(2500)}expect(t.state()?.status).toBe('error');expect(t.engine.start).toHaveBeenCalledTimes(4);t.session.dispose()})
it('disposal removes callbacks and timers',()=>{vi.useFakeTimers();const t=setup();t.session.start();t.session.dispose();expect(t.engine.onresult).toBeNull();expect(vi.getTimerCount()).toBe(0)})
})
