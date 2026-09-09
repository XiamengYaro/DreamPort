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

    if (response.status === 401 && !path.startsWith('/login') && !path.startsWith('/admin/login')) {
      // 修复审计(前端)：token 失效统一清理并回登录，避免页面"假死"
      this.setToken(null)
      localStorage.removeItem('isAdmin')
      localStorage.removeItem('username')
      localStorage.removeItem('pendingUsername')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      throw new Error(data.message || '登录已过期，请重新登录')
    }

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
    email: string
    password?: string
    verifyCode?: string
    captchaToken?: string
    captchaAnswer?: string
    rulesAccepted?: boolean
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

  async syncMinecraftByUuid() {
    return this.request('/user/minecraft/sync-by-uuid', { method: 'POST' })
  }

  async acceptRules() {
    return this.request('/user/rules/accept', { method: 'POST' })
  }

  // 积分任务中心
  async getTasksCenter() {
    return this.request('/points/center')
  }

  async postPointsSignin() {
    return this.request('/points/signin', { method: 'POST' })
  }

  async postPointsClaim(taskId: string) {
    return this.request('/points/claim', { method: 'POST', body: JSON.stringify({ taskId }) })
  }

  async getPointsShop() {
    return this.request('/points/shop')
  }

  async postPointsRedeem(rewardId: string) {
    return this.request('/points/redeem', { method: 'POST', body: JSON.stringify({ rewardId }) })
  }

  // Admin
  async getTasksConfigAdmin() {
    return this.request('/admin/settings/tasksconfig')
  }

  async saveTasksConfigAdmin(body: any) {
    return this.request('/admin/settings/tasksconfig', { method: 'PUT', body: JSON.stringify(body) })
  }

  async getShopConfigAdmin() {
    return this.request('/admin/settings/shopconfig')
  }

  async saveShopConfigAdmin(body: any) {
    return this.request('/admin/settings/shopconfig', { method: 'PUT', body: JSON.stringify(body) })
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

  async banUser(username: string, reason?: string, days?: number) {
    return this.request('/admin/user/ban', {
      method: 'POST',
      body: JSON.stringify({ username, reason, days })
    })
  }

  async unbanUser(username: string) {
    return this.request('/admin/user/unban', {
      method: 'POST',
      body: JSON.stringify({ username })
    })
  }

  // 公开封禁名单(/bans 页)
  async getBans() {
    return this.request('/bans')
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

  // Docs Management(参数对齐后端 DocBody{title,category,filename,content})
  async getDocs() {
    return this.request('/docs')
  }

  async readDoc(category: string | null, filename: string) {
    const q = new URLSearchParams({ filename })
    if (category) q.set('category', category)
    return this.request(`/docs/detail?${q.toString()}`)
  }

  async readDocBySlug(slug: string) {
    return this.request(`/docs/${encodeURIComponent(slug)}`)
  }

  async createDoc(title: string, category: string, content?: string) {
    return this.request('/admin/docs/create', {
      method: 'POST',
      body: JSON.stringify({ title, category, content })
    })
  }

  async deleteDoc(category: string | null, filename: string) {
    return this.request('/admin/docs/delete', {
      method: 'POST',
      body: JSON.stringify({ category, filename })
    })
  }

  async updateDoc(category: string | null, filename: string, content: string) {
    return this.request('/admin/docs/update', {
      method: 'POST',
      body: JSON.stringify({ category, filename, content })
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
  async getMyAppeal() {
    return this.request('/questionnaire/appeal/mine')
  }

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

  // 聊天室（docs/CHAT_SERVERINFO_PLAN.md）
  async getChatHistory(params?: { before?: number; limit?: number; origin?: string }) {
    const q = new URLSearchParams()
    if (params?.before != null) q.set('before', String(params.before))
    if (params?.limit != null) q.set('limit', String(params.limit))
    if (params?.origin) q.set('origin', params.origin)
    const qs = q.toString()
    return this.request(`/chat/history${qs ? '?' + qs : ''}`)
  }

  async sendChat(message: string) {
    return this.request('/chat/send', { method: 'POST', body: JSON.stringify({ message }) })
  }

  // 免登录申请状态查询（/status 页；后端返回裸 {found, ...} 结构）
  async getReviewStatus(username: string) {
    return this.request(`/review/status?username=${encodeURIComponent(username)}`)
  }

  // 公告页(资讯中心 + 更新日志)
  async getAnnouncements() {
    return this.request('/announcements')
  }

  async getAnnouncementsAdmin() {
    return this.request('/admin/settings/announcements')
  }

  async saveAnnouncementsAdmin(body: any) {
    return this.request('/admin/settings/announcements', { method: 'PUT', body: JSON.stringify(body) })
  }

  // 照片墙留言（首页时光照片墙）
  async getPhotoComments(photoKey: string) {
    return this.request(`/portal/comments/${encodeURIComponent(photoKey)}`)
  }

  async getPhotoCommentCounts() {
    return this.request('/portal/comments/counts')
  }

  async postPhotoComment(photoKey: string, content: string) {
    return this.request(`/portal/comments/${encodeURIComponent(photoKey)}`, {
      method: 'POST',
      body: JSON.stringify({ content })
    })
  }

  async deletePhotoComment(id: number) {
    return this.request(`/admin/portal/comments/${id}`, { method: 'DELETE' })
  }

  async updateUserProfile(data: { avatar?: string }) {
    return this.request('/user/profile', {
      method: 'POST',
      body: JSON.stringify(data)
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
  async getPlayerHistory(days?: number) {
    return this.request(`/server/player-history${days ? `?days=${days}` : ''}`)
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

  async deleteNotification(id: number) {
    return this.request(`/notifications/${id}`, { method: 'DELETE' })
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

  async getRulesConfig() { return this.request('/admin/settings/rules') }
  async saveRulesConfig(body: any) { return this.request('/admin/settings/rules', { method: 'PUT', body: JSON.stringify(body) }) }

  // 称号与成就(管理端;titlesconfig/achievementsconfig 后端收裸数组)
  async getTitlesOverview() { return this.request('/admin/titles/overview') }
  async saveTitlesConfigAdmin(titles: any[]) { return this.request('/admin/settings/titlesconfig', { method: 'PUT', body: JSON.stringify(titles) }) }
  async saveAchievementsConfigAdmin(achievements: any[]) { return this.request('/admin/settings/achievementsconfig', { method: 'PUT', body: JSON.stringify(achievements) }) }
  async grantTitle(username: string, code: string) { return this.request('/admin/titles/grant', { method: 'POST', body: JSON.stringify({ username, code }) }) }
  async revokeTitle(username: string, code: string) { return this.request('/admin/titles/revoke', { method: 'POST', body: JSON.stringify({ username, code }) }) }

  // 称号与成就(用户端;JWT 鉴权,作用于当前登录玩家)
  async getMyTitles() { return this.request('/titles/mine') }
  async equipTitle(code: string) { return this.request('/titles/equip', { method: 'POST', body: JSON.stringify({ code }) }) }
  async unequipTitle() { return this.request('/titles/unequip', { method: 'POST', body: JSON.stringify({}) }) }

  // 奖励礼包(管理端:模板 CRUD + 发放)
  async getRewardKits() { return this.request('/admin/rewards/kits') }
  async createRewardKit(name: string, note: string) { return this.request('/admin/rewards/kits', { method: 'POST', body: JSON.stringify({ name, note }) }) }
  async updateRewardKit(id: number, body: { note?: string; commandsText?: string }) { return this.request(`/admin/rewards/kits/${id}`, { method: 'PUT', body: JSON.stringify(body) }) }
  async deleteRewardKit(id: number) { return this.request(`/admin/rewards/kits/${id}`, { method: 'DELETE' }) }
  async sendReward(body: { usernames?: string[]; all?: boolean; kitId?: number; commandsText?: string; title?: string; note?: string }) { return this.request('/admin/rewards/send', { method: 'POST', body: JSON.stringify(body) }) }

  // 聊天 SSE 一次性流票据(避免 JWT 进 URL,审计修复)
  async getChatStreamTicket() {
    return this.request('/chat/stream-ticket', { method: 'POST', body: '{}' })
  }

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
