import { reactive } from 'vue'
import { api, ApiError } from '../../shared/api/ApiClient'
export interface User { id: string; email: string; name: string }
export const auth = reactive<{ user: User | null; checked: boolean }>({ user: null, checked: false })
export class AuthService {
  async restore() {
    try { auth.user = await api.request<User>('/auth/me') }
    catch (e) { if (!(e instanceof ApiError) || e.status !== 401) throw e; auth.user = null }
    auth.checked = true
  }
  async login(email: string,password: string) { auth.user = await api.request<User>('/auth/login','POST',{email,password});auth.checked=true;api.resetSecurityToken() }
  async signup(email: string,name: string,password: string) { auth.user = await api.request<User>('/auth/signup','POST',{email,name,password});auth.checked=true;api.resetSecurityToken() }
  async logout() { await api.request('/auth/logout','POST');auth.user=null;auth.checked=true;api.resetSecurityToken() }
}
export const authService = new AuthService()
