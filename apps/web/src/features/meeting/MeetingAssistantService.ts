import {api} from '../../shared/api/ApiClient'
import type {Segment} from './MeetingService'
export type KnowledgeScope='MEETING_ONLY'|'MEETING_PLUS_GENERAL'
export interface BriefItem {signal:string;sourceId:string;receivedAt:string;text:string}
export interface MeetingBrief {kind:'DETERMINISTIC_MEETING_BRIEF';topics:string[];keyMoments:BriefItem[];recentThread:BriefItem[];substantiveSegments:number;totalSegments:number;llmTokensUsed:0}
export interface TermCorrection {original:string;suggested:string;occurrences:number;sourceIds:string[]}
export interface RetentionSuggestion {kind:'DECISION'|'ACTION'|'ISSUE';text:string;sourceIds:string[]}
export interface AssistantResult {
  answer:string;groundedAnswer:string;additionalInsights:string[];assumptions:string[];
  advisorRole:string;knowledgeScope:KnowledgeScope;provider:string;model:string;sources:Segment[];sourceIds:string[];
  meetingBrief:MeetingBrief;queryCorrections:TermCorrection[];retentionSuggestions:RetentionSuggestion[];
  citations:{sourceId:string;title:string;quote:string}[];
  metrics:{latencyMs:number;selectedChars:number;inputChars:number;selectedSegments:number;totalSegments:number;estimatedInputTokens:number;responseDepth:'FOCUSED'|'EXPANDED';contextBudgetCharacters:number;droppedLowInformation:number;advisorRole:string;usage?:{prompt_tokens?:number;completion_tokens?:number;total_tokens?:number;input_tokens?:number;output_tokens?:number}};
  partialContext:boolean;contextStrategy:string
}
export interface AssistantJob {id:string;status:'QUEUED'|'RUNNING'|'COMPLETED'|'FAILED'|'CANCELLED';question:string;createdAt:string;completedAt?:string;result?:AssistantResult;error?:string}
export interface ContextPreview {strategy:string;brief:MeetingBrief;segmentCount:number;llmTokensUsed:0}
export class MeetingAssistantService {
  create(meeting:string,question:string,segments:Segment[],version:number,agentId:string,responseDepth:'FOCUSED'|'EXPANDED',knowledgeScope:KnowledgeScope,externalApproved=false){
    return api.request<AssistantJob>(`/meetings/${meeting}/ask`,'POST',{question,segments,version,responseDepth,knowledgeScope,approved:true,externalApproved,...(agentId?{agentId}:{})})
  }
  context(meeting:string,segments:Segment[],version:number){return api.request<ContextPreview>(`/meetings/${meeting}/ask/context`,'POST',{segments,version})}
  read(meeting:string,id:string){return api.request<AssistantJob>(`/meetings/${meeting}/ask/${id}`)}
  cancel(meeting:string,id:string){return api.request<AssistantJob>(`/meetings/${meeting}/ask/${id}`,'DELETE')}
}
export const meetingAssistantService=new MeetingAssistantService()
