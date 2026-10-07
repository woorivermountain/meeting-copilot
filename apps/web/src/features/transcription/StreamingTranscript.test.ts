import {describe,it,expect} from 'vitest'
import {StreamingTranscript,type TranscriptEvent} from './StreamingTranscript'
const event=(changes:Partial<TranscriptEvent>={}):TranscriptEvent=>({epoch:1,id:'a',revision:0,text:'다음 주',final:false,startMs:0,endMs:1000,...changes})
describe('streaming transcript contract (not provider integration)',()=>{
  it('replaces interim text instead of appending duplicates and only exports final text',()=>{
    const state=new StreamingTranscript();state.begin(1);state.apply(event());expect(state.confirmed()).toEqual([])
    state.apply(event({revision:1,text:'다음 주 금요일'}));expect(state.snapshot()).toHaveLength(1)
    state.apply(event({revision:2,text:'다음 주 금요일에 배포해요.',final:true}));expect(state.confirmed()[0]?.text).toBe('다음 주 금요일에 배포해요.')
    expect(state.apply(event({revision:3,text:'다른 말'}))).toBe(false)
  })
  it('rejects duplicate and stale events',()=>{
    const state=new StreamingTranscript();state.begin(1);state.apply(event({revision:3}));expect(state.apply(event({revision:2}))).toBe(false);expect(state.apply(event({revision:3}))).toBe(false)
  })
  it('drops unconfirmed words on reconnect without losing confirmed segments',()=>{
    const state=new StreamingTranscript();state.begin(1);state.apply(event({final:true}));state.apply(event({id:'b'}));state.begin(2)
    expect(state.snapshot()).toHaveLength(1);expect(state.apply(event({id:'late'}))).toBe(false);expect(state.apply(event({epoch:2}))).toBe(true)
  })
  it('rejects invalid input and exposes defensive copies',()=>{
    const state=new StreamingTranscript();state.begin(1);expect(state.apply(event({endMs:-1}))).toBe(false);expect(state.apply(event({text:'a'.repeat(4001)}))).toBe(false)
    state.apply(event());state.snapshot()[0]!.text='mutated';expect(state.snapshot()[0]!.text).toBe('다음 주')
  })
})
