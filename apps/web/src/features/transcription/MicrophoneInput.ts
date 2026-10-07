export interface MicrophoneState{status:'idle'|'requesting'|'ready'|'error';level:number;error:string}
export class MicrophoneInput{
  private stream?:MediaStream;private audio?:AudioContext;private frame=0;private generation=0
  constructor(private change:(state:MicrophoneState)=>void){}
  stop(){this.generation++;cancelAnimationFrame(this.frame);this.stream?.getTracks().forEach(t=>{t.onended=null;t.stop()});this.stream=undefined;void this.audio?.close().catch(()=>{});this.audio=undefined;this.change({status:'idle',level:0,error:''})}
  async start(){this.stop();const current=this.generation;this.change({status:'requesting',level:0,error:''})
    try{
      if(!window.isSecureContext||!navigator.mediaDevices?.getUserMedia)throw new Error('UNSUPPORTED')
      const stream=await navigator.mediaDevices.getUserMedia({audio:true,video:false})
      if(current!==this.generation){stream.getTracks().forEach(t=>t.stop());return false}
      this.stream=stream;this.audio=new AudioContext();await this.audio.resume();if(current!==this.generation||!this.audio)return false
      const analyser=this.audio.createAnalyser();analyser.fftSize=512;this.audio.createMediaStreamSource(stream).connect(analyser);const buffer=new Float32Array(analyser.fftSize)
      const tick=()=>{if(current!==this.generation)return;analyser.getFloatTimeDomainData(buffer);const rms=Math.sqrt(buffer.reduce((s,v)=>s+v*v,0)/buffer.length);this.change({status:'ready',level:Math.min(100,Math.round(rms*500)),error:''});this.frame=requestAnimationFrame(tick)}
      stream.getTracks().forEach(t=>t.onended=()=>{this.stop();this.change({status:'error',level:0,error:'마이크 연결이 끊겼습니다. 장치를 확인하고 다시 시작하세요.'})});tick();return true
    }catch(e){if(current!==this.generation)return false;this.stop();const name=(e as Error).name;this.change({status:'error',level:0,error:name==='NotAllowedError'?'마이크 권한을 허용해 주세요. 사이트 권한과 시스템 설정을 확인하세요.':name==='NotFoundError'?'마이크가 없습니다. 입력 장치를 연결하세요.':name==='NotReadableError'?'마이크를 열지 못했습니다. 다른 앱의 사용 여부를 확인하세요.':'마이크 확인을 지원하지 않는 환경입니다. HTTPS 또는 localhost의 일반 Chrome에서 시도하세요.'});return false}
  }
}
