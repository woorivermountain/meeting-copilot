import {describe,it,expect} from 'vitest'
import {needsLeaveConfirmation,leaveDescription} from './leavePolicy'
const clean={open:false,active:false,dirty:false,draft:false,busy:false}
describe('meeting navigation protection',()=>{
  it('warns for an open meeting even before microphone starts',()=>expect(needsLeaveConfirmation({...clean,open:true})).toBe(true))
  it('warns for unsaved transcription and outcome drafts',()=>{expect(needsLeaveConfirmation({...clean,dirty:true})).toBe(true);expect(needsLeaveConfirmation({...clean,draft:true})).toBe(true)})
  it('allows a completed saved meeting to leave',()=>expect(needsLeaveConfirmation(clean)).toBe(false))
  it('does not suggest a pending save can be abandoned',()=>expect(leaveDescription({...clean,busy:true})).toContain('완료한 뒤'))
})
