import { api } from '../../shared/api/ApiClient'
export interface Outcome{id:string;kind:'DECISION'|'ACTION'|'ISSUE';text:string;ownerId:string|null;dueDate:string|null;status:'TODO'|'DOING'|'DONE';version:number}
export class OutcomeService{
  list(meeting:string){return api.request<Outcome[]>(`/meetings/${meeting}/outcomes`)}
  create(meeting:string,input:{kind:string;text:string;ownerId:string|null;dueDate:string|null}){return api.request(`/meetings/${meeting}/outcomes`,'POST',{...input,approved:true})}
  status(meeting:string,item:Outcome,status:string){return api.request<Outcome[]>(`/meetings/${meeting}/outcomes/${item.id}`,'PATCH',{status,version:item.version})}
}
export const outcomeService=new OutcomeService()
