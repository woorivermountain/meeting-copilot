import type { SpeechState } from './SpeechSession'

export function wav(samples:Float32Array,rate:number):ArrayBuffer {
  const length=Math.floor(samples.length*16000/rate),buffer=new ArrayBuffer(44+length*2),view=new DataView(buffer)
  const text=(at:number,value:string)=>{for(let i=0;i<value.length;i++)view.setUint8(at+i,value.charCodeAt(i))}
  text(0,'RIFF');view.setUint32(4,36+length*2,true);text(8,'WAVE');text(12,'fmt ');view.setUint32(16,16,true);view.setUint16(20,1,true);view.setUint16(22,1,true);view.setUint32(24,16000,true);view.setUint32(28,32000,true);view.setUint16(32,2,true);view.setUint16(34,16,true);text(36,'data');view.setUint32(40,length*2,true)
  for(let i=0;i<length;i++){const from=Math.floor(i*rate/16000),to=Math.min(samples.length,Math.max(from+1,Math.floor((i+1)*rate/16000)));let sum=0;for(let j=from;j<to;j++)sum+=samples[j]!;view.setInt16(44+i*2,Math.max(-1,Math.min(1,sum/(to-from)))*32767,true)}return buffer
}

export type SpeechProvider='local'|'azure'|'groq'
export interface SpeechDiagnostic {audioSeconds:number;rms:number;clippedPercent:number;queueWaitMs:number;responseMs:number;empty:boolean;split:'pause'|'limit'|'stop';queued:number}
interface AudioChunk {body:ArrayBuffer;createdAt:number;audioSeconds:number;rms:number;clippedPercent:number;split:SpeechDiagnostic['split']}
// A bounded, serial queue keeps chunk order stable. External consent is provider-specific.
export class LocalSpeechSession {
  private audio?:AudioContext;private source?:MediaStreamAudioSourceNode;private processor?:ScriptProcessorNode
  private samples:Float32Array[]=[];private count=0;private queue:AudioChunk[]=[];private sending=false;private stopped=false;private disposed=false
  private hasSpeech=false;private silenceSamples=0;private lastRequestAt=-Infinity
  private pacingTimer?:ReturnType<typeof setTimeout>;private releasePacing?:()=>void
  private controller=new AbortController();private sequence=0;private id=crypto.randomUUID();private token?:{headerName:string;token:string}
  constructor(private team:string,private change:(state:SpeechState)=>void,private final:(text:string)=>void,private externalApproved=false,private provider:SpeechProvider='local',private diagnostic?:(value:SpeechDiagnostic)=>void,private maxSeconds:4|8=4,private context?:()=>string){}
  private emit(status:SpeechState['status'],error=''){if(!this.disposed)this.change({status,error,audio:status==='listening',interim:'',phase:status==='listening'?(this.sending?'processing':'capturing'):status==='stopping'?'processing':undefined})}
  async start(stream:MediaStream){
    this.emit('starting')
    const timeout=setTimeout(()=>this.controller.abort(),12000)
    try {
      const response=await fetch('/api/auth/csrf',{credentials:'same-origin',signal:this.controller.signal});if(!response.ok)throw new Error('로그인 상태를 확인해 주세요.');this.token=await response.json()
      if(this.disposed||this.stopped)return
      this.audio=new AudioContext();await this.audio.resume();if(this.disposed||this.stopped)return
      this.source=this.audio.createMediaStreamSource(stream);this.processor=this.audio.createScriptProcessor(4096,1,1)
      this.processor.onaudioprocess=e=>this.capture(e.inputBuffer.getChannelData(0))
      this.source.connect(this.processor);this.processor.connect(this.audio.destination);this.emit('listening')
    } catch(e){if(!this.disposed&&!this.stopped)this.fail(this.controller.signal.aborted?'전사 연결 시간이 초과됐어요. 서버 상태를 확인해 주세요.':e instanceof Error?e.message:'전사 연결을 시작하지 못했어요.')}finally{clearTimeout(timeout)}
  }
  private capture(input:Float32Array){
    if(this.stopped||this.disposed||!this.audio)return
    const rate=this.audio.sampleRate,maxSamples=Math.floor(rate*this.maxSeconds),frameSamples=Math.max(1,Math.floor(rate*.02))
    // Small analysis frames make pause detection independent of browser callback size.
    // This is an energy gate, not speaker recognition or a guarantee of noise removal.
    for(let offset=0;offset<input.length&&!this.disposed;){
      const size=Math.min(frameSamples,input.length-offset,maxSamples-this.count),part=new Float32Array(input.subarray(offset,offset+size));offset+=size
      const active=Math.sqrt(part.reduce((sum,v)=>sum+v*v,0)/part.length)>=.003
      this.samples.push(part);this.count+=part.length
      if(active){this.hasSpeech=true;this.silenceSamples=0}else this.silenceSamples+=part.length
      if(this.hasSpeech&&this.silenceSamples>=rate*.6)this.flush('pause')
      else if(this.count>=maxSamples)this.flush('limit')
    }
  }
  private flush(split:SpeechDiagnostic['split']='stop'){
    if(!this.count||!this.audio)return
    const all=new Float32Array(this.count);let offset=0;for(const part of this.samples){all.set(part,offset);offset+=part.length}const hasSpeech=this.hasSpeech;this.samples=[];this.count=0;this.hasSpeech=false;this.silenceSamples=0
    // Skip silent buffers before the backlog check: silence cannot fill the queue.
    if(!hasSpeech)return
    if(this.queue.length>=3){this.fail('전사 처리가 입력을 따라가지 못하고 있어요. 잠시 후 다시 시작해 주세요.');return}
    let energy=0,clipped=0;for(const value of all){energy+=value*value;if(Math.abs(value)>=.99)clipped++}
    this.queue.push({body:wav(all,this.audio.sampleRate),createdAt:performance.now(),audioSeconds:all.length/this.audio.sampleRate,rms:Math.sqrt(energy/all.length),clippedPercent:clipped/all.length*100,split});void this.drain()
  }
  private async pace(){
    const delay=Math.max(0,4000-(performance.now()-this.lastRequestAt))
    if(delay)await new Promise<void>(resolve=>{this.releasePacing=resolve;this.pacingTimer=setTimeout(()=>{this.pacingTimer=undefined;this.releasePacing=undefined;resolve()},delay)})
  }
  private async drain(){if(this.sending||this.disposed)return;this.sending=true;this.emit(this.stopped?'stopping':'listening')
    try{while(this.queue.length&&!this.disposed){await this.pace();if(this.disposed)break;const chunk=this.queue.shift()!,body=chunk.body,sequence=this.sequence++;this.lastRequestAt=performance.now();const startedAt=this.lastRequestAt;const timeout=setTimeout(()=>this.controller.abort(),45000)
      try{const headers:Record<string,string>={'Content-Type':'audio/wav','X-Speech-Session':this.id,'X-Speech-Sequence':String(sequence),'X-Speech-External-Approved':String(this.externalApproved),'X-Speech-Provider':this.provider,[this.token!.headerName]:this.token!.token};const prompt=this.provider==='groq'?this.context?.().trim().slice(0,360):'';if(prompt){const bytes=new TextEncoder().encode(prompt);headers['X-Speech-Context-B64']=btoa(String.fromCharCode(...bytes))}const response=await fetch(`/api/teams/${encodeURIComponent(this.team)}/speech/transcribe`,{method:'POST',credentials:'same-origin',signal:this.controller.signal,headers,body})
        const data=await response.json();if(!response.ok)throw new Error(data.message||'전사 서버에 연결하지 못했어요. 설정을 확인해 주세요.');if(data.sequence!==sequence)throw new Error('전사 응답 순서가 달라졌어요. 다시 시작해 주세요.');if(!this.disposed){this.diagnostic?.({audioSeconds:chunk.audioSeconds,rms:chunk.rms,clippedPercent:chunk.clippedPercent,split:chunk.split,queueWaitMs:startedAt-chunk.createdAt,responseMs:performance.now()-startedAt,empty:!data.text?.trim(),queued:this.queue.length});if(data.text)this.final(data.text)}
      }finally{clearTimeout(timeout)}
    }}catch(e){if(!this.disposed)this.fail(this.controller.signal.aborted?'전사 응답 시간이 초과됐어요. 서버 상태를 확인해 주세요.':e instanceof Error?e.message:'전사 연결이 끊겼어요. 다시 시작해 주세요.')}
    finally{this.sending=false;if(!this.disposed)this.emit(this.stopped?'idle':'listening')}
  }
  stop(){if(this.disposed||this.stopped)return;this.stopped=true;if(!this.audio)this.controller.abort();this.flush();this.disconnect();this.emit(this.sending||this.queue.length?'stopping':'idle')}
  private disconnect(){this.processor?.disconnect();if(this.processor)this.processor.onaudioprocess=null;this.source?.disconnect();void this.audio?.close().catch(()=>{})}
  private fail(message:string){this.emit('error',message);this.dispose()}
  dispose(){this.disposed=true;this.stopped=true;this.controller.abort();clearTimeout(this.pacingTimer);this.releasePacing?.();this.releasePacing=undefined;this.disconnect();this.samples=[];this.queue=[];this.count=0}
}
