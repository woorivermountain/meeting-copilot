import { api } from '../../shared/api/ApiClient'

export interface MeetingLifecycleRecord {
  id: string
  teamId: string
  title: string
  status: 'OPEN' | 'ENDED'
  revision: number
  createdBy: string
  createdAt: string
}

export type TrashedMeeting = MeetingLifecycleRecord & { deletedAt: string }

export class MeetingTrashService {
  list(teamId: string) {
    return api.request<TrashedMeeting[]>(`/teams/${encodeURIComponent(teamId)}/meetings/trash`)
  }

  trash(meetingId: string) {
    return api.request<{ deleted: true }>(`/meetings/${encodeURIComponent(meetingId)}`, 'DELETE')
  }

  restore(meetingId: string) {
    return api.request<MeetingLifecycleRecord>(`/meetings/${encodeURIComponent(meetingId)}/restore`, 'POST')
  }
}

export const meetingTrashService = new MeetingTrashService()
