import {describe,it,expect} from 'vitest'
import {readTextDocument} from './DocumentImport'
const file=(name:string,content:string,size=content.length)=>({name,size,text:async()=>content}) as File
describe('document import',()=>{
  it('prepares a text preview without uploading',async()=>expect(await readTextDocument(file('회의.md','검토할 내용'))).toEqual({title:'회의',content:'검토할 내용'}))
  it('rejects unsupported files',async()=>expect(readTextDocument(file('meeting.pdf','text'))).rejects.toThrow('.txt'))
  it('rejects large and invalid files',async()=>{await expect(readTextDocument(file('a.txt','text',80001))).rejects.toThrow('80KB');await expect(readTextDocument(file('a.txt','\u0000'))).rejects.toThrow('UTF-8')})
})
