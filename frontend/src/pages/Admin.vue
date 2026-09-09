<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-7xl mx-auto">
      <div class="card p-6 mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-white">管理面板</h1>
          <p class="text-stone-400">欢迎回来，{{ username }}</p>
        </div>
        <label class="flex items-center gap-2 text-sm cursor-pointer select-none"
          :class="maintenanceEnabled ? 'text-rose-300' : 'text-stone-400'">
          <AppIcon name="wrench" class="w-4 h-4" /> 维护模式
          <input type="checkbox" v-model="maintenanceEnabled" @change="toggleMaintenance" class="accent-rose-500 w-4 h-4" />
        </label>
      </div>

      <!-- 左侧菜单(桌面竖排 / 移动端横滚) + 右侧内容区 -->
      <div class="flex flex-col lg:flex-row gap-6 items-start">
        <nav class="card p-2 w-full lg:w-52 lg:sticky lg:top-24 shrink-0 flex lg:flex-col gap-1 overflow-x-auto">
          <button v-for="m in menuItems" :key="m.key" @click="activeTab = m.key"
            class="flex items-center gap-2 whitespace-nowrap px-3 py-2.5 rounded-xl text-sm font-medium transition-colors"
            :class="activeTab === m.key ? 'text-orange-400 bg-orange-500/15' : 'text-stone-400 hover:text-white hover:bg-white/5'">
            <AppIcon :name="m.icon" class="w-4 h-4" />
            {{ m.label }}
          </button>
        </nav>
        <!-- 内容区 -->
        <div class="flex-1 min-w-0 w-full">
      <div v-if="loading" class="card p-12 text-center">
        <div class="inline-flex items-center gap-3 text-stone-400">
          <svg class="w-6 h-6 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.641z"></path>
          </svg>
          <span>加载中...</span>
        </div>
      </div>

      <!-- 总览 Dashboard(默认首屏) -->
      <div v-if="activeTab === 'dashboard' && !loading" class="space-y-6">
        <div class="grid grid-cols-2 md:grid-cols-3 gap-4">
          <StatCard :value="statsOverview.totalUsers || 0" label="注册用户" tone="orange" />
          <StatCard :value="statsOverview.todayRegistrations || 0" label="今日注册" tone="blue" />
          <StatCard :value="statsOverview.pendingUsers || 0" label="待审核" tone="amber" />
          <StatCard :value="statsOverview.bannedUsers || 0" label="已封禁" tone="rose" />
          <StatCard :value="serverStatus.players?.online ?? 0" label="在线玩家" tone="emerald" />
          <StatCard :value="((statsOverview.questionnairePassRate?.toFixed(1)) || 0) + '%'" label="问卷通过率" tone="purple" />
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
          <div class="card p-6">
            <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2"><AppIcon name="users" class="w-5 h-5 text-orange-400" /> 最新注册</h3>
            <div class="space-y-2">
              <div v-for="u in recentUsers" :key="u.username"
                class="flex items-center gap-3 p-2.5 rounded-xl bg-stone-900/40 border border-stone-800">
                <AppAvatar :name="u.minecraftName || u.username" size-class="w-9 h-9" />
                <div class="flex-1 min-w-0">
                  <div class="text-white text-sm font-medium truncate">{{ u.username }}</div>
                  <div class="text-xs text-stone-400">{{ formatTime(u.regTime) }}</div>
                </div>
                <span class="text-xs px-2 py-1 rounded-lg" :class="u.status === 'approved' ? 'bg-emerald-500/15 text-emerald-400' : u.status === 'banned' ? 'bg-rose-500/15 text-rose-400' : 'bg-amber-500/15 text-amber-400'">{{ getStatusText(u.status) }}</span>
              </div>
              <p v-if="recentUsers.length === 0" class="text-stone-500 text-sm text-center py-4">暂无用户</p>
            </div>
            <button @click="activeTab = 'players'" class="btn-secondary text-sm w-full mt-3">进入玩家管理</button>
          </div>

          <div class="card p-6">
            <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2"><AppIcon name="document-text" class="w-5 h-5 text-sky-400" /> 最近操作</h3>
            <div class="space-y-2">
              <div v-for="a in recentAudits" :key="a.id" class="p-2.5 rounded-xl bg-stone-900/40 border border-stone-800">
                <div class="flex items-center justify-between gap-2">
                  <span class="badge-info text-xs">{{ auditActionLabel(a.action) }}</span>
                  <span class="text-xs text-stone-400">{{ formatTime(a.timestamp) }}</span>
                </div>
                <div class="text-xs text-stone-400 mt-1 truncate">{{ a.operator }} → {{ a.target }}<span v-if="a.detail"> · {{ a.detail }}</span></div>
              </div>
              <p v-if="recentAudits.length === 0" class="text-stone-500 text-sm text-center py-4">暂无操作记录</p>
            </div>
            <button @click="activeTab = 'audits'" class="btn-secondary text-sm w-full mt-3">查看全部审计日志</button>
          </div>
        </div>

        <div class="card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">快捷入口</h3>
          <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            <button v-for="m in quickLinks" :key="m.key" @click="activeTab = m.key"
              class="flex flex-col items-center gap-2 p-4 rounded-xl bg-stone-900/40 border border-stone-800 hover:border-orange-500/50 hover:bg-white/5 transition-colors">
              <AppIcon :name="m.icon" class="w-5 h-5 text-orange-400" />
              <span class="text-xs text-stone-300">{{ m.label }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 网站内容 Tab -->
      <div v-if="activeTab === 'portal' && !loading" class="space-y-6">
        <!-- 基础信息 -->
        <div class="card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">基础信息</h3>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm text-stone-300 mb-1">服务器名称</label>
              <input v-model="portalData.server_name" type="text" class="input" />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">副标题</label>
              <input v-model="portalData.subtitle" type="text" class="input" />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">品牌短名(登录/注册页、页脚)</label>
              <input v-model="portalData.brand_short" type="text" class="input" placeholder="XMCraft" />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">账户系统副标语(登录/注册页)</label>
              <input v-model="portalData.brand_tagline" type="text" class="input" placeholder="玩家账户系统" />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">品牌主色(全站按钮/链接/图标跟随)</label>
              <div class="flex gap-2 items-center">
                <input v-model="portalData.accent" type="color" class="input w-14 h-9 p-1 cursor-pointer" />
                <input v-model="portalData.accent" type="text" class="input flex-1 font-mono" placeholder="#f97316" />
              </div>
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">Favicon 路径</label>
              <input v-model="portalData.favicon" type="text" class="input font-mono" placeholder="/favicon.png" />
            </div>
            <div class="md:col-span-2">
              <label class="block text-sm text-stone-300 mb-1">服务器描述</label>
              <textarea v-model="portalData.description" class="input min-h-[80px]" rows="3"></textarea>
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">游戏版本</label>
              <input v-model="portalData.version" type="text" class="input" />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">服务器地址</label>
              <input v-model="portalData.server_ip" type="text" class="input" />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">服务器端口</label>
              <input v-model.number="portalData.server_port" type="number" class="input" />
            </div>
            <div class="md:col-span-2">
              <label class="block text-sm text-stone-300 mb-1">地图（名称 / 地址 / 类型，类型用于地图页在线玩家侧栏）</label>
              <div v-for="(m, i) in portalData.map_items" :key="i" class="flex flex-wrap gap-2 mb-2">
                <input v-model="m.name" class="input w-36" placeholder="名称" />
                <input v-model="m.url" class="input flex-1 min-w-[220px]" placeholder="http://mc.example.com:8123" />
                <select v-model="m.type" class="input w-32">
                  <option value="bluemap">BlueMap</option>
                  <option value="dynmap">Dynmap</option>
                  <option value="generic">通用网页</option>
                </select>
                <button type="button" @click="portalData.map_items.splice(i, 1)"
                  class="text-rose-400 hover:text-rose-300 text-sm px-2">删除</button>
              </div>
              <button type="button" @click="portalData.map_items.push({ name: '', url: '', type: 'bluemap' })"
                class="btn-secondary text-xs py-1.5 px-3">+ 添加地图</button>
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">Wiki 链接</label>
              <input v-model="portalData.social.wiki" type="text" class="input" />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">Logo 图片</label>
              <div class="flex gap-2">
                <input v-model="portalData.logo" type="text" class="input flex-1" placeholder="/logo.png" />
                <label class="btn-secondary cursor-pointer">
                  上传
                  <input type="file" accept="image/*" class="hidden" @change="uploadLogo" />
                </label>
              </div>
              <div v-if="portalData.logo" class="mt-2">
                <img :src="portalData.logo" class="h-12 rounded-lg" @error="$event.target.style.display='none'" />
              </div>
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">备案信息</label>
              <input v-model="portalData.icp" type="text" class="input" placeholder="例如: 京ICP备12345678号" />
            </div>
          </div>
          <button @click="savePortalConfig" class="btn-primary mt-4" :disabled="saving">保存基础信息</button>
        </div>

        <!-- 轮播图管理 -->
        <div class="card p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-white">轮播图管理</h3>
            <button @click="addCarousel" class="btn-secondary text-sm">+ 添加轮播图</button>
          </div>
          <div class="space-y-4">
            <div v-for="(item, index) in portalData.carousel" :key="index" class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
              <div class="flex items-start gap-4">
                <div class="w-32 h-20 rounded-lg overflow-hidden bg-stone-700 flex-shrink-0">
                  <img v-if="item.image" :src="item.image" class="w-full h-full object-cover" @error="$event.target.src=''" />
                  <div v-else class="w-full h-full flex items-center justify-center text-stone-500 text-xs">无图片</div>
                </div>
                <div class="flex-1 space-y-2">
                  <input v-model="item.image" type="text" class="input text-sm" placeholder="图片路径或 URL" />
                  <input v-model="item.title" type="text" class="input text-sm" placeholder="标题" />
                  <input v-model="item.subtitle" type="text" class="input text-sm" placeholder="副标题" />
                  <div class="flex gap-2">
                    <label class="btn-secondary text-xs cursor-pointer">
                      上传图片
                      <input type="file" accept="image/*" class="hidden" @change="uploadCarouselImage($event, index)" />
                    </label>
                  </div>
                </div>
                <button @click="removeCarousel(index)" class="text-rose-400 hover:text-rose-300 p-2">删除</button>
              </div>
            </div>
          </div>
          <button @click="savePortalConfig" class="btn-primary mt-4" :disabled="saving">保存轮播图</button>
        </div>

        <!-- 管理团队 -->
        <div class="card p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-white">管理团队</h3>
            <button @click="addTeamMember" class="btn-secondary text-sm">+ 添加成员</button>
          </div>
          <div class="space-y-4">
            <div v-for="(member, index) in portalData.team" :key="index" class="flex items-center gap-4 p-4 bg-stone-800/50 rounded-xl border border-stone-700">
              <AppAvatar :name="member.name || ''" :avatar-url="member.avatar || null" size-class="w-12 h-12" />
              <div class="flex-1 grid grid-cols-1 sm:grid-cols-2 gap-2">
                <input v-model="member.name" type="text" class="input text-sm" placeholder="名称" />
                <input v-model="member.role" type="text" class="input text-sm" placeholder="角色" />
              </div>
              <label class="btn-secondary text-xs cursor-pointer">
                上传头像
                <input type="file" accept="image/*" class="hidden" @change="uploadTeamAvatar($event, index)" />
              </label>
              <button @click="removeTeamMember(index)" class="text-rose-400 hover:text-rose-300 p-2">删除</button>
            </div>
          </div>
          <button @click="savePortalConfig" class="btn-primary mt-4" :disabled="saving">保存管理团队</button>
        </div>

        <!-- 服务器特色 -->
        <div class="card p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-white">服务器特色</h3>
            <button @click="addFeature" class="btn-secondary text-sm">+ 添加特色</button>
          </div>
          <div class="space-y-4">
            <div v-for="(feature, index) in portalData.features" :key="index" class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-2">
                <div class="flex items-center gap-2">
                  <AppIcon :name="feature.icon || 'star'" class="w-7 h-7 shrink-0" />
                  <select v-model="feature.icon" class="input text-sm flex-1">
                    <option v-for="n in iconNames" :key="n" :value="n">{{ n }}</option>
                  </select>
                </div>
                <input v-model="feature.title" type="text" class="input text-sm" placeholder="标题" />
                <input v-model="feature.description" type="text" class="input text-sm" placeholder="描述" />
              </div>
              <button @click="removeFeature(index)" class="text-rose-400 hover:text-rose-300 text-sm mt-2">删除</button>
            </div>
          </div>
          <button @click="savePortalConfig" class="btn-primary mt-4" :disabled="saving">保存特色</button>
        </div>

        <!-- 时光照片墙(首页展示,玩家可留言) -->
        <div class="card p-6">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-lg font-semibold text-white">时光照片墙</h3>
            <button @click="addTimelineEvent" class="btn-secondary text-sm">+ 添加事件</button>
          </div>
          <p class="text-xs text-stone-500 mb-3">这里的内容与图片会展示在首页照片墙,玩家点击照片可放大并留言;分类用于首页筛选,可在下方添加</p>
          <div class="mb-4 flex flex-wrap items-center gap-2">
              <span class="text-xs text-stone-500">照片分类:</span>
              <span v-for="(t, ti) in portalData.photoTypes" :key="t"
                class="inline-flex items-center gap-1 px-2 py-1 rounded-lg bg-orange-500/10 text-orange-400 text-xs">
                {{ typeName(t) }}
                <button v-if="!['announcement', 'event', 'milestone'].includes(t)"
                  @click="portalData.photoTypes.splice(ti, 1)" class="hover:text-rose-400" title="删除分类">×</button>
              </span>
              <input v-model="newPhotoType" placeholder="添加分类" maxlength="12"
                class="input text-xs w-28 py-1" @keyup.enter="addPhotoType" />
              <button @click="addPhotoType" class="btn-secondary text-xs py-1 px-3">添加</button>
            </div>
          </div>
          <div class="space-y-4">
            <div v-for="(event, index) in portalData.timeline" :key="index" class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 mb-2">
                <input v-model="event.date" type="text" class="input text-sm" placeholder="日期 (如: 2024-01-01)" />
                <input v-model="event.title" type="text" class="input text-sm" placeholder="标题" />
              </div>
              <textarea v-model="event.description" class="input text-sm mb-2" rows="2" placeholder="描述"></textarea>
              <div class="flex items-center gap-2">
                <input v-model="event.image" type="text" class="input text-sm flex-1" placeholder="图片路径" />
                <label class="btn-secondary text-xs cursor-pointer">
                  上传图片
                  <input type="file" accept="image/*" class="hidden" @change="uploadTimelineImage($event, index)" />
                </label>
                <button @click="removeTimelineEvent(index)" class="text-rose-400 hover:text-rose-300 p-2">删除</button>
              </div>
            </div>
          </div>
          <button @click="savePortalConfig" class="btn-primary mt-4" :disabled="saving">保存照片墙</button>
        </div>

      <!-- 外观设置 Tab -->
      <div v-if="activeTab === 'settings' && !loading" class="card p-6 space-y-4">
        <h3 class="text-lg font-semibold text-white mb-4">外观设置</h3>
        <div>
          <label class="block text-sm text-stone-300 mb-1">背景图片</label>
          <div class="flex gap-2">
            <input v-model="bgImage" type="text" class="input flex-1" placeholder="/bg.webp" />
            <label class="btn-secondary cursor-pointer">
              上传
              <input type="file" accept="image/*" class="hidden" @change="uploadBgImage" />
            </label>
          </div>
        </div>
        <div>
          <label class="block text-sm text-stone-300 mb-1">透明度 ({{ bgOpacity }})</label>
          <input v-model.number="bgOpacity" type="range" min="0" max="1" step="0.05" class="w-full" />
        </div>
        <div>
          <label class="block text-sm text-stone-300 mb-1">模糊程度 ({{ bgBlur }}px)</label>
          <input v-model.number="bgBlur" type="range" min="0" max="30" step="1" class="w-full" />
        </div>
        <div>
          <label class="block text-sm text-stone-300 mb-1">公告内容</label>
          <input v-model="announcementEdit" type="text" class="input" placeholder="输入公告内容" />
        </div>
        <button @click="saveSettings" class="btn-primary" :disabled="saving">
          {{ saving ? '保存中...' : '保存设置' }}
        </button>
      </div>

      <!-- 系统设置（管理员名单 / 注册 / AI 评分 / 邀请 / 游戏 / 下载中心） -->
      <div v-if="activeTab === 'system' && !loading" class="space-y-6">
        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="users" class="w-5 h-5" /> 管理员名单</h3>
          <p class="text-xs text-stone-500">名单内的用户登录后即拥有管理权限。每行一个用户名;<span class="text-amber-400">注意不要移除你自己</span>。</p>
          <textarea v-model="adminsStr" rows="3" class="input w-full font-mono text-sm" placeholder="管理员用户名,每行一个"></textarea>
          <button @click="saveAdmins" class="btn-primary text-sm" :disabled="savingAdmins">
            {{ savingAdmins ? '保存中...' : '保存管理员名单' }}
          </button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="shield-check" class="w-5 h-5" /> 注册守则</h3>
          <p class="text-xs text-stone-500">玩家注册后、ID 验证前需阅读该文档并同意(强制倒计时)。留空 = 不启用。</p>
          <div>
            <label class="block text-xs text-stone-500 mb-1">守则文档(来自文档中心)</label>
            <select v-model="rulesCfg.doc" class="input w-full text-sm">
              <option value="">未启用</option>
              <option v-for="o in rulesDocOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div class="flex items-center gap-2 text-sm text-stone-300">
            强制阅读 <input v-model.number="rulesCfg.seconds" type="number" min="0" class="input w-20 text-center" /> 秒
          </div>
          <button @click="saveRules" class="btn-primary text-sm">保存注册守则设置</button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="user" class="w-5 h-5" /> 注册设置</h3>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
              <input type="checkbox" v-model="sysCfg.requireEmailCode" class="accent-orange-500" /> 需要邮箱验证码
            </label>
            <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
              <input type="checkbox" v-model="sysCfg.captchaEnabled" class="accent-orange-500" /> 需要图形验证码
            </label>
            <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
              <input type="checkbox" v-model="sysCfg.autoApprove" class="accent-orange-500" /> 自动通过审核
            </label>
            <div class="flex items-center gap-2 text-sm text-stone-300">
              单邮箱上限 <input v-model.number="sysCfg.maxAccountsPerEmail" type="number" min="1" class="input w-20 text-center" />
            </div>
          </div>
          <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
            <input type="checkbox" v-model="sysCfg.domainWhitelistEnabled" class="accent-orange-500" /> 启用邮箱域名白名单
          </label>
          <div v-if="sysCfg.domainWhitelistEnabled">
            <label class="block text-sm text-stone-300 mb-1">白名单域名（逗号分隔）</label>
            <input v-model="sysCfg.emailDomainWhitelistStr" class="input w-full" placeholder="qq.com, 163.com, gmail.com" />
          </div>
          <button @click="saveRegisterSettings" class="btn-primary text-sm">保存注册设置</button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="cpu" class="w-5 h-5" /> AI 评分设置</h3>
          <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
            <input type="checkbox" v-model="llmCfg.enabled" class="accent-orange-500" /> 启用 AI 自动评分
          </label>
          <template v-if="llmCfg.enabled">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div><label class="block text-xs text-stone-500 mb-1">API 地址</label>
                <input v-model="llmCfg.apiBase" class="input w-full" placeholder="https://api.deepseek.com/v1" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">API Key</label>
                <input v-model="llmCfg.apiKey" type="password" class="input w-full" :placeholder="llmCfg.hasApiKey ? '已配置（输入新值可覆盖）' : 'sk-...'" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">模型</label>
                <input v-model="llmCfg.model" class="input w-full" placeholder="deepseek-chat" /></div>
            </div>
            <div><label class="block text-xs text-stone-500 mb-1">系统提示词</label>
              <textarea v-model="llmCfg.systemPrompt" rows="2" class="input w-full resize-none"></textarea></div>
          </template>
          <button @click="saveLlmSettings" class="btn-primary text-sm">保存 AI 设置</button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="pencil-square" class="w-5 h-5" /> 问卷设置</h3>
          <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
            <input type="checkbox" v-model="questCfg.enabled" class="accent-orange-500" />
            启用入服问卷（关闭后注册用户直接进入审核）
          </label>
          <div class="flex items-center gap-2 text-sm text-stone-300">
            通过线 <input v-model.number="questCfg.passScore" type="number" min="0" max="100" class="input w-20 text-center" /> 分（百分制）
          </div>
          <button @click="saveQuestSettings" class="btn-primary text-sm">保存问卷设置</button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="envelope" class="w-5 h-5" /> 邀请设置</h3>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
            <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
              <input type="checkbox" v-model="inviteCfg.enabled" class="accent-orange-500" /> 启用邀请系统
            </label>
            <div class="flex items-center gap-2 text-sm text-stone-300">
              有效期 <input v-model.number="inviteCfg.codeExpiryDays" type="number" min="1" class="input w-20 text-center" /> 天
            </div>
            <div class="flex items-center gap-2 text-sm text-stone-300">
              每人上限 <input v-model.number="inviteCfg.maxInvitesPerUser" type="number" min="1" class="input w-20 text-center" /> 个
            </div>
          </div>
          <button @click="saveInviteSettings" class="btn-primary text-sm">保存邀请设置</button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="chat-bubble" class="w-5 h-5" /> QQ 互通（AstrBot）</h3>
          <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
            <input type="checkbox" v-model="astrbotCfg.enabled" class="accent-orange-500" /> 启用 AstrBot 集成（关闭时 /api/astrbot/** 全部 403）
          </label>
          <div>
            <label class="block text-xs text-stone-500 mb-1">API 令牌（请求头 X-API-Token）</label>
            <div class="flex gap-2">
              <input v-model="astrbotCfg.apiToken" type="text" class="input flex-1 font-mono"
                :placeholder="astrbotCfg.hasToken ? '已配置（输入新值可覆盖）' : '留空则机器人无法通过鉴权'" />
              <button @click="genAstrbotToken" class="btn-secondary text-sm whitespace-nowrap">生成</button>
            </div>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">群绑定（与 AstrBot 插件 forward_groups 一致的群号）</label>
            <textarea v-model="astrbotCfg.groupBindingsStr" rows="4" class="input w-full font-mono text-sm resize-y"
              placeholder='[{"group": 697093624, "mode": "all", "prefix": "#", "forward_join_quit": true}]'></textarea>
            <div class="text-xs text-stone-600 mt-1">mode: all=全部转发进服, prefix=仅带前缀消息进服; forward_join_quit=进退服是否发群</div>
          </div>
          <div class="text-xs text-stone-500">在 AstrBot 插件中配置同一令牌与后端地址（http://主机:18898）即可对接；设计见 docs/ASTRBOT_PLAN.md。</div>
          <button @click="saveAstrbotSettings" class="btn-primary text-sm">保存 QQ 互通设置</button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="shield-check" class="w-5 h-5" /> 游戏设置</h3>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div><label class="block text-xs text-stone-500 mb-1">注册页地址</label>
              <input v-model="gameCfg.webRegisterUrl" class="input w-full" placeholder="https://..." /></div>
            <div><label class="block text-xs text-stone-500 mb-1">基岩版前缀</label>
              <input v-model="gameCfg.bedrockPrefix" class="input w-full" placeholder="." /></div>
          </div>
          <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
            <input type="checkbox" v-model="gameCfg.bedrockEnabled" class="accent-orange-500" /> 启用基岩版支持
          </label>
          <button @click="saveGameSettings" class="btn-primary text-sm">保存游戏设置</button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="archive-box" class="w-5 h-5" /> 下载中心</h3>
          <div class="text-xs text-stone-500">以 JSON 格式管理下载条目</div>
          <textarea v-model="downloadsJson" rows="5" class="input w-full font-mono text-sm resize-y"
            placeholder='{"client": {"title": "客户端", "url": "..."}}'></textarea>
          <button @click="saveDownloads" class="btn-primary text-sm">保存下载中心</button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="shield-check" class="w-5 h-5" /> 账号安全</h3>
          <label class="flex items-center gap-2 text-sm text-stone-300 cursor-pointer">
            <input type="checkbox" v-model="securityCfg.admin2faRequired" class="accent-orange-500" />
            管理员强制两步验证(开启后,名单内管理员未绑定 2FA 时登录会被引导强制绑定)
          </label>
          <button @click="saveSecurity" class="btn-primary text-sm">保存安全设置</button>
        </div>

        <WebhookSettingsCard />
      </div>

      <!-- 公告管理 Tab -->
      <div v-if="activeTab === 'announcements' && !loading" class="space-y-6">
        <div class="card p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="chat-bubble" class="w-5 h-5" /> 资讯中心</h3>
            <button @click="addNews" class="btn-secondary text-sm">+ 添加资讯</button>
          </div>
          <div v-if="newsList.length === 0" class="text-sm text-stone-500">暂无资讯,点右上角添加</div>
          <div class="space-y-4">
            <div v-for="(n, i) in newsList" :key="n.id" class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 mb-2">
                <input v-model="n.title" class="input text-sm" placeholder="标题" />
                <input v-model="n.date" type="date" class="input text-sm" />
              </div>
              <div class="flex items-center gap-4 mb-2 text-xs text-stone-400">
                <label class="flex items-center gap-1.5 cursor-pointer">
                  <input type="checkbox" v-model="n.pinned" class="accent-orange-500" /> 置顶
                </label>
                <label class="flex items-center gap-1.5 cursor-pointer">
                  <input type="checkbox" v-model="n.isDraft" class="accent-orange-500" /> 草稿(不展示)
                </label>
                <label class="flex items-center gap-1.5">
                  定时发布 <input v-model="n.publishAt" type="datetime-local" class="input text-xs py-1 w-48" />
                </label>
              </div>
              <textarea v-model="n.content" class="input text-sm" rows="5" placeholder="内容(支持 Markdown)"></textarea>
              <div class="text-right mt-2"><button @click="newsList.splice(i, 1)" class="text-rose-400 hover:text-rose-300 text-sm">删除</button></div>
            </div>
          </div>
          <button @click="saveAnnouncements" class="btn-primary mt-4" :disabled="savingAnnouncements">
            {{ savingAnnouncements ? '保存中...' : '保存资讯中心' }}
          </button>
        </div>

        <div class="card p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-white flex items-center gap-2"><AppIcon name="document-text" class="w-5 h-5" /> 更新日志</h3>
            <button @click="addChangelog" class="btn-secondary text-sm">+ 添加版本记录</button>
          </div>
          <div v-if="changelogList.length === 0" class="text-sm text-stone-500">暂无版本记录</div>
          <div class="space-y-4">
            <div v-for="(c, i) in changelogList" :key="c.id" class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 mb-2">
                <input v-model="c.version" class="input text-sm" placeholder="版本(如: v1.1.0)" />
                <input v-model="c.date" type="date" class="input text-sm" />
              </div>
              <textarea v-model="c.content" class="input text-sm" rows="5" placeholder="更新内容(支持 Markdown,一行一条)"></textarea>
              <div class="text-right mt-2"><button @click="changelogList.splice(i, 1)" class="text-rose-400 hover:text-rose-300 text-sm">删除</button></div>
            </div>
          </div>
          <button @click="saveAnnouncements" class="btn-primary mt-4" :disabled="savingAnnouncements">
            {{ savingAnnouncements ? '保存中...' : '保存更新日志' }}
          </button>
        </div>
      </div>

      <!-- 文档管理 Tab -->
      <div v-if="activeTab === 'docs' && !loading" class="grid grid-cols-1 lg:grid-cols-[280px_1fr] gap-6 items-start">
        <!-- 左:文档列表 -->
        <div class="card p-4">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-semibold text-white">文档列表</h3>
            <button @click="newDoc" class="btn-secondary text-xs">+ 新建文档</button>
          </div>
          <div v-for="cat in docsData.categories" :key="cat.name" class="mb-3">
            <div class="text-xs text-stone-500 mb-1 px-1">{{ cat.displayName || cat.name }}</div>
            <button v-for="f in cat.files" :key="f"
              class="w-full text-left px-2 py-1.5 rounded-lg text-sm transition-colors"
              :class="docForm.category === cat.name && docForm.filename === f ? 'text-orange-400 bg-orange-500/10' : 'text-stone-400 hover:text-white hover:bg-white/5'"
              @click="openDoc(cat.name, f)">
              {{ f.replace(/\.md$/, '') }}
            </button>
          </div>
          <div v-if="docsData.uncategorized.length" class="mb-3">
            <div class="text-xs text-stone-500 mb-1 px-1">未分类</div>
            <button v-for="f in docsData.uncategorized" :key="f.filename"
              class="w-full text-left px-2 py-1.5 rounded-lg text-sm transition-colors"
              :class="!docForm.category && docForm.filename === f.filename ? 'text-orange-400 bg-orange-500/10' : 'text-stone-400 hover:text-white hover:bg-white/5'"
              @click="openDoc(null, f.filename)">
              {{ f.title }}
            </button>
          </div>
        </div>

        <!-- 右:编辑器 + 实时预览 -->
        <div class="card p-4" v-if="docForm.isNew || docForm.filename">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 mb-2">
            <input v-model="docForm.title" class="input text-sm" placeholder="标题(即文件名)" />
            <select v-model="docForm.category" class="input text-sm">
              <option value="">未分类</option>
              <option v-for="cat in docsData.categories" :key="cat.name" :value="cat.name">{{ cat.displayName || cat.name }}</option>
            </select>
          </div>
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <textarea v-model="docForm.content" class="input text-sm font-mono" rows="18"
              placeholder="Markdown 内容…"></textarea>
            <div class="rounded-xl bg-stone-900/60 border border-stone-700 p-4 overflow-y-auto max-h-[60vh]">
              <div class="prose prose-invert max-w-none text-sm" v-html="docPreview"></div>
            </div>
          </div>
          <div class="flex gap-2 mt-3">
            <button @click="saveDoc" class="btn-primary text-sm" :disabled="!docForm.title || savingDocs">
              {{ savingDocs ? '保存中...' : (docForm.isNew ? '创建文档' : '保存修改') }}
            </button>
            <button v-if="!docForm.isNew" @click="deleteDocCurrent" class="btn-secondary text-sm text-rose-400">删除文档</button>
          </div>
        </div>
        <div class="card p-10 text-center text-stone-500 text-sm" v-else>
          从左侧选择一篇文档,或点击「+ 新建文档」开始撰写(支持 Markdown,右侧实时预览)
        </div>
      </div>

      <!-- 任务与兑换 Tab -->
      <div v-if="activeTab === 'taskshop' && !loading" class="space-y-6">
        <div class="card p-6 space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-semibold text-white">任务配置</h3>
            <button @click="addTask" class="btn-secondary text-sm">+ 添加任务</button>
          </div>
          <p class="text-xs text-stone-500">类型:signin(签到,渠道可选)/ playtime(在线时长,秒)/ streak(连续登录天数)/ full_attendance(满勤,target 填 0 自动=当月天数)</p>
          <div v-for="(t, i) in tasksCfg.tasks" :key="t.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 space-y-3">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div><label class="block text-xs text-stone-500 mb-1">任务 ID</label><input v-model="t.id" class="input text-sm" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">名称</label><input v-model="t.name" class="input text-sm" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">类型</label>
                <select v-model="t.type" class="input text-sm">
                  <option value="signin">签到</option><option value="playtime">在线时长</option>
                  <option value="streak">连续登录</option><option value="full_attendance">满勤</option>
                </select></div>
              <div><label class="block text-xs text-stone-500 mb-1">周期</label>
                <select v-model="t.period" class="input text-sm">
                  <option value="daily">每日</option><option value="weekly">每周</option><option value="monthly">每月</option>
                </select></div>
              <div v-if="t.type === 'signin'"><label class="block text-xs text-stone-500 mb-1">签到渠道</label>
                <select v-model="t.channel" class="input text-sm"><option value="web">网页</option><option value="game">游戏内</option></select></div>
              <div><label class="block text-xs text-stone-500 mb-1">{{ t.type === 'playtime' ? '目标(秒)' : '目标值' }}</label>
                <input v-model.number="t.target" type="number" min="1" class="input text-sm" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">积分奖励</label><input v-model.number="t.points" type="number" min="0" class="input text-sm" /></div>
              <label class="flex items-center gap-2 text-sm text-stone-300 self-end"><input type="checkbox" v-model="t.enabled" class="accent-orange-500" /> 启用</label>
            </div>
            <div><label class="block text-xs text-stone-500 mb-1">描述</label><input v-model="t.desc" class="input text-sm" /></div>
            <button @click="tasksCfg.tasks.splice(i, 1)" class="text-xs text-rose-400 hover:text-rose-300">删除该任务</button>
          </div>
          <button @click="saveTasks" class="btn-primary text-sm">保存任务配置</button>
        </div>

        <div class="card p-6 space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-semibold text-white">兑换商店</h3>
            <button @click="addReward" class="btn-secondary text-sm">+ 添加兑换项</button>
          </div>
          <p class="text-xs text-stone-500">发放指令每行一条,{player} 会替换为领取玩家名;硬币类用经济插件的发放指令(如 eco give {player} 1000)。</p>
          <div v-for="(r, i) in shopCfg.rewards" :key="r.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 space-y-3">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div><label class="block text-xs text-stone-500 mb-1">兑换 ID</label><input v-model="r.id" class="input text-sm" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">名称</label><input v-model="r.name" class="input text-sm" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">所需积分</label><input v-model.number="r.cost" type="number" min="0" class="input text-sm" /></div>
              <label class="flex items-center gap-2 text-sm text-stone-300 self-end"><input type="checkbox" v-model="r.enabled" class="accent-orange-500" /> 上架</label>
            </div>
            <div><label class="block text-xs text-stone-500 mb-1">描述</label><input v-model="r.desc" class="input text-sm" /></div>
            <div><label class="block text-xs text-stone-500 mb-1">发放指令(每行一条)</label>
              <textarea v-model="r.commandsText" rows="2" class="input text-sm font-mono"></textarea></div>
            <button @click="shopCfg.rewards.splice(i, 1)" class="text-xs text-rose-400 hover:text-rose-300">删除该兑换项</button>
          </div>
          <button @click="saveShop" class="btn-primary text-sm">保存兑换商店</button>
        </div>
      </div>

      <!-- 奖励发放 Tab(礼包 + 指令包) -->
      <div v-if="activeTab === 'rewards' && !loading" class="space-y-6">
        <div class="card p-6 space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-semibold text-white">奖励礼包</h3>
            <button @click="showCreateKit = !showCreateKit" class="btn-secondary text-sm">+ 创建礼包</button>
          </div>
          <p class="text-xs text-stone-500">流程:创建礼包 → 服内管理员把物品放进背包执行 /xmw kit save &lt;礼包名&gt; → 状态变「可发放」→ 在下方发放。礼包内容 = 采集的物品 + 每个礼包可配的附加指令(可混合)。</p>
          <div v-if="showCreateKit" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 space-y-3">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div><label class="block text-xs text-stone-500 mb-1">礼包名(2-32 位中文/字母/数字/_/-)</label><input v-model="newKit.name" class="input text-sm" placeholder="如: 新手大礼包" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">备注</label><input v-model="newKit.note" class="input text-sm" /></div>
            </div>
            <button @click="createKit" class="btn-primary text-sm">创建</button>
          </div>
          <EmptyState v-if="rewardKits.length === 0" text="暂无礼包,点击右上角创建" />
          <div v-for="k in rewardKits" :key="k.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 space-y-3">
            <div class="flex items-center justify-between flex-wrap gap-2">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium text-stone-100">{{ k.name }}</span>
                <span class="text-xs px-2 py-0.5 rounded-full" :class="kitStatusClass(k.status)">{{ kitStatusText(k.status) }}</span>
              </div>
              <button @click="deleteKit(k)" class="text-xs text-rose-400 hover:text-rose-300">删除</button>
            </div>
            <div class="text-xs text-stone-500 space-y-1">
              <div v-if="k.summary">内容概要:{{ k.summary }}</div>
              <div v-if="k.status === 'capturing'">待采集 — 服内管理员把物品放进背包后执行 <code class="text-orange-400">/xmw kit save {{ k.name }}</code></div>
              <div v-if="k.capturedBy">采集人:{{ k.capturedBy }} · {{ formatTs(k.capturedAt) }}</div>
            </div>
            <div><label class="block text-xs text-stone-500 mb-1">附加指令(每行一条,{player} 替换为领取玩家;与物品一起发放)</label>
              <textarea v-model="k.commandsText" rows="2" class="input text-sm font-mono" placeholder="如: eco give {player} 100"></textarea></div>
            <button @click="saveKitCommands(k)" class="btn-secondary text-xs">保存附加指令</button>
          </div>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white">发放奖励</h3>
          <p class="text-xs text-stone-500">奖励以游戏内邮件发放:玩家进服输入 /mail 领取(礼包物品直接进背包;背包空间不足会提示清理后重试,邮件保留不丢失)。</p>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="block text-xs text-stone-500 mb-1">发放对象</label>
              <div class="flex items-center gap-4 h-[38px]">
                <label class="flex items-center gap-2 text-sm text-stone-300"><input type="radio" value="single" v-model="sendMode" class="accent-orange-500" /> 指定玩家</label>
                <label class="flex items-center gap-2 text-sm text-stone-300"><input type="radio" value="all" v-model="sendMode" class="accent-orange-500" /> 全员(已通过白名单)</label>
              </div>
            </div>
            <div v-if="sendMode === 'single'"><label class="block text-xs text-stone-500 mb-1">玩家名(多个用英文逗号分隔)</label><input v-model="sendForm.usernamesStr" class="input text-sm" /></div>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div><label class="block text-xs text-stone-500 mb-1">奖励来源</label>
              <select v-model="sendForm.source" class="input text-sm">
                <option value="kit">奖励礼包</option>
                <option value="commands">指令包</option>
              </select></div>
            <div v-if="sendForm.source === 'kit'"><label class="block text-xs text-stone-500 mb-1">选择礼包</label>
              <select v-model.number="sendForm.kitId" class="input text-sm">
                <option v-for="k in readyKits" :key="k.id" :value="k.id">{{ k.name }}(可发放)</option>
              </select></div>
          </div>
          <div v-if="sendForm.source === 'commands'"><label class="block text-xs text-stone-500 mb-1">奖励指令(每行一条,{player} 替换为领取玩家)</label>
            <textarea v-model="sendForm.commandsText" rows="3" class="input text-sm font-mono" placeholder="如: eco give {player} 1000"></textarea></div>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div><label class="block text-xs text-stone-500 mb-1">邮件标题(留空用默认)</label><input v-model="sendForm.title" class="input text-sm" /></div>
            <div><label class="block text-xs text-stone-500 mb-1">备注(留空用默认)</label><input v-model="sendForm.note" class="input text-sm" /></div>
          </div>
          <p v-if="sendMode === 'all' && sendForm.source === 'kit'" class="text-xs text-amber-400">⚠ 全员发放礼包会为每名玩家快照一份完整内容,人数较多时注意数据库占用。</p>
          <div class="flex items-center gap-3">
            <button @click="doSendReward" :disabled="sendingReward" class="btn-primary text-sm">{{ sendingReward ? '发放中...' : '发放' }}</button>
            <span v-if="sendResult" class="text-sm" :class="sendResult.ok ? 'text-emerald-400' : 'text-rose-400'">{{ sendResult.text }}</span>
          </div>
        </div>
      </div>

            <!-- 称号与成就 Tab -->
      <div v-if="activeTab === 'titles' && !loading" class="space-y-6">
        <div class="card p-6 space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-semibold text-white">称号定义</h3>
            <button @click="addTitleDef" class="btn-secondary text-sm">+ 添加称号</button>
          </div>
          <p class="text-xs text-stone-500">code 用于插件变量与奖励关联;「颜色(网页)」用于网页显示,「游戏内颜色」用于聊天/Tab/GUI 显示,留空则跟随网页色(十六进制)。</p>
          <div v-for="(t, i) in titlesCfg.titles" :key="t.code" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 space-y-3">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div><label class="block text-xs text-stone-500 mb-1">代码(code)</label><input v-model="t.code" class="input text-sm font-mono" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">名称</label><input v-model="t.name" class="input text-sm" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">颜色(网页)</label><input v-model="t.color" class="input text-sm font-mono" placeholder="#fbbf24" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">游戏内颜色(留空跟随网页色)</label><input v-model="t.gameColor" class="input text-sm font-mono" placeholder="#fbbf24" /></div>
              <label class="flex items-center gap-2 text-sm text-stone-300 self-end"><input type="checkbox" v-model="t.enabled" class="accent-orange-500" /> 启用</label>
            </div>
            <div><label class="block text-xs text-stone-500 mb-1">描述</label><input v-model="t.desc" class="input text-sm" /></div>
            <button @click="titlesCfg.titles.splice(i, 1)" class="text-xs text-rose-400 hover:text-rose-300">删除该称号</button>
          </div>
          <button @click="saveTitles" class="btn-primary text-sm">保存称号定义</button>
        </div>

        <div class="card p-6 space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-semibold text-white">成就定义</h3>
            <button @click="addAchievementDef" class="btn-secondary text-sm">+ 添加成就</button>
          </div>
          <p class="text-xs text-stone-500">指标:playtime_total=累计在线秒数 / register_days=注册天数 / invite_count=成功邀请数 / points_total=累计获得积分。达标自动授予奖励称号并通知玩家。</p>
          <div v-for="(a, i) in achCfg.achievements" :key="a.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 space-y-3">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div><label class="block text-xs text-stone-500 mb-1">成就 ID</label><input v-model="a.id" class="input text-sm font-mono" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">名称</label><input v-model="a.name" class="input text-sm" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">指标</label>
                <select v-model="a.metric" class="input text-sm">
                  <option value="playtime_total">累计在线时长(秒)</option>
                  <option value="register_days">注册天数</option>
                  <option value="invite_count">成功邀请数</option>
                  <option value="points_total">累计获得积分</option>
                </select></div>
              <div><label class="block text-xs text-stone-500 mb-1">门槛</label><input v-model.number="a.target" type="number" min="1" class="input text-sm" /></div>
              <div><label class="block text-xs text-stone-500 mb-1">奖励称号</label>
                <select v-model="a.reward" class="input text-sm">
                  <option value="">无</option>
                  <option v-for="t in titlesCfg.titles" :key="t.code" :value="t.code">{{ t.name }}({{ t.code }})</option>
                </select></div>
              <label class="flex items-center gap-2 text-sm text-stone-300 self-end"><input type="checkbox" v-model="a.enabled" class="accent-orange-500" /> 启用</label>
            </div>
            <div><label class="block text-xs text-stone-500 mb-1">描述</label><input v-model="a.desc" class="input text-sm" /></div>
            <button @click="achCfg.achievements.splice(i, 1)" class="text-xs text-rose-400 hover:text-rose-300">删除该成就</button>
          </div>
          <button @click="saveAchievements" class="btn-primary text-sm">保存成就定义</button>
        </div>

        <div class="card p-6 space-y-4">
          <h3 class="text-lg font-semibold text-white">手动授予 / 撤销</h3>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-end">
            <div><label class="block text-xs text-stone-500 mb-1">玩家用户名</label><input v-model="grantForm.username" class="input text-sm" /></div>
            <div><label class="block text-xs text-stone-500 mb-1">称号</label>
              <select v-model="grantForm.code" class="input text-sm">
                <option value="">选择称号…</option>
                <option v-for="t in titlesCfg.titles" :key="t.code" :value="t.code">{{ t.name }}({{ t.code }})</option>
              </select></div>
            <div class="flex gap-2">
              <button @click="doGrant" class="btn-primary text-sm flex-1">授予</button>
              <button @click="doRevoke" class="btn-secondary text-sm flex-1">撤销</button>
            </div>
          </div>
        </div>
      </div>

<!-- 审核管理 Tab -->
      <div v-if="activeTab === 'review' && !loading" class="card p-6">
        <h3 class="text-lg font-semibold text-white mb-4">审核管理</h3>
        <EmptyState v-if="pendingUsers.length === 0" icon="check-circle" text="暂无待审核玩家" />
        <div v-else class="space-y-4">
          <div v-for="user in paginatedPendingUsers" :key="user.username" class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <div class="font-medium text-white text-lg">{{ user.username }}</div>
                <div class="text-sm text-stone-400">邮箱：{{ user.email }}</div>
                <div class="text-sm text-stone-400">问卷得分：{{ user.questionnaireScore }} 分</div>
              </div>
              <div class="flex gap-2">
                <button @click="viewQuestionnaire(user)" class="btn-secondary text-sm">详情</button>
                <button @click="confirmApprove(user.username)" class="btn-primary text-sm">通过</button>
                <button @click="confirmReject(user.username)" class="btn-secondary text-sm text-rose-400 border-rose-500/30 hover:bg-rose-500/10">拒绝</button>
              </div>
            </div>
          </div>
          <AppPagination :page="pendingPage" :pages="totalPendingPages" @change="pendingPage = $event" />
        </div>
      </div>

      <!-- 玩家管理 Tab -->
      <div v-if="activeTab === 'players' && !loading" class="card p-6">
        <h3 class="text-lg font-semibold text-white mb-4">玩家管理</h3>
        <div class="flex flex-col sm:flex-row gap-4 mb-4">
          <input v-model="searchQuery" type="text" class="input flex-1" placeholder="搜索玩家..." />
          <select v-model="statusFilter" class="input w-full sm:w-40">
            <option value="">全部状态</option>
            <option value="pending">待答题</option>
            <option value="pending_review">待审核</option>
            <option value="approved">已通过</option>
            <option value="rejected">未通过</option>
            <option value="banned">已封禁</option>
          </select>
          <div class="flex gap-2">
            <button @click="exportUsers" class="btn-secondary text-sm">
              <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              导出 CSV
            </button>
          </div>
        </div>
        
        <!-- 批量操作栏 -->
        <div v-if="selectedUsers.length > 0" class="flex items-center gap-4 mb-4 p-3 bg-orange-500/10 rounded-xl">
          <span class="text-sm text-orange-400">已选择 {{ selectedUsers.length }} 个用户</span>
          <button @click="batchApprove" class="text-sm text-emerald-400 hover:text-emerald-300">批量批准</button>
          <button @click="batchReject" class="text-sm text-amber-400 hover:text-amber-300">批量拒绝</button>
          <button @click="batchBan" class="text-sm text-red-400 hover:text-red-300">批量封禁</button>
          <button @click="batchDelete" class="text-sm text-red-400 hover:text-red-300">批量删除</button>
          <button @click="selectedUsers = []" class="text-sm text-stone-400 hover:text-stone-300 ml-auto">取消选择</button>
        </div>
        <div class="overflow-x-auto">
          <table class="table min-w-[900px]">
            <thead>
              <tr>
                <th class="w-10">
                  <input type="checkbox" @change="toggleAllUsers" :checked="allUsersSelected" class="accent-orange-500" />
                </th>
                <th>用户名</th>
                <th>Minecraft ID</th>
                <th>基岩版 ID</th>
                <th>角色</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in paginatedUsers" :key="user.username">
                <td>
                  <input type="checkbox" :value="user.username" v-model="selectedUsers" class="accent-orange-500" />
                </td>
                <td class="text-white font-medium">{{ user.username }}</td>
                <td>
                  <span v-if="user.minecraftName" class="text-emerald-400 text-sm">{{ user.minecraftName }}</span>
                  <span v-else class="text-stone-500 text-sm">未验证</span>
                </td>
                <td>
                  <span v-if="user.bedrockVerified" class="text-emerald-400 text-sm">{{ user.bedrockName }}</span>
                  <span v-else-if="user.bedrockName" class="text-amber-400 text-sm">{{ user.bedrockName }} (待验证)</span>
                  <span v-else class="text-stone-500 text-sm">未设置</span>
                </td>
                <td><span v-if="user.isAdmin" class="badge-info text-xs">管理员</span><span v-else class="text-stone-400 text-sm">玩家</span></td>
                <td>
                  <select 
                    v-if="!user.isAdmin" 
                    :value="user.status" 
                    @change="changeUserStatus(user.username, ($event.target as HTMLSelectElement).value)"
                    class="input-sm text-xs py-1 px-2"
                    :class="getStatusClass(user.status)"
                  >
                    <option value="pending">待验证</option>
                    <option value="pending_verify">待ID验证</option>
                    <option value="pending_review">待审核</option>
                    <option value="approved">已通过</option>
                    <option value="rejected">已拒绝</option>
                    <option value="banned">已封禁</option>
                  </select>
                  <span v-else :class="getStatusClass(user.status)">{{ getStatusText(user.status) }}</span>
                </td>
                <td>
                  <div class="flex gap-2">
                    <button v-if="user.status !== 'banned' && !user.isAdmin" @click="confirmBan(user.username)" class="text-amber-400 hover:text-amber-300 text-sm font-medium">封禁</button>
                    <button v-if="user.status === 'banned'" @click="unbanUser(user.username)" class="text-sky-400 hover:text-sky-300 text-sm font-medium">解封</button>
                    <button @click="viewQuestionnaire(user)" class="text-orange-400 hover:text-orange-300 text-sm font-medium">问卷</button>
                    <button v-if="!user.isAdmin && bedrockEnabled" @click="openBedrockModal(user)" class="text-blue-400 hover:text-blue-300 text-sm font-medium">基岩版</button>
                    <button v-if="!user.isAdmin" @click="confirmDelete(user.username)" class="text-red-400 hover:text-red-300 text-sm font-medium">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AppPagination :page="playerPage" :pages="totalPlayerPages" @change="playerPage = $event" />
      </div>

      <!-- 统计分析 Tab -->
      <div v-if="activeTab === 'stats' && !loading" class="space-y-6">
        <!-- 总览 -->
        <div class="card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">数据总览</h3>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <StatCard :value="statsOverview.totalUsers || 0" label="总用户数" tone="orange" />
            <StatCard :value="statsOverview.approvedUsers || 0" label="已通过" tone="emerald" />
            <StatCard :value="statsOverview.pendingUsers || 0" label="待审核" tone="amber" />
            <StatCard :value="statsOverview.bannedUsers || 0" label="已封禁" tone="rose" />
          </div>
        </div>
        
        <!-- 注册统计 -->
        <div class="card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">注册统计</h3>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <StatCard :value="statsOverview.todayRegistrations || 0" label="今日注册" tone="blue" />
            <StatCard :value="statsOverview.weekRegistrations || 0" label="本周注册" tone="purple" />
            <StatCard :value="statsOverview.monthRegistrations || 0" label="本月注册" tone="cyan" />
          </div>
        </div>
        
        <!-- 问卷统计 -->
        <div class="card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">问卷统计</h3>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <StatCard :value="(statsOverview.questionnairePassRate?.toFixed(1) || 0) + '%'" label="通过率" tone="emerald" />
            <StatCard :value="statsOverview.averageScore?.toFixed(1) || 0" label="平均分" tone="orange" />
            <StatCard :value="(statsOverview.questionnairePassed || 0) + (statsOverview.questionnaireFailed || 0)" label="总提交数" />
          </div>
        </div>

        <!-- 服务器资源监控 -->
        <ServerMetricsPanel />
      </div>

      <!-- 服务器管理 Tab -->
      <div v-if="activeTab === 'servers' && !loading">
        <ServerManageTab />
      </div>

      <!-- 论坛管理 Tab -->
      <div v-if="activeTab === 'forum' && !loading">
        <ForumManageTab />
      </div>

      <!-- 投票管理 Tab -->
      <div v-if="activeTab === 'polls' && !loading">
        <PollManageTab />
      </div>

      <!-- 反馈工单 Tab -->
      <div v-if="activeTab === 'feedback' && !loading">
        <FeedbackManageTab />
      </div>

      <!-- 操作日志 Tab -->
      <div v-if="activeTab === 'audits' && !loading" class="card p-6">
        <h3 class="text-lg font-semibold text-white mb-4">操作日志</h3>
        <div class="overflow-x-auto">
          <table class="table min-w-[600px]">
            <thead><tr><th>时间</th><th>操作</th><th>操作者</th><th>目标</th><th>详情</th></tr></thead>
            <tbody>
              <tr v-for="audit in paginatedAuditLogs" :key="audit.id">
                <td class="text-stone-400 text-sm whitespace-nowrap">{{ formatTime(audit.timestamp) }}</td>
                <td><span class="badge-info text-xs">{{ auditActionLabel(audit.action) }}</span></td>
                <td class="text-white">{{ audit.operator }}</td>
                <td class="text-stone-300">{{ audit.target }}</td>
                <td class="text-stone-400 text-sm">{{ audit.detail }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 申诉管理 Tab -->
      <div v-if="activeTab === 'appeals' && !loading" class="card p-6">
        <h3 class="text-lg font-semibold text-white mb-4">申诉管理</h3>
        <EmptyState v-if="appeals.length === 0" icon="envelope" text="暂无申诉" />
        <div v-else class="space-y-4">
          <div v-for="appeal in paginatedAppeals" :key="appeal.id" class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
            <div class="flex items-start justify-between mb-3">
              <div>
                <div class="font-medium text-white">{{ appeal.username }}</div>
                <div class="text-xs text-stone-500">{{ formatTime(appeal.createdAt) }}</div>
              </div>
              <span :class="appeal.status === 'pending' ? 'badge-warning' : appeal.status === 'approved' ? 'badge-success' : 'badge-danger'">
                {{ appeal.status === 'pending' ? '待处理' : appeal.status === 'approved' ? '已通过' : '已拒绝' }}
              </span>
            </div>
            <p class="text-stone-300 text-sm mb-3">{{ appeal.reason }}</p>
            <div v-if="appeal.status === 'pending'" class="flex gap-2">
              <button @click="approveAppeal(appeal.id)" class="btn-primary text-sm">通过</button>
              <button @click="rejectAppeal(appeal.id)" class="btn-secondary text-sm">拒绝</button>
            </div>
            <div v-if="appeal.adminReply" class="mt-3 p-3 bg-stone-700/50 rounded-lg">
              <p class="text-xs text-stone-400 mb-1">管理员回复：</p>
              <p class="text-stone-300 text-sm">{{ appeal.adminReply }}</p>
            </div>
          </div>
        </div>
        <AppPagination :page="appealPage" :pages="totalAppealPages" @change="appealPage = $event" />
      </div>

      <!-- 问卷导出按钮 -->
      <div v-if="activeTab === 'questionnaires' && !loading" class="card p-4 mb-6 flex gap-2">
        <span class="text-sm text-stone-400 flex items-center">问卷数据:</span>
        <a href="/api/admin/export/questionnaires?format=csv" class="btn-secondary text-sm">导出 CSV</a>
        <a href="/api/admin/export/questionnaires?format=json" class="btn-secondary text-sm">导出 JSON</a>
      </div>

      <!-- 问卷管理 Tab -->
      <div v-if="activeTab === 'questionnaires' && !loading" class="space-y-6">
      <QuestionnaireEditor @changed="loadQuestionnaires" />

      <div class="card p-6">
        <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2"><AppIcon name="document-text" class="w-5 h-5" /> 问卷历史</h3>
        <div v-if="questionnaires.length === 0" class="text-center py-12 text-stone-500">
          <AppIcon name="pencil-square" class="w-10 h-10 mx-auto mb-2 text-stone-500" />
          <div>暂无问卷记录</div>
        </div>
        <div v-else class="overflow-x-auto">
          <table class="table min-w-[700px]">
            <thead><tr><th>用户名</th><th>分数</th><th>状态</th><th>提交时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="q in questionnaires" :key="q.username">
                <td class="text-white font-medium">{{ q.username }}</td>
                <td><span class="text-orange-400 font-bold">{{ q.questionnaireScore || 0 }}</span></td>
                <td>
                  <span :class="q.questionnairePassed ? 'badge-success' : 'badge-danger'">
                    {{ q.questionnairePassed ? '已通过' : '未通过' }}
                  </span>
                </td>
                <td class="text-stone-400 text-sm">{{ formatTime(q.questionnaireScoredAt) }}</td>
                <td>
                  <button @click="viewQuestionnaire(q)" class="text-orange-400 hover:text-orange-300 text-sm font-medium">详情</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      </div>

      <!-- 验证页面配置 Tab -->
      <div v-if="activeTab === 'migration' && !loading" class="card p-6 space-y-6">
        <div>
          <h3 class="text-lg font-semibold text-white mb-2 flex items-center gap-2"><AppIcon name="archive-box" class="w-5 h-5" /> 旧版数据迁移</h3>
          <p class="text-sm text-stone-400">
            上传旧版 XMWhitelist 的 MySQL 导出文件（mysqldump 生成的 <code>.sql</code>），
            系统将自动导入 9 张旧表（用户/审计/邀请/通知/进服记录/申诉/村谱/公共机器）并转换为新数据结构。
          </p>
        </div>
        <div class="bg-amber-500/10 border border-amber-500/20 rounded-xl p-4 text-sm text-amber-300">
          <span class="inline-flex items-center gap-1.5"><AppIcon name="warning" class="w-4 h-4" /> 仅当系统内尚无用户数据时可执行导入（幂等保护）；旧密码将原样保留，玩家可继续用旧密码登录。</span>
          操作前请确认已备份目标数据库。
        </div>
        <div class="flex items-center gap-3">
          <input ref="migrationFileInput" type="file" accept=".sql"
            class="flex-1 text-sm text-stone-300 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0
                   file:bg-orange-500/15 file:text-orange-400 hover:file:bg-orange-500/25 file:cursor-pointer"
            @change="onMigrationFileChange" />
          <button @click="uploadMigration" :disabled="!migrationFile || migrationLoading"
            class="px-5 py-2.5 rounded-xl bg-orange-500 text-white text-sm font-medium hover:bg-orange-600 disabled:opacity-40 disabled:cursor-not-allowed transition">
            {{ migrationLoading ? '导入中…' : '开始导入' }}
          </button>
        </div>
        <div v-if="migrationResult" class="bg-stone-800/60 rounded-xl p-4 text-sm">
          <div class="text-green-400 font-medium mb-2 flex items-center gap-1.5"><AppIcon name="check-circle" class="w-4 h-4" /> {{ migrationResult.message }}</div>
          <pre class="text-xs text-stone-300 whitespace-pre-wrap overflow-auto max-h-72">{{ JSON.stringify(migrationResult.data, null, 2) }}</pre>
        </div>
        <div v-if="migrationError" class="text-sm text-red-400">{{ migrationError }}</div>
      </div>

      <div v-if="activeTab === 'verify' && !loading" class="card p-6 space-y-6">
        <h3 class="text-lg font-semibold text-white mb-4">验证页面配置</h3>
        
        <div>
          <label class="block text-sm text-stone-300 mb-1">页面标题</label>
          <input v-model="verifyConfig.title" type="text" class="input w-full" placeholder="ID 验证" />
        </div>
        
        <div>
          <label class="block text-sm text-stone-300 mb-1">页面副标题</label>
          <input v-model="verifyConfig.subtitle" type="text" class="input w-full" placeholder="验证你的 Minecraft 账户" />
        </div>
        
        <div>
          <label class="block text-sm text-stone-300 mb-1">自定义说明文字</label>
          <textarea v-model="verifyConfig.instructions" class="input w-full h-24" placeholder="输入自定义说明文字..."></textarea>
        </div>
        
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm text-stone-300 mb-1">Java 版服务器地址</label>
            <input v-model="verifyConfig.javaServerAddress" type="text" class="input w-full" placeholder="mc.xmcraft.cn" />
          </div>
          <div>
            <label class="block text-sm text-stone-300 mb-1">Java 版端口</label>
            <input v-model.number="verifyConfig.javaServerPort" type="number" class="input w-full" placeholder="25565" />
          </div>
        </div>
        
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm text-stone-300 mb-1">基岩版服务器地址</label>
            <input v-model="verifyConfig.bedrockServerAddress" type="text" class="input w-full" placeholder="mc.xmcraft.cn" />
          </div>
          <div>
            <label class="block text-sm text-stone-300 mb-1">基岩版端口</label>
            <input v-model.number="verifyConfig.bedrockServerPort" type="number" class="input w-full" placeholder="19132" />
          </div>
        </div>
        
        <button @click="saveVerifyConfig" class="btn-primary" :disabled="saving">
          {{ saving ? '保存中...' : '保存验证页面配置' }}
        </button>
      </div>

      </div>
      </div>
    </div>
  </div>

    <!-- 确认对话框 -->
    <AppModal :open="showConfirmDialog" :title="confirmTitle" @close="showConfirmDialog = false">
      <p class="text-stone-400">{{ confirmMessage }}</p>
      <div v-if="showBanDaysInput" class="mt-4 space-y-3">
        <div>
          <label class="block text-xs text-stone-500 mb-1">封禁理由(可选,展示在封禁公示页)</label>
          <input v-model="banReason" type="text" class="input w-full" placeholder="如:破坏服务器环境" />
        </div>
        <div>
          <label class="block text-xs text-stone-500 mb-1">临时封禁天数</label>
          <input v-model.number="banDays" type="number" min="1" class="input w-full" placeholder="留空 = 永久封禁" />
        </div>
      </div>
      <template #footer>
        <button @click="showConfirmDialog = false" class="btn-secondary">取消</button>
        <button @click="executeConfirmAction" class="btn-primary">确认</button>
      </template>
    </AppModal>

    <!-- 删除用户确认对话框（两次确认） -->
    <AppModal :open="showDeleteDialog" :title="deleteStep === 1 ? '确认删除用户' : '最终确认'" @close="showDeleteDialog = false; deleteStep = 1">
      <template v-if="deleteStep === 1">
        <p class="text-stone-400 mb-4">你确定要删除用户 <span class="text-white font-medium">{{ deleteUsername }}</span> 吗？</p>
        <p class="text-red-400 text-sm mb-6">此操作不可撤销，用户的所有数据将被永久删除。</p>
      </template>
      <template v-else>
        <p class="text-stone-400 mb-4">请输入用户名 <span class="text-white font-medium">{{ deleteUsername }}</span> 以确认删除：</p>
        <input v-model="deleteConfirmInput" type="text" class="input w-full" :placeholder="deleteUsername" />
      </template>
      <template #footer>
        <button @click="showDeleteDialog = false; deleteStep = 1" class="btn-secondary">取消</button>
        <button v-if="deleteStep === 1" @click="deleteStep = 2" class="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded-xl">继续</button>
        <button v-else @click="executeDelete" class="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded-xl" :disabled="deleteConfirmInput !== deleteUsername">确认删除</button>
      </template>
    </AppModal>

    <!-- 基岩版 ID 设置弹窗 -->
    <AppModal :open="showBedrockModal" title="设置基岩版 ID" @close="showBedrockModal = false">
      <div class="space-y-4">
        <div>
          <label class="block text-sm text-stone-300 mb-1">玩家用户名</label>
          <input :value="bedrockTargetUser?.username" type="text" class="input w-full" disabled />
        </div>
        <div>
          <label class="block text-sm text-stone-300 mb-1">当前 Java 版 ID</label>
          <input :value="bedrockTargetUser?.minecraftName || '未验证'" type="text" class="input w-full" disabled />
        </div>
        <div>
          <label class="block text-sm text-stone-300 mb-1">基岩版用户名</label>
          <input v-model="bedrockNameInput" type="text" class="input w-full" placeholder="输入基岩版用户名（不含前缀）" />
          <p class="mt-1 text-xs text-stone-500">系统会自动添加 "." 前缀</p>
        </div>
        <div v-if="bedrockTargetUser?.bedrockName" class="p-3 bg-stone-800/50 rounded-xl">
          <p class="text-sm text-stone-400">当前基岩版 ID: <span class="text-white">{{ bedrockTargetUser.bedrockName }}</span></p>
          <p class="text-sm text-stone-400 mt-1">验证状态: <span :class="bedrockTargetUser.bedrockVerified ? 'text-emerald-400' : 'text-amber-400'">{{ bedrockTargetUser.bedrockVerified ? '已验证' : '待验证' }}</span></p>
        </div>
        <div v-if="bedrockVerifyMessage" class="p-3 rounded-xl" :class="bedrockVerifySuccess ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400'">
          {{ bedrockVerifyMessage }}
        </div>
      </div>
      <template #footer>
        <button @click="showBedrockModal = false" class="btn-secondary">取消</button>
        <button @click="setBedrockId" class="btn-primary" :disabled="!bedrockNameInput || bedrockLoading">
          {{ bedrockLoading ? '设置中...' : '设置' }}
        </button>
        <button v-if="bedrockTargetUser?.bedrockName && !bedrockTargetUser?.bedrockVerified" @click="verifyBedrockId" class="bg-emerald-500 hover:bg-emerald-600 text-white px-4 py-2 rounded-xl" :disabled="bedrockLoading">
          {{ bedrockLoading ? '验证中...' : '验证' }}
        </button>
      </template>
    </AppModal>

    <!-- 问卷详情弹窗 -->
    <AppModal :open="showQuestionnaireDetail" size="xl" :title="(selectedUser?.username ?? '') + ' 的问卷详情'" @close="showQuestionnaireDetail = false">
      <div v-if="questionnaireDetail" class="space-y-4">
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 p-4 bg-orange-900/20 rounded-xl">
          <div><div class="text-xs text-stone-400">分数</div><div class="text-2xl font-bold text-orange-400">{{ questionnaireDetail.questionnaireScore || 0 }}</div></div>
          <div><div class="text-xs text-stone-400">状态</div><div :class="questionnaireDetail.questionnairePassed ? 'text-emerald-400' : 'text-rose-400'">{{ questionnaireDetail.questionnairePassed ? '已通过' : '未通过' }}</div></div>
          <div><div class="text-xs text-stone-400">账号状态</div><div class="text-white">{{ getStatusText(questionnaireDetail.status) }}</div></div>
          <div><div class="text-xs text-stone-400">答题时间</div><div class="text-sm text-stone-300">{{ formatTime(questionnaireDetail.questionnaireScoredAt) }}</div></div>
        </div>
        <div class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
          <h4 class="font-medium text-white mb-3">修改分数</h4>
          <div class="flex flex-col sm:flex-row gap-4 items-end">
            <div class="flex-1 w-full"><label class="block text-sm text-stone-300 mb-1">分数</label><input v-model="editScore" type="number" class="input" min="0" /></div>
            <div class="flex-1 w-full"><label class="block text-sm text-stone-300 mb-1">通过状态</label><select v-model="editPassed" class="input"><option :value="true">通过</option><option :value="false">未通过</option></select></div>
            <button @click="saveQuestionnaire" class="btn-primary w-full sm:w-auto">保存</button>
          </div>
        </div>
        <div class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
          <h4 class="font-medium text-white mb-3">玩家答案</h4>
          <div v-if="questionnaireDetail.questionnaireAnswers" class="space-y-3">
            <div v-for="(answer, index) in parsedAnswers" :key="index" class="p-3 bg-stone-700/50 rounded-lg">
              <div class="text-sm font-medium text-stone-200 mb-2">第 {{ index + 1 }} 题</div>
              <p class="text-stone-300 text-sm">{{ answer }}</p>
            </div>
          </div>
          <div v-else class="text-stone-400 text-sm">暂无答题记录</div>
        </div>
        <div class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
          <h4 class="font-medium text-white mb-3">AI 评语</h4>
          <div v-if="parsedReasons.length > 0" class="space-y-3">
            <div v-for="(reason, index) in parsedReasons" :key="index" class="p-3 bg-stone-700/50 rounded-lg">
              <div class="text-sm font-medium text-stone-200 mb-2">第 {{ index + 1 }} 题</div>
              <textarea v-model="parsedReasons[index]" class="input text-sm" rows="2"></textarea>
            </div>
          </div>
          <div v-else class="text-stone-400 text-sm">暂无答题记录</div>
          <button v-if="parsedReasons.length > 0" @click="saveReasons" class="btn-secondary mt-3 text-sm">保存评语</button>
        </div>
      </div>
    </AppModal>
</template>

<script setup lang="ts">
import { ref, onMounted, inject, computed, watch } from 'vue'
import api from '@/services/api'
import AppIcon from '@/components/AppIcon.vue'
import QuestionnaireEditor from '@/components/QuestionnaireEditor.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppPagination from '@/components/ui/AppPagination.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatCard from '@/components/ui/StatCard.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import ServerManageTab from '@/components/admin/ServerManageTab.vue'
import ServerMetricsPanel from '@/components/admin/ServerMetricsPanel.vue'
import ForumManageTab from '@/components/admin/ForumManageTab.vue'
import PollManageTab from '@/components/admin/PollManageTab.vue'
import FeedbackManageTab from '@/components/admin/FeedbackManageTab.vue'
import WebhookSettingsCard from '@/components/admin/WebhookSettingsCard.vue'
import { iconNames } from '@/components/AppIcon.vue'
import { getStatusText, getStatusClass } from '@/lib/status'
import { renderMarkdown } from '@/lib/markdown'

const notify = inject('notify') as any

const activeTab = ref('dashboard')
const maintenanceEnabled = ref(false)
const menuItems = [
  { key: 'dashboard', icon: 'squares-2x2', label: '总览' },
  { key: 'portal', icon: 'home', label: '门户管理' },
  { key: 'settings', icon: 'cog', label: '外观设置' },
  { key: 'system', icon: 'cpu', label: '系统设置' },
  { key: 'announcements', icon: 'chat-bubble', label: '公告管理' },
  { key: 'docs', icon: 'document-text', label: '文档管理' },
  { key: 'review', icon: 'clipboard-check', label: '审核管理' },
  { key: 'players', icon: 'users', label: '玩家管理' },
  { key: 'stats', icon: 'chart-bar', label: '数据统计' },
  { key: 'servers', icon: 'server-stack', label: '服务器管理' },
  { key: 'forum', icon: 'chat-bubble', label: '论坛管理' },
  { key: 'polls', icon: 'dot-circle', label: '投票管理' },
  { key: 'feedback', icon: 'envelope', label: '反馈工单' },
  { key: 'taskshop', icon: 'squares-2x2', label: '任务与兑换' },
  { key: 'rewards', icon: 'sparkles', label: '奖励发放' },
  { key: 'titles', icon: 'shield-check', label: '称号与成就' },
  { key: 'audits', icon: 'document-text', label: '审计日志' },
  { key: 'appeals', icon: 'envelope', label: '申诉处理' },
  { key: 'questionnaires', icon: 'pencil-square', label: '问卷管理' },
  { key: 'verify', icon: 'shield-check', label: '验证页面' },
  { key: 'migration', icon: 'archive-box', label: '数据迁移' }
]

// ===== 系统设置（注册/AI/邀请/游戏/下载中心） =====
const sysCfg = ref<any>({ requireEmailCode: true, captchaEnabled: false, autoApprove: false,
  maxAccountsPerEmail: 2, domainWhitelistEnabled: true, emailDomainWhitelistStr: '' })
const llmCfg = ref<any>({ enabled: false, apiBase: '', apiKey: '', model: '', systemPrompt: '', hasApiKey: false })
const inviteCfg = ref<any>({ enabled: true, codeExpiryDays: 7, maxInvitesPerUser: 3 })
const gameCfg = ref<any>({ webRegisterUrl: '', bedrockEnabled: false, bedrockPrefix: '.' })
const astrbotCfg = ref<any>({ enabled: false, apiToken: '', hasToken: false, groupBindingsStr: '[]' })
const downloadsJson = ref('{}')

const questCfg = ref<any>({ enabled: true, passScore: 60 })
const securityCfg = ref<any>({ admin2faRequired: false })

const loadSecurity = async () => {
  try {
    const r: any = await api.getSecurityConfig()
    if (r.success) securityCfg.value = { admin2faRequired: !!r.data?.admin2faRequired }
  } catch (e) { console.error(e) }
}
const saveSecurity = async () => {
  try {
    const r: any = await api.saveSecurityConfig(securityCfg.value.admin2faRequired)
    if (r.success) notify?.success('安全设置已保存')
    else notify?.error(r.message || '保存失败')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}

const loadSystemSettings = async () => {
  try {
    const [reg, llm, inv, game, dls, quest, astr, ann, syscfg, rules, tsk, shop, titlesData, sec] = await Promise.all([
      api.getRegisterSettings(), api.getLlmSettings(), api.getInviteSettings(),
      api.getGameSettings(), api.getDownloadsAdmin(), api.getQuestionnaireSettings(),
      api.getAstrbotSettings(), api.getAnnouncementsAdmin(), api.getSystemConfig(),
      api.getRulesConfig(), api.getTasksConfigAdmin(), api.getShopConfigAdmin(),
      api.getTitlesOverview(), api.getSecurityConfig()
    ])
    if (reg.success) Object.assign(sysCfg.value, reg.data,
      { emailDomainWhitelistStr: (reg.data.emailDomainWhitelist || []).join(', ') })
    if (llm.success) Object.assign(llmCfg.value, llm.data)
    if (inv.success) Object.assign(inviteCfg.value, inv.data)
    if (game.success) Object.assign(gameCfg.value, game.data)
    if (dls.success) downloadsJson.value = JSON.stringify(dls.data ?? {}, null, 2)
    if (quest.success) Object.assign(questCfg.value, quest.data)
    if (sec.success) securityCfg.value = { admin2faRequired: !!sec.data?.admin2faRequired }
    if (syscfg.success) adminsStr.value = (syscfg.data.admins || []).join('\n')
    if (astr.success) {
      Object.assign(astrbotCfg.value, astr.data)
      try { astrbotCfg.value.groupBindingsStr = JSON.stringify(JSON.parse(astr.data.groupBindings || '[]'), null, 2) }
      catch { astrbotCfg.value.groupBindingsStr = '[]' }
    }
    if (rules.success) rulesCfg.value = { doc: rules.data.doc || '', seconds: Number(rules.data.seconds) || 15 }
    if (tsk.success) tasksCfg.value = tsk.data
    if (ann.success) {
      // 修复审计：公告管理此前从不加载已有数据,打开即空、保存即清空线上公告
      newsList.value = Array.isArray(ann.data?.news) ? ann.data.news : []
      changelogList.value = Array.isArray(ann.data?.changelog) ? ann.data.changelog : []
    }
    if (titlesData.success) {
      // 修复审计：称号总览并入系统设置加载,管理员进入即见现有定义
      titlesCfg.value = { titles: Array.isArray(titlesData.data?.titles) ? titlesData.data.titles : [] }
      achCfg.value = { achievements: Array.isArray(titlesData.data?.achievements) ? titlesData.data.achievements : [] }
    }
    if (shop.success) {
      shopCfg.value = {
        rewards: (shop.data.rewards || []).map((r: any) => ({
          ...r,
          commandsText: (r.commands || []).map((c: any) => c.cmd || ('deposit:' + (c.amount || 0))).join('\n')
        }))
      }
    }
  } catch (e) { console.error('loadSystemSettings failed', e) }
}

const saveRegisterSettings = async () => {
  try {
    const body: any = { ...sysCfg.value,
      emailDomainWhitelist: sysCfg.value.emailDomainWhitelistStr.split(',').map((x: string) => x.trim()).filter(Boolean) }
    delete body.emailDomainWhitelistStr
    const r: any = await api.saveRegisterSettings(body)
    if (r.success) notify?.success('注册设置已保存')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}
const saveQuestSettings = async () => {
  try { const r: any = await api.saveQuestionnaireSettings(questCfg.value); if (r.success) notify?.success('问卷设置已保存') }
  catch (e: any) { notify?.error(e.message || '保存失败') }
}

const saveLlmSettings = async () => {
  try {
    const r: any = await api.saveLlmSettings(llmCfg.value)
    if (r.success) notify?.success('AI 设置已保存')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}
const saveInviteSettings = async () => {
  try {
    const r: any = await api.saveInviteSettings(inviteCfg.value)
    if (r.success) notify?.success('邀请设置已保存')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}
const saveGameSettings = async () => {
  try {
    const r: any = await api.saveGameSettings(gameCfg.value)
    if (r.success) notify?.success('游戏设置已保存')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}
const saveAstrbotSettings = async () => {
  try { JSON.parse(astrbotCfg.value.groupBindingsStr || '[]') }
  catch { notify?.error('群绑定 JSON 格式错误'); return }
  try {
    const body: any = { ...astrbotCfg.value, groupBindings: astrbotCfg.value.groupBindingsStr }
    delete body.groupBindingsStr
    const r: any = await api.saveAstrbotSettings(body)
    if (r.success) notify?.success('QQ 互通设置已保存')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}
const genAstrbotToken = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  let t = ''
  for (let i = 0; i < 40; i++) t += chars[Math.floor(Math.random() * chars.length)]
  astrbotCfg.value.apiToken = t
}
const rulesCfg = ref<any>({ doc: '', seconds: 15 })
const rulesDocOptions = computed(() => {
  const opts: Array<{ value: string; label: string }> = []
  for (const cat of docsData.value.categories || []) {
    for (const f of cat.docs || []) {
      opts.push({ value: cat.dirName + '/' + f.filename, label: (cat.displayName || cat.name) + ' / ' + f.title })
    }
  }
  for (const f of docsData.value.uncategorized || []) {
    opts.push({ value: f.filename, label: '未分类 / ' + f.title })
  }
  return opts
})
const tasksCfg = ref<any>({ tasks: [] })
const shopCfg = ref<any>({ rewards: [] })

const saveRules = async () => {
  try {
    const r: any = await api.saveRulesConfig(rulesCfg.value)
    if (r.success) notify && notify.success('注册守则设置已保存')
  } catch (e: any) { notify && notify.error(e.message || '保存失败') }
}


const addTask = () => {
  tasksCfg.value.tasks.push({
    id: 'task_' + Date.now(), type: 'signin', channel: 'web', period: 'daily',
    name: '新任务', desc: '', target: 1, points: 5, enabled: true
  })
}
const saveTasks = async () => {
  try {
    const r: any = await api.saveTasksConfigAdmin(tasksCfg.value)
    if (r.success) notify && notify.success('任务配置已保存')
  } catch (e: any) { notify && notify.error(e.message || '保存失败') }
}
const addReward = () => {
  shopCfg.value.rewards.push({
    id: 'reward_' + Date.now(), name: '新兑换项', desc: '', cost: 100,
    commandsText: 'eco give {player} 100', enabled: true
  })
}
const saveShop = async () => {
  try {
    const rewards = shopCfg.value.rewards.map((r: any) => ({
      id: r.id, name: r.name, desc: r.desc || '', cost: Number(r.cost) || 0, enabled: !!r.enabled,
      commands: String(r.commandsText || '').split('\n').map((x: string) => x.trim()).filter(Boolean)
        .map((cmd: string) => ({ type: 'command', cmd }))
    }))
    const r: any = await api.saveShopConfigAdmin({ rewards })
    if (r.success) notify && notify.success('兑换商店已保存')
  } catch (e: any) { notify && notify.error(e.message || '保存失败') }
}
const titlesCfg = ref<any>({ titles: [] })
const achCfg = ref<any>({ achievements: [] })

const grantForm = ref<any>({ username: '', code: '' })

const addTitleDef = () => {
  titlesCfg.value.titles.push({ code: '', name: '新称号', desc: '', color: '#fbbf24', enabled: true })
}
const saveTitles = async () => {
  try {
    // 修复审计：后端 titlesconfig 收裸数组,原发 {titles:[...]} 形状不匹配
    const r: any = await api.saveTitlesConfigAdmin(titlesCfg.value.titles)
    if (r.success) notify && notify.success('称号定义已保存')
  } catch (e: any) { notify && notify.error(e.message || '保存失败') }
}
const addAchievementDef = () => {
  achCfg.value.achievements.push({ id: 'ach_' + Date.now(), name: '新成就', desc: '', metric: 'playtime_total', target: 10, reward: '', enabled: true })
}
const saveAchievements = async () => {
  try {
    // 修复审计：后端 achievementsconfig 收裸数组
    const r: any = await api.saveAchievementsConfigAdmin(achCfg.value.achievements)
    if (r.success) notify && notify.success('成就定义已保存')
  } catch (e: any) { notify && notify.error(e.message || '保存失败') }
}
const doGrant = async () => {
  if (!grantForm.value.username || !grantForm.value.code) { notify && notify.error('请填写用户名与称号'); return }
  try {
    const r: any = await api.grantTitle(grantForm.value.username, grantForm.value.code)
    if (r.success) notify && notify.success('已授予')
  } catch (e: any) { notify && notify.error(e.message || '操作失败') }
}
const doRevoke = async () => {
  if (!grantForm.value.username || !grantForm.value.code) { notify && notify.error('请填写用户名与称号'); return }
  try {
    const r: any = await api.revokeTitle(grantForm.value.username, grantForm.value.code)
    if (r.success) notify && notify.success('已撤销')
  } catch (e: any) { notify && notify.error(e.message || '操作失败') }
}
const saveDownloads = async () => {
  try {
    const parsed = JSON.parse(downloadsJson.value)
    const r: any = await api.saveDownloadsAdmin(parsed)
    if (r.success) notify?.success('下载中心已保存')
  } catch (e: any) { notify?.error(e.message?.includes('JSON') ? 'JSON 格式错误' : (e.message || '保存失败')) }
}

// ===== 奖励发放(礼包 + 指令包) =====
const rewardKits = ref<any[]>([])
const showCreateKit = ref(false)
const newKit = ref<any>({ name: '', note: '' })
const sendMode = ref<'single' | 'all'>('single')
const sendForm = ref<any>({ usernamesStr: '', source: 'kit', kitId: null, commandsText: '', title: '', note: '' })
const sendingReward = ref(false)
const sendResult = ref<any>(null)

const readyKits = computed(() => rewardKits.value.filter(k => k.status === 'ready'))

/** 后端 commands JSON({"commands":[...]} 或裸数组)→ textarea 文本 */
const commandsToText = (commandsJson: string) => {
  try {
    const parsed = JSON.parse(commandsJson || '[]')
    const arr = Array.isArray(parsed) ? parsed : (parsed.commands || [])
    return arr.map((c: any) => c.cmd || '').filter(Boolean).join('\n')
  } catch { return '' }
}
const kitStatusText = (s: string) => s === 'ready' ? '可发放' : s === 'disabled' ? '已停用' : '待采集'
const kitStatusClass = (s: string) => s === 'ready' ? 'bg-emerald-500/20 text-emerald-400'
  : s === 'disabled' ? 'bg-rose-500/20 text-rose-400' : 'bg-amber-500/20 text-amber-400'
const formatTs = (ts: number) => ts ? new Date(ts).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : ''

const loadRewardKits = async () => {
  try {
    const r: any = await api.getRewardKits()
    if (r.success) {
      rewardKits.value = (r.data.kits || []).map((k: any) => ({ ...k, commandsText: commandsToText(k.commands) }))
      if (!sendForm.value.kitId && readyKits.value.length) sendForm.value.kitId = readyKits.value[0].id
    }
  } catch (e) { console.error(e) }
}

const createKit = async () => {
  if (!newKit.value.name.trim()) { notify?.error('请填写礼包名'); return }
  try {
    const r: any = await api.createRewardKit(newKit.value.name.trim(), newKit.value.note)
    if (r.success) {
      notify?.success(r.message || '已创建')
      newKit.value = { name: '', note: '' }
      showCreateKit.value = false
      await loadRewardKits()
    } else notify?.error(r.message || '创建失败')
  } catch (e: any) { notify?.error(e.message || '创建失败') }
}

const saveKitCommands = async (k: any) => {
  try {
    const r: any = await api.updateRewardKit(k.id, { note: k.note, commandsText: k.commandsText })
    if (r.success) notify?.success('附加指令已保存')
    else notify?.error(r.message || '保存失败')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
}

const deleteKit = async (k: any) => {
  if (!confirm(`确定删除礼包「${k.name}」？已发放的邮件不受影响。`)) return
  try {
    const r: any = await api.deleteRewardKit(k.id)
    if (r.success) { notify?.success('已删除'); await loadRewardKits() }
    else notify?.error(r.message || '删除失败')
  } catch (e: any) { notify?.error(e.message || '删除失败') }
}

const doSendReward = async () => {
  const body: any = {
    all: sendMode.value === 'all',
    title: sendForm.value.title || undefined,
    note: sendForm.value.note || undefined
  }
  if (!body.all) {
    const names = sendForm.value.usernamesStr.split(/[,，]/).map((x: string) => x.trim()).filter(Boolean)
    if (!names.length) { notify?.error('请填写玩家名'); return }
    body.usernames = names
  }
  if (sendForm.value.source === 'kit') {
    if (!sendForm.value.kitId) { notify?.error('请选择礼包'); return }
    body.kitId = sendForm.value.kitId
  } else {
    if (!sendForm.value.commandsText.trim()) { notify?.error('请填写奖励指令'); return }
    body.commandsText = sendForm.value.commandsText
  }
  sendingReward.value = true
  try {
    const r: any = await api.sendReward(body)
    sendResult.value = r.success ? { ok: true, text: r.message } : { ok: false, text: r.message || '发放失败' }
    if (r.success) notify?.success(r.message)
    else notify?.error(r.message || '发放失败')
  } catch (e: any) {
    sendResult.value = { ok: false, text: e.message || '发放失败' }
    notify?.error(e.message || '发放失败')
  } finally { sendingReward.value = false }
}

const loadMaintenance = async () => {
  try { const r: any = await api.getMaintenanceMode(); maintenanceEnabled.value = !!r.data?.enabled } catch (e) {}
}
const toggleMaintenance = async () => {
  try {
    await api.setMaintenanceMode(maintenanceEnabled.value)
    notify?.success(maintenanceEnabled.value ? '维护模式已开启' : '维护模式已关闭')
  } catch (e: any) {
    maintenanceEnabled.value = !maintenanceEnabled.value
    notify?.error(e.message || '操作失败')
  }
}
const migrationFileInput = ref<HTMLInputElement | null>(null)
const migrationFile = ref<File | null>(null)
const migrationLoading = ref(false)
const migrationResult = ref<any>(null)
const migrationError = ref('')

const onMigrationFileChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  migrationFile.value = input.files && input.files[0] ? input.files[0] : null
  migrationResult.value = null
  migrationError.value = ''
}

const uploadMigration = async () => {
  if (!migrationFile.value) return
  migrationLoading.value = true
  migrationError.value = ''
  migrationResult.value = null
  try {
    const res: any = await api.uploadMigrationDump(migrationFile.value)
    migrationResult.value = res
  } catch (err: any) {
    migrationError.value = err.message || '导入失败'
  } finally {
    migrationLoading.value = false
  }
}
const searchQuery = ref('')
const statusFilter = ref('')
const loading = ref(true)
const saving = ref(false)
const showQuestionnaireDetail = ref(false)
const selectedUser = ref<any>(null)
const questionnaireDetail = ref<any>(null)
const editScore = ref(0)
const editPassed = ref(false)
const parsedReasons = ref<string[]>([])
const parsedAnswers = ref<string[]>([])

const username = ref(localStorage.getItem('username') || '')
const bgImage = ref('/bg.webp')
const bgOpacity = ref(0.5)
const bgBlur = ref(16)
const announcementEdit = ref('')

const portalData = ref({
  photoTypes: ['announcement', 'event', 'milestone'] as string[],
  server_name: '夏日小镇★XMCraft',
  subtitle: 'Minecraft Java生存服务器',
  brand_short: 'XMCraft',
  brand_tagline: '玩家账户系统',
  favicon: '',
  accent: '#f97316',
  description: '',
  version: '1.20.4',
  server_ip: 'play.xmcraft.cn',
  server_port: 25565,
  logo: '/logo.png',
  icp: '',
  map_url: '',
  map_items: [] as any[],
  social: { wiki: '' },
  carousel: [] as any[],
  team: [] as any[],
  features: [] as any[],
  timeline: [] as any[]
})

const users = ref<any[]>([])
const selectedUsers = ref<string[]>([])
const statsOverview = ref<any>({})
const auditLogs = ref<any[]>([])

// 审计操作码 → 中文标签(覆盖后端全部 .log() 操作码;未知码原样展示)
const auditActionLabels: Record<string, string> = {
  approve: '通过审核', reject: '拒绝审核', ban: '封禁', unban: '解封',
  unban_expired: '临时封禁到期解封', delete: '删除用户', update_user: '更新用户',
  batch: '批量操作', status_change: '状态变更', setup: '初始化',
  maintenance: '维护模式', verify_bedrock: '基岩验证', set_bedrock: '绑定基岩 ID',
  qq_bind: '绑定 QQ', qq_unbind: '解绑 QQ',
  questionnaire_submit: '提交问卷', questionnaire_save_bulk: '批量保存问卷',
  admin_update_questionnaire: '更新问卷配置',
  migration_upload: '上传迁移数据', migration_seed_cleanup: '清理迁移种子',
  appeal_approved: '申诉通过', appeal_rejected: '申诉驳回',
  settings_register: '注册设置', settings_questionnaire: '问卷设置',
  settings_llm: 'AI 评分设置', settings_invite: '邀请设置',
  settings_game: '游戏设置', settings_downloads: '下载中心设置',
  settings_astrbot: 'QQ 互通设置', settings_announcements: '公告设置',
  settings_tasks: '任务配置', settings_shop: '兑换商店',
  server_token_issue: '签发按服令牌', server_token_mode: '切换鉴权模式',
  server_enable: '启用服务器通道', server_disable: '停用服务器通道',
  feedback_create: '提交反馈', feedback_reply: '回复工单', feedback_close: '关闭工单',
  poll_create: '创建投票', poll_open: '开启投票', poll_close: '关闭投票', poll_update: '更新投票',
  webhook_test: 'Webhook 测试', settings_webhook: 'Webhook 设置',
  forum_section_create: '创建板块', forum_section_update: '更新板块', forum_section_delete: '删除板块',
  forum_thread_edit: '编辑帖子',
}
const auditActionLabel = (a: string) => {
  if (auditActionLabels[a]) return auditActionLabels[a]
  if (a.startsWith('settings_')) return '系统设置变更'
  return a
}

// 总览 Dashboard
const serverStatus = ref<any>({ online: false, players: { online: 0, max: 0 } })
const loadServerStatus = async () => { try { const r: any = await api.getServerStatus(); if (r.success) serverStatus.value = r.data } catch (e) {} }
const recentUsers = computed(() => [...users.value].sort((a: any, b: any) => (b.regTime || 0) - (a.regTime || 0)).slice(0, 6))
const recentAudits = computed(() => (auditLogs.value || []).slice(0, 6))
const quickLinks = [
  { key: 'players', icon: 'users', label: '玩家管理' },
  { key: 'review', icon: 'clipboard-check', label: '审核管理' },
  { key: 'stats', icon: 'chart-bar', label: '数据统计' },
  { key: 'announcements', icon: 'chat-bubble', label: '公告管理' },
  { key: 'docs', icon: 'document-text', label: '文档管理' },
  { key: 'system', icon: 'cpu', label: '系统设置' },
]
const appeals = ref<any[]>([])
const questionnaires = ref<any[]>([])
const verifyConfig = ref({
  title: 'ID 验证',
  subtitle: '验证你的 Minecraft 账户',
  instructions: '',
  javaServerAddress: 'mc.xmcraft.cn',
  javaServerPort: 25565,
  bedrockServerAddress: 'mc.xmcraft.cn',
  bedrockServerPort: 19132
})
const pendingPage = ref(1)
const playerPage = ref(1)
const auditPage = ref(1)
const appealPage = ref(1)
const pageSize = 10

const showConfirmDialog = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const confirmAction = ref<() => void>(() => {})
const showBanDaysInput = ref(false)
const banDays = ref<number | null>(null)
const banReason = ref('')

// 删除用户相关
const showDeleteDialog = ref(false)
const deleteUsername = ref('')
const deleteStep = ref(1)
const deleteConfirmInput = ref('')

// 基岩版 ID 相关
const bedrockEnabled = ref(false)
const showBedrockModal = ref(false)
const bedrockTargetUser = ref<any>(null)
const bedrockNameInput = ref('')
const bedrockLoading = ref(false)
const bedrockVerifyMessage = ref('')
const bedrockVerifySuccess = ref(false)

const pendingUsers = computed(() => users.value.filter(u => u.status === 'pending' || u.status === 'pending_review'))
const filteredUsers = computed(() => {
  let result = users.value
  if (searchQuery.value) result = result.filter(u => u.username.toLowerCase().includes(searchQuery.value.toLowerCase()) || u.email?.toLowerCase().includes(searchQuery.value.toLowerCase()))
  if (statusFilter.value) result = result.filter(u => u.status === statusFilter.value)
  return result
})
const knownTypeNames: Record<string, string> = {
  announcement: '公告更新', event: '活动赛事', milestone: '成就纪念'
}
const typeName = (t: string) => knownTypeNames[t] || t
const newPhotoType = ref('')
const addPhotoType = () => {
  const t = newPhotoType.value.trim()
  if (!t) return
  if (t.length > 12) { notify?.error('分类名过长(≤12 字)'); return }
  if (portalData.value.photoTypes.includes(t) || knownTypeNames[t]) { notify?.error('该分类已存在'); return }
  portalData.value.photoTypes.push(t)
  newPhotoType.value = ''
}

const totalPendingPages = computed(() => Math.ceil(pendingUsers.value.length / pageSize))
const totalAuditPages = computed(() => Math.max(1, Math.ceil(auditLogs.value.length / pageSize)))
const paginatedAuditLogs = computed(() => auditLogs.value.slice((auditPage.value - 1) * pageSize, auditPage.value * pageSize))
const totalAppealPages = computed(() => Math.max(1, Math.ceil(appeals.value.length / pageSize)))
const paginatedAppeals = computed(() => appeals.value.slice((appealPage.value - 1) * pageSize, appealPage.value * pageSize))
const totalPlayerPages = computed(() => Math.ceil(filteredUsers.value.length / pageSize))
const paginatedPendingUsers = computed(() => { const s = (pendingPage.value - 1) * pageSize; return pendingUsers.value.slice(s, s + pageSize) })
const paginatedUsers = computed(() => { const s = (playerPage.value - 1) * pageSize; return filteredUsers.value.slice(s, s + pageSize) })

watch([searchQuery, statusFilter], () => { playerPage.value = 1 })

onMounted(async () => {
  loading.value = true
  await Promise.all([loadUsers(), loadSettings(), loadPortalConfig(), loadBedrockConfig(), loadStats(), loadAudits(), loadAppeals(), loadVerifyConfig(), loadQuestionnaires(), loadDocs(), loadAdmins(), loadMaintenance(), loadSystemSettings(), loadServerStatus(), loadRewardKits()])
  loading.value = false
})

const loadUsers = async () => { try { const r: any = await api.getUsers(); if (r.success) users.value = r.data.users || [] } catch (e) { notify?.error('加载用户失败') } }

// 批量操作相关
const allUsersSelected = computed(() => {
  if (paginatedUsers.value.length === 0) return false
  return paginatedUsers.value.every((u: any) => selectedUsers.value.includes(u.username))
})

const toggleAllUsers = () => {
  if (allUsersSelected.value) {
    selectedUsers.value = []
  } else {
    selectedUsers.value = paginatedUsers.value.map((u: any) => u.username)
  }
}

const batchApprove = async () => {
  if (!confirm(`确定要批准选中的 ${selectedUsers.value.length} 个用户吗？`)) return
  try {
    const r: any = await api.batchApproveUsers(selectedUsers.value)
    if (r.success) {
      notify?.success(r.data.message)
      selectedUsers.value = []
      await loadUsers()
    }
  } catch (e: any) { notify?.error(e.message || '批量批准失败') }
}

const batchReject = async () => {
  const reason = prompt('请输入拒绝原因（可选）')
  if (!confirm(`确定要拒绝选中的 ${selectedUsers.value.length} 个用户吗？`)) return
  try {
    const r: any = await api.batchRejectUsers(selectedUsers.value, reason || '')
    if (r.success) {
      notify?.success(r.data.message)
      selectedUsers.value = []
      await loadUsers()
    }
  } catch (e: any) { notify?.error(e.message || '批量拒绝失败') }
}

const batchBan = async () => {
  const reason = prompt('请输入封禁原因（可选）')
  if (!confirm(`确定要封禁选中的 ${selectedUsers.value.length} 个用户吗？`)) return
  try {
    const r: any = await api.batchBanUsers(selectedUsers.value, reason || '')
    if (r.success) {
      notify?.success(r.data.message)
      selectedUsers.value = []
      await loadUsers()
    }
  } catch (e: any) { notify?.error(e.message || '批量封禁失败') }
}

const batchDelete = async () => {
  if (!confirm(`确定要删除选中的 ${selectedUsers.value.length} 个用户吗？此操作不可撤销！`)) return
  if (!confirm('请再次确认：此操作将永久删除用户数据，确定继续？')) return
  try {
    const r: any = await api.batchDeleteUsers(selectedUsers.value)
    if (r.success) {
      notify?.success(r.data.message)
      selectedUsers.value = []
      await loadUsers()
    }
  } catch (e: any) { notify?.error(e.message || '批量删除失败') }
}

const exportUsers = async () => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch('/api/admin/export/users?format=csv', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `users_${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
    notify?.success('导出成功')
  } catch (e: any) { notify?.error(e.message || '导出失败') }
}

const changeUserStatus = async (username: string, newStatus: string) => {
  try {
    const r: any = await api.updateUserStatus(username, newStatus)
    if (r.success) {
      notify?.success(`已将 ${username} 状态修改为 ${getStatusText(newStatus)}`)
      await loadUsers()
    }
  } catch (e: any) {
    notify?.error(e.message || '修改状态失败')
    await loadUsers() // 刷新以恢复原状态
  }
}

const loadBedrockConfig = async () => { try { const r: any = await api.getConfig(); if (r.success) bedrockEnabled.value = r.data.bedrockEnabled || false } catch (e) {} }

// 加载统计数据
const loadStats = async () => {
  try {
    const r: any = await api.getStatsOverview()
    if (r.success) statsOverview.value = r.data
  } catch (e) {}
}

// 加载操作日志
const loadAudits = async () => {
  try {
    const r: any = await api.getAuditLogs()
    if (r.success) auditLogs.value = r.data || []
  } catch (e) {}
}

// 加载申诉列表
const loadAppeals = async () => {
  try {
    const r: any = await api.getAppeals()
    if (r.success) appeals.value = r.data || []
  } catch (e) {}
}

// 加载验证页面配置
const loadVerifyConfig = async () => {
  try {
    const r: any = await api.getConfig()
    if (r.success && r.data.verifyPage) {
      verifyConfig.value = { ...verifyConfig.value, ...r.data.verifyPage }
    }
  } catch (e) {}
}

// 加载问卷历史
const loadQuestionnaires = async () => {
  try {
    // 从用户列表中筛选有问卷记录的用户
    const r: any = await api.getUsers()
    if (r.success) {
      questionnaires.value = (r.data.users || []).filter((u: any) => 
        u.questionnaireScore > 0 || u.questionnaireScoredAt > 0
      )
    }
  } catch (e) {}
}

// 保存验证页面配置
const saveVerifyConfig = async () => {
  saving.value = true
  try {
    await api.updatePortalConfig({ verifyPage: verifyConfig.value })
    notify?.success('验证页面配置已保存')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
  saving.value = false
}

// 申诉操作
const approveAppeal = async (id: number) => {
  try {
    const r: any = await api.approveAppeal(id)
    if (r.success) {
      notify?.success('申诉已通过')
      await loadAppeals()
    }
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

const rejectAppeal = async (id: number) => {
  const reply = prompt('请输入拒绝原因（可选）')
  try {
    const r: any = await api.rejectAppeal(id, reply || '')
    if (r.success) {
      notify?.success('申诉已拒绝')
      await loadAppeals()
    }
  } catch (e: any) { notify?.error(e.message || '操作失败') }
}

const loadSettings = async () => {
  try {
    const r: any = await api.getBackground()
    if (r.success) { bgImage.value = r.data.image || '/bg.webp'; bgOpacity.value = r.data.opacity ?? 0.5; bgBlur.value = r.data.blur ?? 16; announcementEdit.value = r.data.announcement || '' }
  } catch (e) {}
}
const loadPortalConfig = async () => {
  try {
    const r: any = await api.getPortalConfig()
    if (r.success && r.data.portal) {
      const p = r.data.portal
      portalData.value = {
        server_name: p.server_name || '夏日小镇★XMCraft',
        brand_short: p.brand_short || 'XMCraft',
        brand_tagline: p.brand_tagline || '玩家账户系统',
        favicon: p.favicon || '',
        accent: p.accent || '#f97316',
        subtitle: p.subtitle || '',
        description: p.description || '',
        version: p.version || '1.20.4',
        server_ip: p.server_ip || '',
        server_port: p.server_port || 25565,
        logo: p.logo || '/logo.png',
        icp: p.icp || '',
        map_url: p.map_url || '',
        map_items: Array.isArray(p.map_items) && p.map_items.length
          ? p.map_items.map((m: any) => ({ name: m.name || '', url: m.url || '', type: m.type || 'generic' }))
          : (p.map_url || '').split('|').map((u: string) => u.trim()).filter(Boolean)
              .map((u: string) => ({ name: '地图', url: u, type: 'generic' })),
        social: p.social || { wiki: '' },
        carousel: Array.isArray(p.carousel) ? p.carousel : [],
        team: Array.isArray(p.team) ? p.team : [],
        features: Array.isArray(p.features) ? p.features : [],
        timeline: (Array.isArray(p.timeline) ? p.timeline : []).map((t: any) =>
          t && !t.id ? { ...t, id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6) } : t),
        photoTypes: Array.isArray(p.photo_types) && p.photo_types.length
          ? p.photo_types : ['announcement', 'event', 'milestone'],
      }
    }
  } catch (e) { console.error(e) }
}

// Portal 操作
const addCarousel = () => { portalData.value.carousel.push({ image: '', title: '', subtitle: '' }) }
const removeCarousel = (i: number) => { portalData.value.carousel.splice(i, 1) }
const addTeamMember = () => { portalData.value.team.push({ name: '', role: '', avatar: '' }) }
const removeTeamMember = (i: number) => { portalData.value.team.splice(i, 1) }
const addFeature = () => { portalData.value.features.push({ icon: 'star', title: '', description: '' }) }
const removeFeature = (i: number) => { portalData.value.features.splice(i, 1) }
const newTimelineId = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6)

// ===== 文档管理(读取/新建/编辑/删除,MD 实时预览) =====
const docsData = ref<any>({ categories: [], uncategorized: [] })
const docForm = ref({ isNew: false, category: '' as string | null, filename: '', title: '', content: '' })
const savingDocs = ref(false)
const docPreview = computed(() => renderMarkdown(docForm.value.content || ''))

const loadDocs = async () => {
  try {
    const r: any = await api.getDocs()
    if (r.success || r.categories) docsData.value = r.data ?? r
  } catch (e) { console.error(e) }
}

const openDoc = async (category: string | null, filename: string) => {
  try {
    const r: any = await api.readDoc(category, filename)
    if (r.success || r.filename) {
      const d = r.data ?? r
      docForm.value = { isNew: false, category: d.category || category || '', filename: d.filename || filename, title: d.title || filename, content: d.content || '' }
    }
  } catch (e: any) { notify?.error(e.message || '读取失败') }
}

const newDoc = () => {
  docForm.value = { isNew: true, category: '', filename: '', title: '', content: '' }
}

const saveDoc = async () => {
  savingDocs.value = true
  try {
    let r: any
    if (docForm.value.isNew) {
      r = await api.createDoc(docForm.value.title, docForm.value.category, docForm.value.content)
    } else {
      r = await api.updateDoc(docForm.value.category, docForm.value.filename, docForm.value.content)
    }
    if (r.success) {
      notify?.success('文档已保存')
      await loadDocs()
      if (docForm.value.isNew && r.data?.filename) {
        docForm.value = { isNew: false, category: docForm.value.category, filename: r.data.filename, title: r.data.title || docForm.value.title, content: docForm.value.content }
      }
    } else { notify?.error(r.message || '保存失败') }
  } catch (e: any) { notify?.error(e.message || '保存失败') }
  savingDocs.value = false
}

const deleteDocCurrent = async () => {
  if (!confirm(`确认删除文档「${docForm.value.filename}」吗?`)) return
  try {
    const r: any = await api.deleteDoc(docForm.value.category, docForm.value.filename)
    if (r.success) {
      notify?.success('已删除')
      docForm.value = { isNew: false, category: '', filename: '', title: '', content: '' }
      await loadDocs()
    } else { notify?.error(r.message || '删除失败') }
  } catch (e: any) { notify?.error(e.message || '删除失败') }
}

// ===== 管理员名单 =====
const adminsStr = ref('')
const savingAdmins = ref(false)
const loadAdmins = async () => {
  try {
    const r: any = await api.getSystemConfig()
    if (r.success) adminsStr.value = (r.data.admins || []).join('\n')
  } catch (e) { console.error(e) }
}
const saveAdmins = async () => {
  savingAdmins.value = true
  try {
    const admins = adminsStr.value.split('\n').map((x: string) => x.trim()).filter(Boolean)
    const r: any = await api.updateSystemConfig({ admins })
    if (r.success) notify?.success('管理员名单已保存')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
  savingAdmins.value = false
}

// ===== 公告管理(资讯中心 + 更新日志) =====
const newsList = ref<any[]>([])
const changelogList = ref<any[]>([])
const savingAnnouncements = ref(false)
const newItemId = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
const addNews = () => newsList.value.unshift({ id: newItemId(), title: '', date: new Date().toISOString().slice(0, 10), content: '', pinned: false, isDraft: false, publishAt: '', status: 'published' })
const addChangelog = () => changelogList.value.unshift({ id: newItemId(), version: '', date: new Date().toISOString().slice(0, 10), content: '' })
const saveAnnouncements = async () => {
  savingAnnouncements.value = true
  try {
    for (const n of newsList.value) {
      if (!n.id) n.id = newItemId()
      if (!n.publishAt) n.publishAt = ''
      n.status = n.isDraft ? 'draft' : 'published'
    }
    for (const c of changelogList.value) if (!c.id) c.id = newItemId()
    const r: any = await api.saveAnnouncementsAdmin({ news: newsList.value, changelog: changelogList.value })
    if (r.success) notify?.success('公告内容已保存')
  } catch (e: any) { notify?.error(e.message || '保存失败') }
  savingAnnouncements.value = false
}
const addTimelineEvent = () => { portalData.value.timeline.push({ id: newTimelineId(), date: '', title: '', description: '', image: '', type: '' }) }
const removeTimelineEvent = (i: number) => { portalData.value.timeline.splice(i, 1) }

const savePortalConfig = async () => {
  saving.value = true
  try {
    // map_items 为权威数据;map_url 派生保留(旧版本/外部消费兼容)
    const validItems = portalData.value.map_items.filter((m: any) => (m.url || '').trim())
    portalData.value.map_items = validItems
    portalData.value.map_url = validItems.map((m: any) => m.url.trim()).join('|')
    await api.updatePortalConfig(portalData.value); notify?.success('保存成功') } catch (e: any) { notify?.error(e.message) }
  saving.value = false
}

// 图片上传
const uploadFile = async (file: File): Promise<string | null> => {
  if (file.size > 1024 * 1024) {
    notify?.info(`图片较大 (${(file.size / 1024 / 1024).toFixed(1)}MB)，建议压缩后再上传以提升加载速度`)
  }
  try { const r: any = await api.uploadImage(file); if (r.success) return r.data.url } catch (e: any) { notify?.error('上传失败: ' + e.message) }
  return null
}
const uploadCarouselImage = async (e: Event, index: number) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) { const url = await uploadFile(file); if (url) portalData.value.carousel[index].image = url }
}
const uploadTeamAvatar = async (e: Event, index: number) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) { const url = await uploadFile(file); if (url) portalData.value.team[index].avatar = url }
}
const uploadTimelineImage = async (e: Event, index: number) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) { const url = await uploadFile(file); if (url) portalData.value.timeline[index].image = url }
}
const uploadBgImage = async (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) { const url = await uploadFile(file); if (url) bgImage.value = url }
}

// 设置操作
const uploadLogo = async (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) {
    const url = await uploadFile(file)
    if (url) portalData.value.logo = url
  }
}

const saveSettings = async () => {
  saving.value = true
  try {
    await api.updateBackground({ image: bgImage.value, opacity: bgOpacity.value, blur: bgBlur.value, announcement: announcementEdit.value })
    document.documentElement.style.setProperty('--bg-image', `url('${bgImage.value}')`)
    document.documentElement.style.setProperty('--bg-opacity', String(bgOpacity.value))
    document.documentElement.style.setProperty('--bg-blur', bgBlur.value + 'px')
    notify?.success('设置已保存')
  } catch (e: any) { notify?.error(e.message) }
  saving.value = false
}

// 审核操作
const confirmApprove = (u: string) => { showBanDaysInput.value = false; confirmTitle.value = '确认通过'; confirmMessage.value = `确定通过 ${u} 的白名单申请吗？`; confirmAction.value = async () => { try { await api.approveUser(u); notify?.success('已通过'); await loadUsers() } catch (e: any) { notify?.error(e.message) } }; showConfirmDialog.value = true }
const confirmReject = (u: string) => { showBanDaysInput.value = false; confirmTitle.value = '确认拒绝'; confirmMessage.value = `确定拒绝 ${u} 的白名单申请吗？`; confirmAction.value = async () => { try { await api.rejectUser(u); notify?.success('已拒绝'); await loadUsers() } catch (e: any) { notify?.error(e.message) } }; showConfirmDialog.value = true }
const confirmBan = (u: string) => { banDays.value = null; banReason.value = ''; showBanDaysInput.value = true; confirmTitle.value = '确认封禁'; confirmMessage.value = `确定封禁 ${u} 吗？可填写理由并设置临时封禁天数(留空 = 永久)`; confirmAction.value = async () => { try { await api.banUser(u, banReason.value || undefined, banDays.value ?? undefined); notify?.success('已封禁'); await loadUsers() } catch (e: any) { notify?.error(e.message) } }; showConfirmDialog.value = true }
const confirmDelete = (u: string) => { deleteUsername.value = u; deleteStep.value = 1; deleteConfirmInput.value = ''; showDeleteDialog.value = true }
const executeDelete = async () => {
  if (deleteConfirmInput.value !== deleteUsername.value) return
  try {
    await api.deleteUser(deleteUsername.value)
    notify?.success('用户已删除')
    showDeleteDialog.value = false
    await loadUsers()
  } catch (e: any) { notify?.error(e.message) }
}
const executeConfirmAction = async () => { await confirmAction.value(); showConfirmDialog.value = false }
const unbanUser = async (u: string) => { try { await api.unbanUser(u); notify?.success('已解封'); await loadUsers() } catch (e: any) { notify?.error(e.message) } }
const formatTime = (t: number) => t ? new Date(t).toLocaleString('zh-CN') : '-'

const viewQuestionnaire = async (user: any) => {
  selectedUser.value = user
  try {
    const r: any = await api.getAdminQuestionnaire(user.username)
    if (r.success) {
      questionnaireDetail.value = r.data
      editScore.value = r.data.questionnaireScore || 0
      editPassed.value = r.data.questionnairePassed || false
      
      // 解析 AI 评语
      try { parsedReasons.value = JSON.parse(r.data.questionnaireReasons || '[]') } catch { parsedReasons.value = [] }
      
      // 解析玩家答案
      try {
        const answers = JSON.parse(r.data.questionnaireAnswers || '{}')
        parsedAnswers.value = Object.values(answers).map((a: any) => typeof a === 'object' ? JSON.stringify(a) : String(a))
      } catch { parsedAnswers.value = [] }
      
      showQuestionnaireDetail.value = true
    }
  } catch (e: any) { notify?.error(e.message) }
}
const saveQuestionnaire = async () => {
  if (!selectedUser.value) return
  try { await api.updateAdminQuestionnaire({ username: selectedUser.value.username, questionnaireScore: editScore.value, questionnairePassed: editPassed.value, status: editPassed.value ? 'approved' : undefined }); notify?.success('保存成功'); await loadUsers(); showQuestionnaireDetail.value = false } catch (e: any) { notify?.error(e.message) }
}
const saveReasons = async () => {
  if (!selectedUser.value) return
  try { await api.updateAdminQuestionnaire({ username: selectedUser.value.username, questionnaireReasons: JSON.stringify(parsedReasons.value) }); notify?.success('评语保存成功') } catch (e: any) { notify?.error(e.message) }
}

// 基岩版 ID 管理
const openBedrockModal = (user: any) => {
  bedrockTargetUser.value = user
  bedrockNameInput.value = ''
  bedrockVerifyMessage.value = ''
  bedrockVerifySuccess.value = false
  showBedrockModal.value = true
}

const setBedrockId = async () => {
  if (!bedrockTargetUser.value || !bedrockNameInput.value) return
  bedrockLoading.value = true
  try {
    const r: any = await api.setBedrockId(bedrockTargetUser.value.username, bedrockNameInput.value)
    if (r.success) {
      notify?.success('基岩版 ID 已设置')
      bedrockVerifyMessage.value = r.data.message || '已设置，等待玩家登录验证'
      bedrockVerifySuccess.value = true
      await loadUsers()
    }
  } catch (e: any) {
    notify?.error(e.message)
    bedrockVerifyMessage.value = e.message || '设置失败'
    bedrockVerifySuccess.value = false
  } finally {
    bedrockLoading.value = false
  }
}

const verifyBedrockId = async () => {
  if (!bedrockTargetUser.value) return
  bedrockLoading.value = true
  try {
    const r: any = await api.verifyBedrockId(bedrockTargetUser.value.username)
    if (r.success) {
      if (r.data.verified) {
        notify?.success('基岩版 ID 验证成功')
        bedrockVerifyMessage.value = '验证成功'
        bedrockVerifySuccess.value = true
        await loadUsers()
      } else {
        bedrockVerifyMessage.value = r.data.message || '未找到登录记录'
        bedrockVerifySuccess.value = false
      }
    }
  } catch (e: any) {
    notify?.error(e.message)
    bedrockVerifyMessage.value = e.message || '验证失败'
    bedrockVerifySuccess.value = false
  } finally {
    bedrockLoading.value = false
  }
}
</script>
