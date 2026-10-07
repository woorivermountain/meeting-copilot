import {api} from '../../shared/api/ApiClient'
import type {Segment} from './MeetingService'
export interface AssistantResult {
  answer:string;groundedAnswer:string;additionalInsights:string[];assumptions:string[];
  advisorRole:string;knowledgeScope:'MODEL_GENERAL_KNOWLEDGE_NO_WEB';provider:string;model:string;sources:Segment[];sourceIds:string[];
  citations:{sourceId:string;title:string;quote:string}[];
  metrics:{latencyMs:number;selectedChars:number;inputChars:number;selectedSegments:number;totalSegments:number;estimatedInputTokens:number;responseDepth:'FOCUSED'|'EXPANDED';contextBudgetCharacters:number;droppedLowInformation:number;advisorRole:string;usage?:{prompt_tokens?:number;completion_tokens?:number;total_tokens?:number;input_tokens?:number;output_tokens?:number}};
  partialContext:boolean;contextStrategy:string
}
export interface AssistantJob {id:string;status:'QUEUED'|'RUNNING'|'COMPLETED'|'FAILED'|'CANCELLED';question:string;createdAt:string;completedAt?:string;result?:AssistantResult;error?:string}
export class MeetingAssistantService {
  create(meeting:string,question:string,segments:Segment[],version:number,agentId:string,responseDepth:'FOCUSED'|'EXPANDED',externalApproved=false){
    return api.request<AssistantJob>(`/meetings/${meeting}/ask`,'POST',{question,segments,version,responseDepth,approved:true,externalApproved,...(agentId?{agentId}:{})})
  }
  read(meeting:string,id:string){return api.request<AssistantJob>(`/meetings/${meeting}/ask/${id}`)}
  cancel(meeting:string,id:string){return api.request<AssistantJob>(`/meetings/${meeting}/ask/${id}`,'DELETE')}
}
export const meetingAssistantService=new MeetingAssistantService()
