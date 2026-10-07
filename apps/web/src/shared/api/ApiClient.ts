export class ApiError extends Error { constructor(message: string, public status: number) { super(message) } }
export class ApiClient {
  private csrf: { token: string; headerName: string } | null = null
  resetSecurityToken() { this.csrf = null }
  async request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
    const headers: Record<string,string> = { 'Content-Type': 'application/json' }
    if (!['GET','HEAD'].includes(method)) {
      if (!this.csrf) this.csrf = await this.request('/auth/csrf')
      headers[this.csrf!.headerName] = this.csrf!.token
    }
    let response: Response
    try { response = await fetch(`/api${path}`, { method, headers, credentials:'same-origin', ...(body === undefined ? {} : { body: JSON.stringify(body) }) }) }
    catch { throw new ApiError('서버에 연결하지 못했습니다. Spring 서버 실행과 네트워크를 확인하세요.', 0) }
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      if (response.status === 403 || response.status === 401) this.csrf = null
      throw new ApiError(data.message || '요청을 처리하지 못했습니다. 서버 상태와 입력을 확인하세요.', response.status)
    }
    return data as T
  }
}
export const api = new ApiClient()
