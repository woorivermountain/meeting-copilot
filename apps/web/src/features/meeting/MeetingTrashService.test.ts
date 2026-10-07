import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../shared/api/ApiClient'
import { MeetingTrashService } from './MeetingTrashService'

describe('MeetingTrashService', () => {
  const service = new MeetingTrashService()

  beforeEach(() => vi.restoreAllMocks())

  it('uses the recoverable trash endpoints without a permanent-delete call', async () => {
    const request = vi.spyOn(api, 'request').mockResolvedValue({ deleted: true })

    await service.list('team/id')
    await service.trash('meeting/id')
    await service.restore('meeting/id')

    expect(request.mock.calls).toEqual([
      ['/teams/team%2Fid/meetings/trash'],
      ['/meetings/meeting%2Fid', 'DELETE'],
      ['/meetings/meeting%2Fid/restore', 'POST'],
    ])
  })
})
