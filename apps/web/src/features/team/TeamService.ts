import { api } from '../../shared/api/ApiClient'
export interface Team { id:string; name:string; role:'OWNER'|'MEMBER' }
export interface Member { id:string; name:string; email:string; role:string }
export class TeamService {
  list(){return api.request<Team[]>('/teams')}
  create(name:string){return api.request<{id:string;name:string;inviteCode:string}>('/teams','POST',{name})}
  join(code:string){return api.request<{id:string}>('/teams/join','POST',{code})}
  members(id:string){return api.request<Member[]>(`/teams/${id}/members`)}
}
export const teamService=new TeamService()
