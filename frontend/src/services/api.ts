const BASE_URL = '/api'

class ApiService {
  private token: string | null = null

  setToken(token: string | null) {
    this.token = token
    if (token) {
      localStorage.setItem('token', token)
    } else {
      localStorage.removeItem('token')
    }
  }

  getToken(): string | null {
    if (!this.token) {
      this.token = localStorage.getItem('token')
    }
    return this.token
  }

  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = { ...(options.headers as Record<string, string>) }

    // FormData 不设置 Content-Type，让浏览器自动设置 boundary
    if (!(options.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json'
    }

    const token = this.getToken()
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${BASE_URL}${path}`, {
      ...options,
      headers
    })

    const data = await response.json()

    if (!response.ok) {
      throw new Error(data.message || 'Request failed')
    }

    return data
  }

  // Config
  async getConfig() {
    return this.request('/config')
  }

  async validateToken() {
    return this.request('/auth/validate')
  }

  // Auth
  async login(username: string, password: string) {
    return this.request('/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    })
  }

  async adminLogin(username: string, password: string) {
    return this.request('/admin/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    })
  }

  // Registration
  async register(data: {
    minecraftName?: string
    bedrockName?: string
    email: string
    password?: string
    verifyCode?: string
    captchaToken?: string
    captchaAnswer?: string
  }) {
    return this.request('/register', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  // Captcha
  async generateCaptcha() {
    return this.request('/captcha/generate', {
      method: 'POST'
    })
  }

  // Verify Code
  async sendVerifyCode(email: string) {
    return this.request('/verify/send', {
      method: 'POST',
      body: JSON.stringify({ email })
    })
  }

  // User
  async getUserStatus() {
    return this.request('/user/status')
  }

  async updateUser(data: { email?: string }) {
    return this.request('/user/update', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  async changePassword(data: { oldPassword: string; newPassword: string }) {
    return this.request('/user/password', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  // Bedrock (User)
  async getBedrockStatus() {
    return this.request('/user/bedrock/status')
  }

  async setMyBedrockId(bedrockName: string) {
    return this.request('/user/bedrock/set', {
      method: 'POST',
      body: JSON.stringify({ bedrockName })
    })
  }

  async verifyMyBedrock() {
    return this.request('/user/bedrock/verify', {
      method: 'POST'
    })
  }

  async cancelMyBedrock() {
    return this.request('/user/bedrock/cancel', {
      method: 'POST'
    })
  }

  // Minecraft ID (Java)
  async getMinecraftStatus() {
    return this.request('/user/minecraft/status')
  }

  async setMinecraftId(minecraftName: string) {
    return this.request('/user/minecraft/set', {
      method: 'POST',
      body: JSON.stringify({ minecraftName })
    })
  }

  async verifyMinecraft() {
    return this.request('/user/minecraft/verify', {
      method: 'POST'
    })
  }

  // Admin
  async getUsers() {
    return this.request('/admin/users')
  }

  async getAdminQuestionnaire(username: string) {
    return this.request(`/admin/questionnaire/${username}`)
  }

  async updateAdminQuestionnaire(data: { username: string; questionnaireScore?: number; questionnairePassed?: boolean; questionnaireReasons?: string; status?: string }) {
    return this.request('/admin/questionnaire/update', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  // Background
  async getBackground() {
    return this.request('/admin/background')
  }

  async updateBackground(data: { image?: string; opacity?: number; blur?: number; announcement?: string }) {
    return this.request('/admin/background', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  async approveUser(username: string) {
    return this.request('/admin/user/approve', {
      method: 'POST',
      body: JSON.stringify({ username })
    })
  }

  async rejectUser(username: string, reason?: string) {
    return this.request('/admin/user/reject', {
      method: 'POST',
      body: JSON.stringify({ username, reason })
    })
  }

  async banUser(username: string, reason?: string) {
    return this.request('/admin/user/ban', {
      method: 'POST',
      body: JSON.stringify({ username, reason })
    })
  }

  async unbanUser(username: string) {
    return this.request('/admin/user/unban', {
      method: 'POST',
      body: JSON.stringify({ username })
    })
  }

  async deleteUser(username: string) {
    return this.request('/admin/user/delete', {
      method: 'POST',
      body: JSON.stringify({ username })
    })
  }

  async addUser(data: { username: string; email?: string; status?: string; minecraftName?: string; bedrockName?: string }) {
    return this.request('/admin/user/add', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  async adminUpdateUser(data: { username: string; email?: string; status?: string; minecraftName?: string; bedrockName?: string; banReason?: string }) {
    return this.request('/admin/user/update', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  // Batch operations
  async batchApproveUsers(usernames: string[]) {
    return this.request('/admin/user/batch-approve', {
      method: 'POST',
      body: JSON.stringify({ usernames })
    })
  }

  async batchRejectUsers(usernames: string[], reason?: string) {
    return this.request('/admin/user/batch-reject', {
      method: 'POST',
      body: JSON.stringify({ usernames, reason })
    })
  }

  async batchBanUsers(usernames: string[], reason?: string) {
    return this.request('/admin/user/batch-ban', {
      method: 'POST',
      body: JSON.stringify({ usernames, reason })
    })
  }

  async batchDeleteUsers(usernames: string[]) {
    return this.request('/admin/user/batch-delete', {
      method: 'POST',
      body: JSON.stringify({ usernames })
    })
  }

  async updateUserStatus(username: string, status: string) {
    return this.request('/admin/user/update-status', {
      method: 'POST',
      body: JSON.stringify({ username, status })
    })
  }

  // Docs Management
  async createDoc(title: string, category: string, filename?: string) {
    return this.request('/admin/docs/create', {
      method: 'POST',
      body: JSON.stringify({ title, category, filename })
    })
  }

  async deleteDoc(filename: string) {
    return this.request('/admin/docs/delete', {
      method: 'POST',
      body: JSON.stringify({ filename })
    })
  }

  async updateDoc(filename: string, content: string) {
    return this.request('/admin/docs/update', {
      method: 'POST',
      body: JSON.stringify({ filename, content })
    })
  }

  async createCategory(name: string) {
    return this.request('/admin/docs/category/create', {
      method: 'POST',
      body: JSON.stringify({ name })
    })
  }

  async deleteCategory(dirName: string) {
    return this.request('/admin/docs/category/delete', {
      method: 'POST',
      body: JSON.stringify({ dirName })
    })
  }

  async reorderDocs(type: string, items: any[]) {
    return this.request('/admin/docs/reorder', {
      method: 'POST',
      body: JSON.stringify({ type, items })
    })
  }

  // Maintenance Mode
  async getMaintenanceMode() {
    return this.request('/admin/maintenance')
  }

  async setMaintenanceMode(enabled: boolean) {
    return this.request('/admin/maintenance', {
      method: 'POST',
      body: JSON.stringify({ enabled })
    })
  }

  // Server Config
  async getServerConfig() {
    return this.request('/admin/server-config')
  }

  async updateServerConfig(data: Record<string, any>) {
    return this.request('/admin/server-config', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  // System Config
  async getSystemConfig() {
    return this.request('/admin/system-config')
  }

  async updateSystemConfig(data: Record<string, any>) {
    return this.request('/admin/system-config', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  // Questionnaire Management
  async getQuestionnaireList() {
    return this.request('/admin/questionnaire/list')
  }

  async resetQuestionnaire() {
    return this.request('/admin/questionnaire/reset')
  }

  async saveQuestionnaire(data: string) {
    return this.request('/admin/questionnaire/save', {
      method: 'POST',
      body: data
    })
  }

  async addQuestion(question: any) {
    return this.request('/admin/questionnaire/add-question', {
      method: 'POST',
      body: JSON.stringify({ question })
    })
  }

  async updateQuestion(payload: { question: any; options: any[] }) {
    return this.request('/admin/questionnaire/update-question', { method: 'POST', body: JSON.stringify(payload) })
  }

  async moveQuestion(payload: { id: number; direction: 'up' | 'down' }) {
    return this.request('/admin/questionnaire/move-question', { method: 'POST', body: JSON.stringify(payload) })
  }

  async saveBulkQuestionnaire(payload: { name: string; passScore: number; questions: any[] }) {
    return this.request('/admin/questionnaire/save-bulk', { method: 'POST', body: JSON.stringify(payload) })
  }

  async deleteQuestion(id: number) {
    return this.request('/admin/questionnaire/delete-question', {
      method: 'POST',
      body: JSON.stringify({ id })
    })
  }

  // Appeals
  async submitAppeal(reason: string) {
    return this.request('/questionnaire/appeal', {
      method: 'POST',
      body: JSON.stringify({ reason })
    })
  }

  async getAppeals() {
    return this.request('/admin/appeals')
  }

  async approveAppeal(id: number, reply?: string) {
    return this.request('/admin/appeals/approve', {
      method: 'POST',
      body: JSON.stringify({ id, reply })
    })
  }

  async rejectAppeal(id: number, reply?: string) {
    return this.request('/admin/appeals/reject', {
      method: 'POST',
      body: JSON.stringify({ id, reply })
    })
  }

  // Stats
  async getStatsOverview() {
    return this.request('/admin/stats/overview')
  }

  async getStatsRegistrations() {
    return this.request('/admin/stats/registrations')
  }

  async getStatsQuestionnaires() {
    return this.request('/admin/stats/questionnaires')
  }

  // User Profile
  async getUserProfile() {
    return this.request('/user/profile')
  }

  // QQ 绑定（docs/ASTRBOT_PLAN.md §5.2）
  async getQqBindStatus() {
    return this.request('/user/qq/status')
  }

  async qqBind(code: string) {
    return this.request('/user/qq/bind', {
      method: 'POST',
      body: JSON.stringify({ code })
    })
  }

  async qqUnbind() {
    return this.request('/user/qq/unbind', { method: 'POST' })
  }

  async updateUserProfile(data: { avatar?: string }) {
    return this.request('/user/profile', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  async uploadAvatar(imageData: string) {
    return this.request('/user/avatar/upload', {
      method: 'POST',
      body: JSON.stringify({ image: imageData })
    })
  }

  async updateEmail(email: string, verifyCode: string) {
    return this.request('/user/email/update', {
      method: 'POST',
      body: JSON.stringify({ email, verifyCode })
    })
  }

  // Bedrock
  async setBedrockId(username: string, bedrockName: string) {
    return this.request('/admin/user/set-bedrock', {
      method: 'POST',
      body: JSON.stringify({ username, bedrockName })
    })
  }

  async verifyBedrockId(username: string) {
    return this.request('/admin/user/verify-bedrock', {
      method: 'POST',
      body: JSON.stringify({ username })
    })
  }

  async getAuditLogs() {
    return this.request('/admin/audits')
  }

  // Version
  async getVersion() {
    return this.request('/version')
  }

  // Server Status
  async getServerStatus() {
    return this.request('/server/status')
  }

  // Portal Management
  async getPortalConfig() {
    return this.request('/admin/portal')
  }

  async updatePortalConfig(data: Record<string, any>) {
    return this.request('/admin/portal', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  async updatePortalTeam(team: Array<{ name: string; role: string; avatar?: string }>) {
    return this.request('/admin/portal/team', {
      method: 'POST',
      body: JSON.stringify({ team })
    })
  }

  async updatePortalCarousel(carousel: Array<{ image: string; title: string; subtitle: string }>) {
    return this.request('/admin/portal/carousel', {
      method: 'POST',
      body: JSON.stringify({ carousel })
    })
  }

  async updatePortalFeatures(features: Array<{ icon: string; title: string; description: string }>) {
    return this.request('/admin/portal/features', {
      method: 'POST',
      body: JSON.stringify({ features })
    })
  }

  async updatePortalTimeline(timeline: Array<{ date: string; title: string; description: string; image?: string }>) {
    return this.request('/admin/portal/timeline', {
      method: 'POST',
      body: JSON.stringify({ timeline })
    })
  }

  // File Upload
  async uploadImage(file: File): Promise<{ url: string; filename: string }> {
    const formData = new FormData()
    formData.append('file', file)
    return this.request('/admin/upload', {
      method: 'POST',
      body: formData
    })
  }

  // CMI Data
  async getCmiStats() {
    return this.request('/cmi/stats')
  }

  async getCmiExtendedStats() {
    return this.request('/cmi/stats/extended')
  }

  async getCmiWealth(limit: number = 10) {
    return this.request(`/cmi/wealth?limit=${limit}`)
  }

  async getCmiPlaytime(limit: number = 10) {
    return this.request(`/cmi/playtime?limit=${limit}`)
  }

  async getCmiActiveDays(limit: number = 10) {
    return this.request(`/cmi/activedays?limit=${limit}`)
  }

  async getCmiOnlinePlayers() {
    return this.request('/cmi/online')
  }

  async getCmiBanned() {
    return this.request('/cmi/banned')
  }

  async getCmiPlayer(username: string) {
    return this.request(`/cmi/player/${encodeURIComponent(username)}`)
  }

  // Village Trade
  async getVillageList() {
    return this.request('/village/list')
  }

  async submitVillageTrade(data: any) {
    return this.request('/village/submit', { method: 'POST', body: JSON.stringify(data) })
  }

  async getVillageAdminPending() {
    return this.request('/village/admin/pending')
  }

  async approveVillageTrade(id: number) {
    return this.request(`/village/admin/approve/${id}`, { method: 'POST' })
  }

  async rejectVillageTrade(id: number) {
    return this.request(`/village/admin/reject/${id}`, { method: 'POST' })
  }

  // Player History
  async getPlayerHistory() {
    return this.request('/server/player-history')
  }

  // Player Profile
  async getPlayerList() {
    return this.request('/players/list')
  }

  async getPlayerProfile(username: string) {
    return this.request(`/players/profile/${encodeURIComponent(username)}`)
  }

  // Invite
  async generateInviteCode() {
    return this.request('/invite/generate', { method: 'POST', body: '{}' })
  }

  async getMyInviteCodes() {
    return this.request('/invite/my-codes')
  }

  async getPendingInvitations() {
    return this.request('/invite/pending')
  }

  async confirmInvitation(username: string) {
    return this.request('/invite/confirm', { method: 'POST', body: JSON.stringify({ username }) })
  }

  async rejectInvitation(username: string) {
    return this.request('/invite/reject', { method: 'POST', body: JSON.stringify({ username }) })
  }

  // Notifications
  async getNotifications() {
    return this.request('/notifications')
  }

  async markNotificationRead(id: number) {
    return this.request('/notifications/read', { method: 'POST', body: JSON.stringify({ id }) })
  }

  async markAllNotificationsRead() {
    return this.request('/notifications/read-all', { method: 'POST' })
  }

  // Verify
  async checkVerification() {
    return this.request('/verify/check', { method: 'POST' })
  }

  async getVerifyStatus() {
    return this.request('/verify/status')
  }

  // Setup (first run)
  async getSetupStatus() {
    return this.request('/setup/status')
  }

  async submitSetup(form: FormData) {
    return this.request('/setup', { method: 'POST', body: form })
  }

  // Migration (admin)
  async uploadMigrationDump(file: File): Promise<any> {
    const formData = new FormData()
    formData.append('file', file)
    return this.request('/admin/migration/upload', { method: 'POST', body: formData })
  }

  async getRegisterSettings() { return this.request('/admin/settings/register') }
  async saveRegisterSettings(body: any) { return this.request('/admin/settings/register', { method: 'PUT', body: JSON.stringify(body) }) }
  async getLlmSettings() { return this.request('/admin/settings/llm') }
  async saveLlmSettings(body: any) { return this.request('/admin/settings/llm', { method: 'PUT', body: JSON.stringify(body) }) }
  async getQuestionnaireSettings() { return this.request('/admin/settings/questionnaire') }
  async saveQuestionnaireSettings(body: any) { return this.request('/admin/settings/questionnaire', { method: 'PUT', body: JSON.stringify(body) }) }
  async getInviteSettings() { return this.request('/admin/settings/invite') }
  async saveInviteSettings(body: any) { return this.request('/admin/settings/invite', { method: 'PUT', body: JSON.stringify(body) }) }
  async getGameSettings() { return this.request('/admin/settings/game') }
  async saveGameSettings(body: any) { return this.request('/admin/settings/game', { method: 'PUT', body: JSON.stringify(body) }) }
  async getAstrbotSettings() { return this.request('/admin/settings/astrbot') }
  async saveAstrbotSettings(body: any) { return this.request('/admin/settings/astrbot', { method: 'PUT', body: JSON.stringify(body) }) }
  async getDownloadsAdmin() { return this.request('/admin/settings/downloads') }
  async saveDownloadsAdmin(body: any) { return this.request('/admin/settings/downloads', { method: 'PUT', body: JSON.stringify(body) }) }

  async getQuestionnaireSettings() { return this.request('/admin/settings/questionnaire') }
  async saveQuestionnaireSettings(body: any) { return this.request('/admin/settings/questionnaire', { method: 'PUT', body: JSON.stringify(body) }) }

  async getMigrationReport() {
    return this.request('/admin/migration/report')
  }

  // Public Machines
  async getMachineList() {
    return this.request('/machine/list')
  }

  async submitMachine(data: any) {
    return this.request('/machine/submit', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  async uploadMachineScreenshot(file: File): Promise<{ url: string; filename: string }> {
    const formData = new FormData()
    formData.append('file', file)
    return this.request('/machine/upload', {
      method: 'POST',
      body: formData
    })
  }

  async getMachineAdminPending() {
    return this.request('/machine/admin/pending')
  }

  async approveMachine(id: number) {
    return this.request(`/machine/admin/approve/${id}`, { method: 'POST' })
  }

  async rejectMachine(id: number) {
    return this.request(`/machine/admin/reject/${id}`, { method: 'POST' })
  }
}

export const api = new ApiService()
export default api
