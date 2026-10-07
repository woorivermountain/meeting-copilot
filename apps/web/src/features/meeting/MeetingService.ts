import { api } from '../../shared/api/ApiClient'
export interface Meeting { id:string;teamId:string;title:string;status:'OPEN'|'ENDED';revision:number;createdAt:string }
export interface Segment { id:string;receivedAt:string;text:string }
export interface Transcript { version:number;segments:Segment[] }
export interface Revision { version:number;approvedBy:string;createdAt:string }
export class MeetingService {
  list(team:string){return api.request<Meeting[]>(`/teams/${team}/meetings`)}
  create(team:string,title:string){return api.request<Meeting>(`/teams/${team}/meetings`,'POST',{title})}
  get(id:string){return api.request<Meeting>(`/meetings/${id}`)}
  end(id:string){return api.request<Meeting>(`/meetings/${id}/end`,'POST')}
  transcript(id:string,version?:number){return api.request<Transcript>(`/meetings/${id}/transcript${version===undefined?'':`?version=${version}`}`)}
  save(id:string,version:number,segments:Segment[]){return api.request<Transcript>(`/meetings/${id}/transcript`,'PUT',{version,segments,approved:true})}
  history(id:string){return api.request<Revision[]>(`/meetings/${id}/transcript/history`)}
}
export const meetingService=new MeetingService()
