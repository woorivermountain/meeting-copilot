export type SpeechStatus='idle'|'starting'|'listening'|'reconnecting'|'stopping'|'error'|'unsupported'
export interface SpeechState{status:SpeechStatus;interim:string;error:string;audio:boolean}
export interface RecognitionEngine{
  lang:string;continuous:boolean;interimResults:boolean
  onstart:(()=>void)|null;onend:(()=>void)|null;onaudiostart:(()=>void)|null;onaudioend:(()=>void)|null
  onerror:((event:{error:string})=>void)|null
  onresult:((event:{resultIndex:number;results:ArrayLike<{isFinal:boolean;0:{transcript:string}}>})=>void)|null
  start():void;stop():void;abort():void
}
const errors:Record<string,string>={
  'not-allowed':'마이크 권한이 차단되었습니다. 주소창의 사이트 권한과 시스템 마이크 권한을 확인하세요.',
  'service-not-allowed':'이 브라우저는 음성 인식 서비스를 허용하지 않습니다. 일반 Chrome 창에서 같은 주소를 열어 주세요.',
  'audio-capture':'마이크 입력을 열지 못했습니다. 장치 연결과 기본 마이크 설정을 확인하세요.',
  network:'음성 인식 서비스 연결에 실패했습니다. 인터넷 연결을 확인하고 일반 Chrome에서 다시 시도하세요.',
  'language-not-supported':'이 환경에서 한국어 인식을 지원하지 않습니다. 다른 브라우저에서 시도하세요.',
  timeout:'음성 인식이 시작되지 않았습니다. 권한 팝업을 확인하고 다시 시작하세요.',
  'retry-limit':'연결이 반복 종료되었습니다. 마이크와 인터넷 연결을 확인한 뒤 다시 시작하세요.',
  aborted:'음성 인식이 중단되었습니다. 다시 시작할 수 있습니다.'
}
export class SpeechSession{
  private state:SpeechState={status:'idle',interim:'',error:'',audio:false}
  private wanted=false;private disposed=false;private attempts=0;private delivered=new Set<number>();private timer:ReturnType<typeof setTimeout>|undefined
  constructor(private engine:RecognitionEngine,private change:(state:SpeechState)=>void,private final:(text:string)=>void){
    engine.lang='ko-KR';engine.continuous=true;engine.interimResults=true
    engine.onstart=()=>{if(!this.wanted||this.disposed){this.abort();return}this.clear();this.emit({status:'listening',error:''})}
    engine.onaudiostart=()=>{if(this.wanted)this.emit({audio:true})};engine.onaudioend=()=>this.emit({audio:false})
    engine.onresult=e=>{
      if(this.disposed||!['listening','stopping'].includes(this.state.status))return
      let draft=''
      for(let i=e.resultIndex;i<e.results.length;i++){const result=e.results[i],text=result?.[0]?.transcript.trim();if(!text)continue
        if(result.isFinal){if(!this.delivered.has(i)){this.delivered.add(i);this.attempts=0;this.final(text)}}else draft+=text+' '
      }this.emit({interim:draft.trim()})
    }
    engine.onerror=e=>{if(this.disposed||['error','idle','stopping'].includes(this.state.status))return;if(e.error==='no-speech'&&this.wanted){this.emit({error:'음성이 감지되지 않아 입력을 다시 연결합니다.'});return}this.fail(e.error)}
    engine.onend=()=>{this.clear();this.emit({interim:'',audio:false});if(this.disposed||this.state.status==='error')return;if(!this.wanted){this.emit({status:'idle'});return}if(++this.attempts>3){this.fail('retry-limit');return}this.emit({status:'reconnecting'});this.timer=setTimeout(()=>{if(this.wanted&&!this.disposed)this.begin()},this.attempts*800)}
  }
  private emit(patch:Partial<SpeechState>){this.state={...this.state,...patch};if(!this.disposed)this.change({...this.state})}
  private clear(){clearTimeout(this.timer);this.timer=undefined}
  private abort(){try{this.engine.abort()}catch{}}
  private fail(code:string){this.wanted=false;this.clear();this.emit({status:'error',error:errors[code]||`음성 인식 오류(${code}). 다시 시작하거나 수동 입력을 이용하세요.`,interim:'',audio:false});this.abort()}
  private begin(){this.delivered=new Set();this.emit({interim:'',audio:false});this.timer=setTimeout(()=>this.fail('timeout'),12000);try{this.engine.start()}catch{this.fail('aborted')}}
  start(){if(this.disposed||['starting','listening','reconnecting','stopping'].includes(this.state.status))return;this.wanted=true;this.attempts=0;this.clear();this.emit({status:'starting',error:''});this.begin()}
  stop(){this.wanted=false;this.clear();if(['idle','error','unsupported'].includes(this.state.status))return;if(this.state.status!=='listening'){this.emit({status:'idle',interim:'',audio:false});this.abort();return}this.emit({status:'stopping'});this.timer=setTimeout(()=>{this.emit({status:'idle',interim:'',audio:false});this.abort()},2000);try{this.engine.stop()}catch{this.emit({status:'idle',audio:false});this.abort()}}
  dispose(){this.disposed=true;this.wanted=false;this.clear();this.engine.onstart=this.engine.onend=this.engine.onaudiostart=this.engine.onaudioend=this.engine.onerror=this.engine.onresult=null;this.abort()}
}
