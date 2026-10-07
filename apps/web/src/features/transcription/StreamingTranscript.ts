/** Provider-neutral transcript state. Adapters must keep one ID per audio segment,
 * and monotonically increase revision within a connection epoch.
 * Not wired to a provider yet; no audio or network side effects.
 */
export interface TranscriptEvent {
  epoch:number
  id:string
  revision:number
  text:string
  final:boolean
  startMs:number
  endMs:number
  speaker?:string
}
export class StreamingTranscript {
  private epoch=0
  private segments=new Map<string,TranscriptEvent>()
  begin(epoch:number){
    if(!Number.isSafeInteger(epoch)||epoch<=this.epoch)throw new Error('Connection epoch must increase')
    this.epoch=epoch
    // Unconfirmed text is not silently promoted when a connection breaks.
    for(const [key,value] of this.segments)if(!value.final)this.segments.delete(key)
  }
  apply(event:TranscriptEvent):boolean{
    if(event.epoch!==this.epoch||!event.id||event.id.length>128||!Number.isSafeInteger(event.revision)||event.revision<0||typeof event.text!=='string'||event.text.length>4000||!Number.isFinite(event.startMs)||!Number.isFinite(event.endMs)||event.startMs<0||event.endMs<event.startMs)return false
    const key=`${event.epoch}:${event.id}`,previous=this.segments.get(key)
    if(previous&&(previous.final||event.revision<=previous.revision))return false
    if(!previous&&this.segments.size>=500)return false
    this.segments.set(key,{...event})
    return true
  }
  snapshot(){return [...this.segments.values()].sort((a,b)=>a.epoch-b.epoch||a.startMs-b.startMs||a.id.localeCompare(b.id)).map(value=>({...value}))}
  confirmed(){return this.snapshot().filter(value=>value.final)}
  clear(){this.segments.clear()}
}
