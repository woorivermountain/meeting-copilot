import {api} from '../../shared/api/ApiClient'
import type {SpeechState} from './SpeechSession'
import {wav} from './LocalSpeechSession'

export class DeepgramSpeechSession {
  private socket?:WebSocket;private audio?:AudioContext;private source?:MediaStreamAudioSourceNode;private processor?:ScriptProcessorNode
  private disposed=false;private stopping=false;private finalized=new Set<number>();private timer?:ReturnType<typeof setTimeout>
  constructor(private team:string,private change:(value:SpeechState)=>void,private final:(text:string)=>void){}
  private emit(status:SpeechState['status'],interim='',error=''){if(!this.disposed)this.change({status,interim,error,audio:status==='listening'})}
  async start(stream:MediaStream){
    this.emit('starting');this.timer=setTimeout(()=>this.fail('Deepgram 연결 시간이 초과됐어요. 다시 시작해 주세요.'),15000)
    try{
      const {accessToken}=await api.request<{accessToken:string}>(`/teams/${this.team}/speech/deepgram/token`,'POST',{externalApproved:true});if(this.disposed)return
      const params=new URLSearchParams({model:'nova-3',language:'ko',encoding:'linear16',sample_rate:'16000',channels:'1',interim_results:'true',punctuate:'true',endpointing:'600',mip_opt_out:'true'})
      const socket=this.socket=new WebSocket('wss://api.deepgram.com/v1/listen?'+params,['bearer',accessToken]);
      socket.onopen=async()=>{try{if(this.disposed)return;clearTimeout(this.timer);this.audio=new AudioContext();await this.audio.resume();if(this.disposed)return;this.source=this.audio.createMediaStreamSource(stream);this.processor=this.audio.createScriptProcessor(4096,1,1);this.processor.onaudioprocess=e=>{if(this.stopping||this.disposed)return;if(socket.bufferedAmount>256000){this.fail('음성 전송이 밀리고 있어요. 연결을 확인하고 다시 시작해 주세요.');return}socket.send(wav(e.inputBuffer.getChannelData(0),this.audio!.sampleRate).slice(44))};this.source.connect(this.processor);this.processor.connect(this.audio.destination);this.emit('listening')}catch{this.fail('마이크 음성을 전송하지 못했어요. 다시 시작해 주세요.')}}
      socket.onmessage=e=>{if(this.disposed)return;try{const data=JSON.parse(e.data);if(data.type==='Error'){this.fail('Deepgram이 전사를 처리하지 못했어요. 설정과 한도를 확인해 주세요.');return}if(data.type!=='Results')return;const text=data.channel?.alternatives?.[0]?.transcript;if(typeof text!=='string'||text.length>4000)return;if(data.is_final){if(text&&Number.isFinite(data.start)&&!this.finalized.has(data.start)){this.finalized.add(data.start);this.final(text)}this.emit(this.stopping?'stopping':'listening')}else if(!this.stopping&&!this.finalized.has(data.start))this.emit('listening',text)}catch{this.fail('전사 응답 형식을 확인하지 못했어요. 다시 연결해 주세요.')}}
      socket.onerror=()=>this.fail('Deepgram 연결에 실패했어요. 키와 네트워크를 확인해 주세요.')
      socket.onclose=()=>{if(this.disposed)return;if(this.stopping){this.emit('idle');this.dispose()}else this.fail('전사 연결이 끊겼어요. 미확정 문장은 저장하지 않았어요. 다시 연결해 주세요.')}
    }catch{this.fail('Deepgram 연결을 시작하지 못했어요. 서버 설정과 사용 한도를 확인해 주세요.')}
  }
  stop(){if(this.disposed||this.stopping)return;this.stopping=true;this.disconnect();clearTimeout(this.timer);if(this.socket?.readyState===WebSocket.OPEN){this.emit('stopping');this.socket.send(JSON.stringify({type:'CloseStream'}));this.timer=setTimeout(()=>this.fail('마지막 전사 응답이 지연돼 연결을 닫았어요. 미확정 내용은 직접 확인해 주세요.'),10000)}else{this.emit('idle');this.dispose()}}
  private disconnect(){if(this.processor){this.processor.onaudioprocess=null;this.processor.disconnect()}this.source?.disconnect();void this.audio?.close().catch(()=>{})}
  private fail(message:string){this.emit('error','',message);this.dispose()}
  dispose(){this.disposed=true;clearTimeout(this.timer);this.disconnect();this.socket?.close();this.finalized.clear()}
}
