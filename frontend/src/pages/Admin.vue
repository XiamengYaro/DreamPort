<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-6xl mx-auto">
      <div class="card p-6 mb-6">
        <h1 class="text-2xl font-bold text-white">管理面板</h1>
        <p class="text-stone-400">欢迎回来，{{ username }}</p>
      </div>

      <!-- Tab 按钮 -->
      <div class="card p-2 mb-6 flex gap-1">
        <button @click="activeTab = 'portal'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'portal' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9" /></svg>
          网站内容
        </button>
        <button @click="activeTab = 'settings'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'settings' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" /><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
          外观设置
        </button>
        <button @click="activeTab = 'review'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'review' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" /></svg>
          审核管理
          <span v-if="pendingUsers.length > 0" class="px-2 py-0.5 bg-orange-500 text-white text-xs rounded-full">{{ pendingUsers.length }}</span>
        </button>
        <button @click="activeTab = 'players'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'players' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" /></svg>
          玩家管理
        </button>
        <button @click="activeTab = 'stats'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'stats' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>
          统计分析
        </button>
        <button @click="activeTab = 'audits'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'audits' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" /></svg>
          操作日志
        </button>
        <button @click="activeTab = 'appeals'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'appeals' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" /></svg>
          申诉管理
        </button>
        <button @click="activeTab = 'questionnaires'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'questionnaires' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
          问卷历史
        </button>
        <button @click="activeTab = 'verify'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'verify' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" /></svg>
          验证页面
        </button>
        <button @click="activeTab = 'migration'" class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-200" :class="activeTab === 'migration' ? 'bg-orange-500/15 text-orange-400 border border-orange-500/20' : 'text-stone-400 hover:text-white hover:bg-white/5'">
          <span class="text-lg">📦</span><span class="text-sm font-medium">迁移</span>
        </button>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="card p-12 text-center">
        <div class="inline-flex items-center gap-3 text-stone-400">
          <svg class="w-6 h-6 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.641z"></path>
          </svg>
          <span>加载中...</span>
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
              <div class="w-12 h-12 rounded-full overflow-hidden bg-stone-700 flex-shrink-0">
                <img v-if="member.avatar" :src="member.avatar" class="w-full h-full object-cover" @error="$event.target.src=''" />
                <div v-else class="w-full h-full flex items-center justify-center text-stone-500 text-lg font-bold">{{ member.name?.charAt(0) }}</div>
              </div>
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
                <input v-model="feature.icon" type="text" class="input text-sm" placeholder="图标 (emoji)" />
                <input v-model="feature.title" type="text" class="input text-sm" placeholder="标题" />
                <input v-model="feature.description" type="text" class="input text-sm" placeholder="描述" />
              </div>
              <button @click="removeFeature(index)" class="text-rose-400 hover:text-rose-300 text-sm mt-2">删除</button>
            </div>
          </div>
          <button @click="savePortalConfig" class="btn-primary mt-4" :disabled="saving">保存特色</button>
        </div>

        <!-- 历史事件 -->
        <div class="card p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-white">历史事件</h3>
            <button @click="addTimelineEvent" class="btn-secondary text-sm">+ 添加事件</button>
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
          <button @click="savePortalConfig" class="btn-primary mt-4" :disabled="saving">保存历史事件</button>
        </div>
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

      <!-- 审核管理 Tab -->
      <div v-if="activeTab === 'review' && !loading" class="card p-6">
        <h3 class="text-lg font-semibold text-white mb-4">审核管理</h3>
        <div v-if="pendingUsers.length === 0" class="text-center py-12 text-stone-500">
          <svg class="w-16 h-16 mx-auto mb-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" /></svg>
          <div>暂无待审核玩家</div>
        </div>
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
          <div v-if="pendingUsers.length > pageSize" class="flex justify-center gap-2 mt-4">
            <button @click="pendingPage--" :disabled="pendingPage <= 1" class="btn-ghost text-sm">上一页</button>
            <span class="text-stone-400 text-sm py-2">{{ pendingPage }} / {{ totalPendingPages }}</span>
            <button @click="pendingPage++" :disabled="pendingPage >= totalPendingPages" class="btn-ghost text-sm">下一页</button>
          </div>
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
        <div v-if="filteredUsers.length > pageSize" class="flex justify-center gap-2 mt-4">
          <button @click="playerPage--" :disabled="playerPage <= 1" class="btn-ghost text-sm">上一页</button>
          <span class="text-stone-400 text-sm py-2">{{ playerPage }} / {{ totalPlayerPages }}</span>
          <button @click="playerPage++" :disabled="playerPage >= totalPlayerPages" class="btn-ghost text-sm">下一页</button>
        </div>
      </div>

      <!-- 统计分析 Tab -->
      <div v-if="activeTab === 'stats' && !loading" class="space-y-6">
        <!-- 总览 -->
        <div class="card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">数据总览</h3>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div class="text-center p-4 rounded-xl bg-stone-800/50">
              <div class="text-2xl font-bold text-orange-400">{{ statsOverview.totalUsers || 0 }}</div>
              <div class="text-xs text-stone-400">总用户数</div>
            </div>
            <div class="text-center p-4 rounded-xl bg-stone-800/50">
              <div class="text-2xl font-bold text-emerald-400">{{ statsOverview.approvedUsers || 0 }}</div>
              <div class="text-xs text-stone-400">已通过</div>
            </div>
            <div class="text-center p-4 rounded-xl bg-stone-800/50">
              <div class="text-2xl font-bold text-amber-400">{{ statsOverview.pendingUsers || 0 }}</div>
              <div class="text-xs text-stone-400">待审核</div>
            </div>
            <div class="text-center p-4 rounded-xl bg-stone-800/50">
              <div class="text-2xl font-bold text-rose-400">{{ statsOverview.bannedUsers || 0 }}</div>
              <div class="text-xs text-stone-400">已封禁</div>
            </div>
          </div>
        </div>
        
        <!-- 注册统计 -->
        <div class="card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">注册统计</h3>
          <div class="grid grid-cols-3 gap-4">
            <div class="text-center p-4 rounded-xl bg-blue-500/10">
              <div class="text-xl font-bold text-blue-400">{{ statsOverview.todayRegistrations || 0 }}</div>
              <div class="text-xs text-stone-400">今日注册</div>
            </div>
            <div class="text-center p-4 rounded-xl bg-purple-500/10">
              <div class="text-xl font-bold text-purple-400">{{ statsOverview.weekRegistrations || 0 }}</div>
              <div class="text-xs text-stone-400">本周注册</div>
            </div>
            <div class="text-center p-4 rounded-xl bg-cyan-500/10">
              <div class="text-xl font-bold text-cyan-400">{{ statsOverview.monthRegistrations || 0 }}</div>
              <div class="text-xs text-stone-400">本月注册</div>
            </div>
          </div>
        </div>
        
        <!-- 问卷统计 -->
        <div class="card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">问卷统计</h3>
          <div class="grid grid-cols-3 gap-4">
            <div class="text-center p-4 rounded-xl bg-emerald-500/10">
              <div class="text-xl font-bold text-emerald-400">{{ statsOverview.questionnairePassRate?.toFixed(1) || 0 }}%</div>
              <div class="text-xs text-stone-400">通过率</div>
            </div>
            <div class="text-center p-4 rounded-xl bg-orange-500/10">
              <div class="text-xl font-bold text-orange-400">{{ statsOverview.averageScore?.toFixed(1) || 0 }}</div>
              <div class="text-xs text-stone-400">平均分</div>
            </div>
            <div class="text-center p-4 rounded-xl bg-stone-800/50">
              <div class="text-xl font-bold text-white">{{ (statsOverview.questionnairePassed || 0) + (statsOverview.questionnaireFailed || 0) }}</div>
              <div class="text-xs text-stone-400">总提交数</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作日志 Tab -->
      <div v-if="activeTab === 'audits' && !loading" class="card p-6">
        <h3 class="text-lg font-semibold text-white mb-4">操作日志</h3>
        <div class="overflow-x-auto">
          <table class="table min-w-[600px]">
            <thead><tr><th>时间</th><th>操作</th><th>操作者</th><th>目标</th><th>详情</th></tr></thead>
            <tbody>
              <tr v-for="audit in auditLogs" :key="audit.id">
                <td class="text-stone-400 text-sm whitespace-nowrap">{{ formatTime(audit.timestamp) }}</td>
                <td><span class="badge-info text-xs">{{ audit.action }}</span></td>
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
        <div v-if="appeals.length === 0" class="text-center py-12 text-stone-500">
          <div class="text-4xl mb-2">📭</div>
          <div>暂无申诉</div>
        </div>
        <div v-else class="space-y-4">
          <div v-for="appeal in appeals" :key="appeal.id" class="p-4 bg-stone-800/50 rounded-xl border border-stone-700">
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
      </div>

      <!-- 问卷历史 Tab -->
      <div v-if="activeTab === 'questionnaires' && !loading" class="card p-6">
        <h3 class="text-lg font-semibold text-white mb-4">问卷历史</h3>
        <div v-if="questionnaires.length === 0" class="text-center py-12 text-stone-500">
          <div class="text-4xl mb-2">📝</div>
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

      <!-- 验证页面配置 Tab -->
      <div v-if="activeTab === 'migration' && !loading" class="card p-6 space-y-6">
        <div>
          <h3 class="text-lg font-semibold text-white mb-2">📦 旧版数据迁移</h3>
          <p class="text-sm text-stone-400">
            上传旧版 XMWhitelist 的 MySQL 导出文件（mysqldump 生成的 <code>.sql</code>），
            系统将自动导入 9 张旧表（用户/审计/邀请/通知/进服记录/申诉/村谱/公共机器）并转换为新数据结构。
          </p>
        </div>
        <div class="bg-amber-500/10 border border-amber-500/20 rounded-xl p-4 text-sm text-amber-300">
          ⚠️ 仅当系统内尚无用户数据时可执行导入（幂等保护）；旧密码将原样保留，玩家可继续用旧密码登录。
          操作前请确认已备份目标数据库。
        </div>
        <div class="flex items-center gap-3">
          <input ref="migrationFileInput" type="file" accept=".sql"
            class="flex-1 text-sm text-stone-300 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0
                   file:bg-orange-500/15 file:text-orange-400 hover:file:bg-orange-500/25 file:cursor-pointer"
            @change="onMigrationFileChange" />
          <button @click="uploadMigration" :disabled="!migrationFile || migrationLoading"
            class="px-5 py-2.5 rounded-xl bg-orange-500 text-white text-sm font-medium hover:bg-orange-600 disabled:opacity-40 disabled:cursor-not-allowed transition-all">
            {{ migrationLoading ? '导入中…' : '开始导入' }}
          </button>
        </div>
        <div v-if="migrationResult" class="bg-stone-800/60 rounded-xl p-4 text-sm">
          <div class="text-green-400 font-medium mb-2">✅ {{ migrationResult.message }}</div>
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
        
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm text-stone-300 mb-1">Java 版服务器地址</label>
            <input v-model="verifyConfig.javaServerAddress" type="text" class="input w-full" placeholder="mc.xmcraft.cn" />
          </div>
          <div>
            <label class="block text-sm text-stone-300 mb-1">Java 版端口</label>
            <input v-model.number="verifyConfig.javaServerPort" type="number" class="input w-full" placeholder="25565" />
          </div>
        </div>
        
        <div class="grid grid-cols-2 gap-4">
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

    <!-- 确认对话框 -->
    <div v-if="showConfirmDialog" class="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50" @click.self="showConfirmDialog = false">
      <div class="card w-full max-w-md p-6 animate-scale-in">
        <h3 class="text-lg font-semibold text-white mb-2">{{ confirmTitle }}</h3>
        <p class="text-stone-400 mb-6">{{ confirmMessage }}</p>
        <div class="flex gap-3 justify-end">
          <button @click="showConfirmDialog = false" class="btn-secondary">取消</button>
          <button @click="executeConfirmAction" class="btn-primary">确认</button>
        </div>
      </div>
    </div>

    <!-- 删除用户确认对话框（两次确认） -->
    <div v-if="showDeleteDialog" class="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50" @click.self="showDeleteDialog = false">
      <div class="card w-full max-w-md p-6 animate-scale-in">
        <!-- 第一次确认 -->
        <template v-if="deleteStep === 1">
          <h3 class="text-lg font-semibold text-red-400 mb-2">确认删除用户</h3>
          <p class="text-stone-400 mb-4">你确定要删除用户 <span class="text-white font-medium">{{ deleteUsername }}</span> 吗？</p>
          <p class="text-red-400 text-sm mb-6">此操作不可撤销，用户的所有数据将被永久删除。</p>
          <div class="flex gap-3 justify-end">
            <button @click="showDeleteDialog = false" class="btn-secondary">取消</button>
            <button @click="deleteStep = 2" class="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded-xl">继续</button>
          </div>
        </template>
        <!-- 第二次确认 -->
        <template v-else>
          <h3 class="text-lg font-semibold text-red-400 mb-2">最终确认</h3>
          <p class="text-stone-400 mb-4">请输入用户名 <span class="text-white font-medium">{{ deleteUsername }}</span> 以确认删除：</p>
          <input v-model="deleteConfirmInput" type="text" class="input w-full mb-4" :placeholder="deleteUsername" />
          <div class="flex gap-3 justify-end">
            <button @click="showDeleteDialog = false; deleteStep = 1" class="btn-secondary">取消</button>
            <button @click="executeDelete" class="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded-xl" :disabled="deleteConfirmInput !== deleteUsername">确认删除</button>
          </div>
        </template>
      </div>
    </div>

    <!-- 基岩版 ID 设置弹窗 -->
    <div v-if="showBedrockModal" class="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50" @click.self="showBedrockModal = false">
      <div class="card w-full max-w-md p-6 animate-scale-in">
        <h3 class="text-lg font-semibold text-white mb-4">设置基岩版 ID</h3>
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
        <div class="flex gap-3 justify-end mt-6">
          <button @click="showBedrockModal = false" class="btn-secondary">取消</button>
          <button @click="setBedrockId" class="btn-primary" :disabled="!bedrockNameInput || bedrockLoading">
            {{ bedrockLoading ? '设置中...' : '设置' }}
          </button>
          <button v-if="bedrockTargetUser?.bedrockName && !bedrockTargetUser?.bedrockVerified" @click="verifyBedrockId" class="bg-emerald-500 hover:bg-emerald-600 text-white px-4 py-2 rounded-xl" :disabled="bedrockLoading">
            {{ bedrockLoading ? '验证中...' : '验证' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 问卷详情弹窗 -->
    <div v-if="showQuestionnaireDetail" class="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50" @click.self="showQuestionnaireDetail = false">
      <div class="card w-full max-w-4xl max-h-[85vh] overflow-hidden">
        <div class="p-4 flex items-center justify-between border-b border-stone-700">
          <h3 class="text-lg font-semibold text-white">{{ selectedUser?.username }} 的问卷详情</h3>
          <button @click="showQuestionnaireDetail = false" class="text-stone-400 hover:text-white text-2xl">&times;</button>
        </div>
        <div class="p-4 overflow-auto max-h-[70vh]">
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
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject, computed, watch } from 'vue'
import api from '@/services/api'
import { getStatusText, getStatusClass } from '@/lib/status'

const notify = inject('notify') as any

const activeTab = ref('portal')
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
  server_name: '夏日小镇',
  subtitle: 'XMCraft Minecraft 服务器',
  description: '',
  version: '1.20.4',
  server_ip: 'play.xmcraft.cn',
  server_port: 25565,
  logo: '/logo.png',
  icp: '',
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
const pageSize = 10

const showConfirmDialog = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const confirmAction = ref<() => void>(() => {})

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
const totalPendingPages = computed(() => Math.ceil(pendingUsers.value.length / pageSize))
const totalPlayerPages = computed(() => Math.ceil(filteredUsers.value.length / pageSize))
const paginatedPendingUsers = computed(() => { const s = (pendingPage.value - 1) * pageSize; return pendingUsers.value.slice(s, s + pageSize) })
const paginatedUsers = computed(() => { const s = (playerPage.value - 1) * pageSize; return filteredUsers.value.slice(s, s + pageSize) })

watch([searchQuery, statusFilter], () => { playerPage.value = 1 })

onMounted(async () => {
  loading.value = true
  await Promise.all([loadUsers(), loadSettings(), loadPortalConfig(), loadBedrockConfig(), loadStats(), loadAudits(), loadAppeals(), loadVerifyConfig(), loadQuestionnaires()])
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
        server_name: p.server_name || '夏日小镇',
        subtitle: p.subtitle || '',
        description: p.description || '',
        version: p.version || '1.20.4',
        server_ip: p.server_ip || '',
        server_port: p.server_port || 25565,
        logo: p.logo || '/logo.png',
        icp: p.icp || '',
        social: p.social || { wiki: '' },
        carousel: Array.isArray(p.carousel) ? p.carousel : [],
        team: Array.isArray(p.team) ? p.team : [],
        features: Array.isArray(p.features) ? p.features : [],
        timeline: Array.isArray(p.timeline) ? p.timeline : []
      }
    }
  } catch (e) { console.error(e) }
}

// Portal 操作
const addCarousel = () => { portalData.value.carousel.push({ image: '', title: '', subtitle: '' }) }
const removeCarousel = (i: number) => { portalData.value.carousel.splice(i, 1) }
const addTeamMember = () => { portalData.value.team.push({ name: '', role: '', avatar: '' }) }
const removeTeamMember = (i: number) => { portalData.value.team.splice(i, 1) }
const addFeature = () => { portalData.value.features.push({ icon: '🎯', title: '', description: '' }) }
const removeFeature = (i: number) => { portalData.value.features.splice(i, 1) }
const addTimelineEvent = () => { portalData.value.timeline.push({ date: '', title: '', description: '', image: '' }) }
const removeTimelineEvent = (i: number) => { portalData.value.timeline.splice(i, 1) }

const savePortalConfig = async () => {
  saving.value = true
  try { await api.updatePortalConfig(portalData.value); notify?.success('保存成功') } catch (e: any) { notify?.error(e.message) }
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
const confirmApprove = (u: string) => { confirmTitle.value = '确认通过'; confirmMessage.value = `确定通过 ${u} 的白名单申请吗？`; confirmAction.value = async () => { try { await api.approveUser(u); notify?.success('已通过'); await loadUsers() } catch (e: any) { notify?.error(e.message) } }; showConfirmDialog.value = true }
const confirmReject = (u: string) => { confirmTitle.value = '确认拒绝'; confirmMessage.value = `确定拒绝 ${u} 的白名单申请吗？`; confirmAction.value = async () => { try { await api.rejectUser(u); notify?.success('已拒绝'); await loadUsers() } catch (e: any) { notify?.error(e.message) } }; showConfirmDialog.value = true }
const confirmBan = (u: string) => { confirmTitle.value = '确认封禁'; confirmMessage.value = `确定封禁 ${u} 吗？`; confirmAction.value = async () => { try { await api.banUser(u); notify?.success('已封禁'); await loadUsers() } catch (e: any) { notify?.error(e.message) } }; showConfirmDialog.value = true }
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
