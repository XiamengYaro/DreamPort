# Changelog

本项目的所有显著变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本管理遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [未发布]

### Fixed（称号与成就 · 用户反馈两 bug）
- **成就定义删不掉**：①配置为显式空数组时 `achievements()`/`titles()` 误回退到内置默认(删光即"复活"6 个默认成就)——现仅在「从未配置/JSON 畸形」时回退默认,显式空列表被尊重;②保存校验过严:成就的奖励称号已被删除时整次保存 400 卡死(先删称号再改成就必触发)——现自动清空悬空奖励引用后正常保存(评估端本就跳过缺失奖励);后台保存成功后回读服务端总览,规整结果即时可见
- **「我的称号」页无法使用**：TopNavigation 桌面/移动两处链接误写 `/players/{用户名}`(复数,与公开玩家目录路由冲突/无此路由),实际路由是 `/player/:username`——点击后无法到达资料页称号面板;已改为正确路径(与聊天广场在线列表、玩家目录页的链接口径一致)

### Added（两步验证 · 阶段 C）
- **2FA(TOTP 两步验证)**:验证器 App 扫码绑定(otpauth URI+二维码),RFC 6238 原创实现(零新依赖,±1 窗口容错);新表 dp_user_2fa(V13)
- **恢复码**:启用时生成 8 个一次性恢复码(库中只存 bcrypt 哈希,用即删),验证器丢失时可用于登录
- **邮箱备用通道**:登录卡片一键发码到绑定邮箱(内存态 5 分钟,3 次/5 分钟,与注册验证码池隔离)
- **登录流**:密码通过 → `needs_2fa` + 5 分钟 challenge(服务端内存凭据,非 JWT,不授予任何会话权限)→ 验证码(TOTP/恢复码/邮箱码)→ 签发正式 token;玩家与管理员同一套逻辑
- **管理员强制 2FA**:系统设置「账号安全」开关(默认关);开启后名单内管理员未绑定登录会被引导强制绑定页(扫码→确认→恢复码→完成登录)
- **防爆破**:验证连错 5 次锁 15 分钟;验证 10/min/IP、邮箱码 3/5min;停用需密码+验证码双因子
- 前端:Login.vue 新增两步验证/强制绑定卡片;Dashboard「两步验证」卡片(开启向导/恢复码展示/停用);新增 qrcode 依赖(MIT)
- 测试:TotpServiceTest(Base32 编解码/生成验证/窗口容错)5 例,共 35 测试全绿
- 冒烟:强制绑定→登录→错码 401→邮箱码→TOTP 登录→恢复码登录(含单次性)→停用→回归,全链路通过;修复恢复码校验哈希格式不一致问题(生成含连字符、校验未兼容)

### Added（社区与内容 · 阶段 B）
- **一级导航「社区」聚合页**(/community):论坛 / 投票 / 反馈工单三个 tab
- **论坛(进阶版)**:板块 CRUD、游客可读、发帖 3/天·回帖 10/天·30s 限频、置顶/精华/锁定、帖子与回复点赞、@提及通知、10 分钟内编辑留痕、Markdown+DOMPurify 渲染;**审核模式可切换**(默认先发后审+敏感词兜底,可切先审后发,审核动作通知作者);后台「论坛管理」Tab(设置+待审队列+板块管理)
- **投票**:单选/多选、草稿→开启(全员铃铛)→关闭、截止时间、**结果可见性逐场可配**(实时公开=默认/结束后公开);后台「投票管理」Tab
- **反馈工单(多轮对话式)**:玩家提交(3 条/天)→管理员回复(**铃铛+邮件**,新模板 feedback_reply_*)→追问→关闭;后台「反馈工单」Tab 会话式处理
- **通用事件 Webhook**:多个目标 URL+独立 HMAC-SHA256 签名(X-DP-Signature/X-DP-Timestamp)、事件过滤、异步+2 次退避重试、测试按钮;事件源挂审计骨架(review/appeal/questionnaire/reward/feedback/forum/poll/community/server),村谱/机器审核**补齐此前缺失的审计**
- 基建:V12 迁移(10 张 dp_ 表)、SensitiveWordFilter 从 ChatService 抽出共用、SimpleRateLimiter 滑窗+日配额限频器、新增 CommunityInfraTest 单测(共 30 测试全绿)

### Added（监控与多服 · 阶段 A）
- **服务器资源监控**：插件心跳附带 TPS(1/5/15 分钟)/平均 tick 耗时/JVM 内存/进程 CPU/运行时长（`features.report-metrics` 开关，默认开；Velocity 代理报内存/CPU 无 TPS）；新表 `dp_server_metrics`（V11，7 天保留）；`GET /api/server/metrics?serverId=&hours=24|168` 公开查询
- **修复假 TPS**：`/api/server/status` 的 `tps` 此前硬编码 20.0，现返回主服真实 TPS（无指标数据为 null，前端显示"—"）；分服心跳附带 `tps1m/memUsedMb/memMaxMb/cpuLoad`
- **TPS 低阈值告警**：默认 <15 连续 3 次心跳触发管理员铃铛+通知邮箱（1 小时冷却），`metrics.config` 可调阈值/关闭
- **按服 token 粒度管理**：启用 `dp_server.token_hash`；后台「服务器管理」Tab（新）——分服心跳状态/签发·轮换令牌(明文仅显示一次,库中只存 SHA-256)/按服启停；`security.config.tokenMode = shared(默认)|per_server`，per_server 模式下 `/internal/v1/**` 按 `X-Server-Id` 校验该服令牌且停用即拒，全局令牌保留为应急通道(命中记 warn 审计)
- **地图集成升级**：门户地图条目结构化 `map_items[{name,url,type: bluemap|dynmap|generic}]`（后台动态行编辑器，兼容旧 `map_url` 竖线格式）；新公开端点 `GET /api/map/live?i=` 由后端代理 BlueMap/Dynmap 玩家位置 JSON（URL 只取后台已配置条目，杜绝 SSRF）；地图页新增「在线位置」侧栏（头像+世界+坐标，15s 刷新，点击深链定位），iframe 嵌入不变
- 后端审计码新增：`server_token_issue`/`server_token_mode`/`server_enable`/`server_disable`

### Fixed（前端动画/过渡）
- **修复卡片 hover 上浮全站失效**：移除全局 `.card` 入场动画（0.4s + `:nth-child` 交错延迟）——其 `animation-fill-mode: both` 在动画结束后永久锁定 transform（级联优先级高于普通声明），压住 `.card-hover` 的 hover 位移；且任何列表过滤/翻页/弹窗开关引发的卡片重挂载都会整批重播入场动画（后台管理页尤甚）
- 页面入场收敛为**路由挂载时整页一次性上浮淡入**（`main > *` + `backwards` fill）：只动页面根节点，页面内部再渲染不再重播；nth-child 位置式交错延迟一并移除
- **修复顶栏用户菜单/通知中心零动画**：原 `<transition name="modal">` 的样式定义在 AppModal 的 scoped style 里，作用不到这两个下拉（一直瞬间弹出）；统一改用全局 `pop` 过渡（右上锚点淡入下滑），AppModal 遮罩过渡同步上移全局共用，弹窗入场不再与卡片入场动画叠加
- **全局通知（toast）补出场动画**：改用 `TransitionGroup`——进场上滑、出场右移、多条堆叠重排走 FLIP 平滑上移（原 5 秒后瞬间消失且下方条目跳位）
- 问卷计分弹窗补遮罩过渡（原裸 `v-if` 瞬间出现）；移动端菜单补滑入/滑出过渡
- 全仓 48 处 `transition-all` 定向化为 `transition-colors`/`transition-[width]` 等具体属性（进度条宽度、下拉可见性、hover 配色各自精确过渡，避免布局属性被意外纳入过渡）

### Removed（前端死代码）
- index.css 无引用的 `.page-*`/`.slide-*` 过渡（路由 transition 已历史移除）、Questionnaire 未引用的 `fadeInScale` keyframes、Tailwind 配置 4 个未用动画定义（fade-in/slide-up/scale-in/float）、TopNavigation 3 个无引用样式类

## [1.4.1] - 2026-09-09

**热修:守则门死锁——玩家无法确认协议导致 ID 验证/绑定/改 ID 卡住**。

### Fixed
- **守则门死锁(玩家卡在「确认协议」)**:v1.4.0 守则门要求服务端验证/绑定/改 ID 前必须先同意守则,但前端唯一守则入口(Verify 页)在玩家「已验证(绑定过 UUID)」时被隐藏,且控制台(Dashboard)没有守则入口——已绑定 UUID 的玩家(含守则门上线前注册的老玩家)想同意守则却无处可点,验证/绑定/改 ID 全部被服务端 400 拦截,生产 dp_rules_consent 因此 0 行
- **Verify 页**:守则卡片显示条件去掉「已验证」短路——只要未同意守则即显示(与服务端守卫一致),消除死锁
- **控制台(Dashboard)**:Minecraft 账户信息卡片上方新增「服务器守则」同意区块(倒计时→勾选→同意),玩家在控制台即可确认协议后继续验证/绑定/改 ID

## [1.4.0] - 2026-09-09

**守则门强制阅读 · 积分任务系统 · 称号系统全量上线(游戏内 PAPI 显示 + /titles GUI + 网页展示佩戴 + 游戏内颜色可定义)· 奖励礼包发放 · 全仓库安全审计修复 · playertitle 旧称号数据迁移**。

### Added(守则门 · 积分任务 · 称号系统)
- **守则门**(67976d1):注册后验证前强制阅读服务器守则——后台可配置守则文档与强制阅读秒数,内置默认守则,服务端 set/verify 双守卫校验(dp_rules_consent 表)
- **积分任务系统 P1**(a30320c):积分账本/任务中心/兑换邮件(游戏内 GUI)/后台可视化配置;任务按类型/周期/渠道定义,达标自动发放积分并支持兑换
- **称号系统**(V9 三表 + 引擎 + 后台 CRUD):游戏内称号显示切换(PlaceholderAPI 变量 `%dreamport_title%` / `_raw` / `_code`,由外部聊天/Tab 插件消费);`/titles` 佩戴 GUI(点击佩戴/脱下,拦截拖拽);后台称号/成就管理(定义 CRUD + 成就指标/门槛/奖励称号 + 手动授予/撤销);成就引擎按 4 种指标(累计在线/注册天数/成功邀请/累计积分)定时评估自动授予并站内通知

### Added（奖励礼包）
- **礼包化奖励发放(端到端)**:管理后台新增「奖励发放」页——① 奖励礼包卡:Web 创建礼包模板 → 服内管理员把物品放进背包执行 `/xmw kit save <礼包名>` 采集上传(Paper ItemStack#serializeAsBytes 序列化 Base64,自带数据版本号,MC 升级自动迁移)→ 状态变「可发放」,列表展示内容概要/采集人;每个礼包可配附加指令(与物品混合发放);② 发放奖励卡:单人/全员(已通过白名单)发放,来源选礼包或手填指令包,发放时把物品+指令**快照**进游戏内邮件(dp_mail 新增 items MEDIUMTEXT 列)
- **游戏内邮件支持物品直发**:领取时整包反序列化→背包空间检查(不足提示清理后重试,邮件保留零损失)→物品入包→再执行附加指令→异步回执;`/xmw kit list` 服内查模板
- 新端点:`POST /internal/v1/kit/save`、`GET /internal/v1/kit/list`(server-token);`/api/admin/rewards/{kits,send}`(admin JWT+审计+铃铛通知);Flyway V10(dp_reward_kit 表+dp_mail.items 列)

### Security（服务端）
- **修复任意已通过玩家可提权 admin**：`/api/admin/login` 增加管理员名单校验（原 P4 待办未实现，等于管理后门）
- **修复问卷 SSE 流式提交越权**：`/questionnaire/stream` 强制 username=当前登录者；`saveResult` 状态机改为已通过/封禁用户重答不降级（原可代他人答题并把其状态改写为 rejected 踢出服务器）
- **修复问卷计分规则泄露**：config 不再下发选项分值，客观题改按选项 id 精确计分（原文本 contains 匹配可拼接选项文本刷满分，白名单门槛形同虚设）
- **修复 7 个 /internal/v1 端点免鉴权**：`/activity` `/signin` `/mail/pending` `/mail/claimed` `/title/active` `/title/mine` `/title/equip` 补 server-token 校验（原可匿名读/销毁他人奖励邮件、代签到刷积分、改他人称号）；并移除 `/internal/**` CORS（防浏览器跨站调用）
- **修复 `/api/chat/save` 无鉴权**：补登录+本人+限流+敏感词过滤（原可匿名冒充任意玩家刷入公开聊天历史）
- **WS 审核推送仅 admin 角色可订阅**（原任意登录用户可监听全部审核/封禁流水）
- **公开玩家资料页 QQ 号脱敏**；Mojang 查询错误不再回显内部细节
- **限流/访问日志统一取 XFF 最后一跳**（原信任第一段可伪造绕过登录/注册/验证码限流）
- **SettingService 删除改参数化**；头像/机器/门户上传加 magic-bytes 校验+头像重编码与清理旧文件，SVG 新上传禁收（CSP sandbox 兜底旧文件）
- **头像服务 SSRF 修复**：皮肤 URL 仅放行 https 且 host 在 minecraft.net
- **邀请码原子消费**（原 TOCTOU 可并发双人同用一码）；QQ 绑定同步化；问卷 SSE 改有界线程池；AuthFilter 前置修正访问日志用户标记

### Fixed（功能）
- **在线时长任务/成就复活**：`dp_daily_activity` 此前全库无任何写入，`/internal/v1/activity` 会话时长落库后「今日在线 X 分钟 / 周 5 小时」任务与累计时长成就可正常推进

### Frontend
- **补齐 api.ts 缺失的 5 个称号管理方法**：修复 Admin「系统设置」整 Tab 加载失败（原 TypeError 使 12 项配置全部丢失）与称号/成就 Tab 瘫痪；saveTitles/saveAchievements 请求体改裸数组对齐后端契约
- **公告管理装载已有数据**（原打开即空、点保存即清空线上公告）；称号总览并入系统设置加载
- **SSE 聊天改一次性流票据**：JWT 不再进 URL（新增 `/api/chat/stream-ticket`）
- **401 统一清 token 回登录**；App 挂载以服务端校验刷新 isAdmin；问卷 complete 事件写 sessionStorage 复活 `/questionnaire-result`
- 客观题按选项 id 提交（与后端新评分契约同步）；Verify 倒计时去叠加；TopNavigation 监听卸载清理；PlayerChart 单点除零；Dashboard 在线状态按心跳玩家列表判定（原恒显"在线"）；Status 时间线不再用当前时间冒充审核时间；Docs 裸 fetch 收口 api 层；Portal 移除假管理员/假轮播示例数据；去重复路由/方法

### Plugin（Paper/Velocity）
- **奖励邮件领取跨 GUI 会话幂等**（原重开 /mail 可双倍奖励）；回执改奖励执行后异步发送
- 进服邮件提醒延迟 100 秒 → 5 秒；热重载 i18n 即时生效；onDisable 释放 HttpClient；/xmw 消息改主线程路由
- 决策缓存上限；心跳上报显式 server-name；称号拉取补日志；GUI 拦截拖拽
- **默认 server-token 清空**（原 dev-internal-token 与后端默认一致，漏配等于无鉴权）
- Velocity：login-check 响应改 Gson 解析、超时收紧、缓存上限、默认 token 清空

### Fixed(称号 · 配置)
- **修复称号/成就/任务/商店配置从未生效的重大 bug**：定义存 JSON 对象，`get(key, String.class)` 反序列化失败静默返回 null，后台保存的定义(自定义称号、enabled 开关、颜色、任务、兑换商店)运行时永远回退默认——改 `getRaw` 取原始 JSON(影响 titles.config / achievements.config / tasks.config / shop.config)
- **游戏内称号颜色可定义**：称号定义新增独立「游戏内颜色」，聊天/Tab/GUI 显示优先用它、留空跟随网页色；内部端点(插件 active/mine)下发生效色，网页展示仍用网页色
- **玩家网页称号配置入口**：PlayerProfile「我的称号与成就」面板(已拥有佩戴/脱下、未解锁、成就进度)+ 用户菜单「我的称号」入口直达(?titles=1 自动滚动)
- 服务端健壮性：撤销参数/用户/拥有校验、定义保存结构校验(畸形 400 不再静默回退默认)、成就奖励引用校验、已禁用称号全局隐形(enabled 口径统一)；公开玩家资料页返回当前佩戴称号(title 字段)

### Migration
- **playertitle 旧称号数据迁移**(2026-09-09)：旧称号插件 title 库 3 种称号映射迁移至 DreamPort——屠龙者→`dragon_slayer`(#FFAA00)/弑龙者→`dragon_killer`(#AA00AA)/管理组→`admin_team`(#FF5555)；写入 dp_setting.titles.config(仅 3 个新称号，不含默认，默认称号由用户后台自行维护)；导入 dp_user_titles 11 条拥有记录与 dp_title_equipped 6 条佩戴记录；旧插件 title_coin/reward_log/buff/particle 无对应概念不迁移

## [1.3.0] - 2026-09-08

**收口纯正版账号**:产品只服务正版玩家——移除 BS 皮肤站互通与微软正版绑定,头像全面本地双层渲染,全局移动端适配。

### Removed
- **移除 BlessingSkin 皮肤站互通**:OAuth2 Provider 全端点(authorize/token/userinfo)、/api/user/bs/*、BlessingSkinService、blessingskin 设置端点与管理卡、注册玩家分型(premium/offline/bedrock → 仅正版)、控制台皮肤站角色卡、BS 插件目录(bs-plugin-dreamport/)与全部分册文档;改密/改名/改邮箱不再同步皮肤站,改邮箱直接落本站
- **移除微软正版验证**:MicrosoftOAuthController(/api/auth/microsoft/start|callback)整体删除——该链路从未持久化绑定结果(microsoftVerified 无任何写 true 路径),属未接线死代码;前端类型同步清理
- 仓库地图/构建脚本同步:build.sh 收为三步,不再产出皮肤站插件 zip

### Changed
- **头像正版识别改 Mojang 按名查档**:名字在 Mojang 官方库存在即取官方皮肤(官方 UUID 缓存命中 24h/未命中 1h,不可达自动降级且不缓存);移除原 microsoftVerified 判定与皮肤站插件取材层
- **头像服务三级缓存**:浏览器 ETag + 内存 LRU + 磁盘持久化 data/avatar-cache/(重启不丢);冷缓存解析移出锁外并发执行;过期条目后台单线程静默刷新(SWR),显示永不等网络(实测:冷 3.8s→内存 0.004s→重启后 0.099s 零 Mojang 外呼)
- **全站头像默认方形圆角**(rounded-xl,大尺寸 rounded-2xl)
- **控制台头像统一玩家皮肤大头照(双层)**:个人资料卡与 Minecraft 卡均按「绑定 MC 名→账号名」解析,下线「更换头像」上传入口(后端 /user/avatar/upload 端点保留)
- **新增按 UUID 一键同步新 ID**:正版改名后控制台点一下即向 Mojang 查询该 UUID 当前绑定名并更新(官方 v4 UUID 生效;非官方 UUID 引导重新进服验证)
- **全局移动端适配**:≤640px 根字号 15px 全站等比缩放;无响应式前缀网格补 sm: 堆叠;首页区块收缩;UUID 长串 break-all;375×812 视口实测 17 条路由零横向溢出
- **深色玻璃小字对比度全站提亮**:stone-500/600/700 与占位符提亮至可读水平(≥5:1)
- 排行榜四榜单(财富/在线时长/活跃天数/封禁)玩家 ID 前增加 36px 双层皮肤头像

### Fixed
- **玩家详情恒显"玩家不存在"**:/api/players/profile 原返回裸对象(无 success 字段),前端按 r.success 判定恒假——改统一 {success,data} 包装
- **玩家详情经济/时长数据缺失**:快照按游戏名存储,原按账号名匹配——改游戏名优先(minecraftName→回退 username)
- **总游戏时长/活跃天数全为 0、登录次数缺失**:插件时长仅从 EssentialsX userdata 采集,生产未装——补 Bukkit 原生统计兜底(TOTAL_WORLD_TIME/20)并新增登录次数(LEAVE_GAME)端到端透出
- **封禁弹窗无法填写理由/时长**:确认弹窗从未渲染输入框(showBanDaysInput 被忽略、理由恒 undefined)——补齐「封禁理由」「临时封禁天数」输入并端到端提交
- 管理员名单设置卡去除嵌套双层样式,与其他设置卡统一

## [插件 1.2.2] - 2026-09-08

### Changed
- **撤回皮肤站主站主题注入**:BS 插件「UI 同步主站」(dp_theme_sync)整体移除——bootstrap.php 不再经 RenderingHeader 注入深色玻璃主题,theme-sync 视图删除,配置页开关与保存逻辑同步下线;皮肤站恢复 BlessingSkin 原生主题
- 插件玻璃风配置页(对接指南/连接测试/全部配置项)保留;build.sh 断言改为核对登录按钮注入/配置页/保存端点;发行 zip 换为 dreamport-oauth-1.2.2.zip

## [1.2.0] - 2026-09-07

自 1.1.0 以来的 37 个提交:皮肤站互通、封禁体系、注册分型、UI 大版本迭代与全插件文档库。

### Added
- **BlessingSkin 皮肤站互通**(dreamport-oauth 插件 v1.1.0):DreamPort 作 OAuth2 Provider,皮肤站网页 SSO 登录;**注册分型一键开通**(非正版玩家注册即开通皮肤站账号,密码与 DreamPort 相同 + 初始积分 + 同名角色);账号双向同步(改密码/改游戏名/改邮箱);纯 SSO 模式(隐藏皮肤站账密表单);角色数据回传(控制台 3D 头像与皮肤/披风);DP 新增 OAuth2 Provider 端点与 `/api/user/bs/{players,provision}`
- **封禁体系**:公开封禁名单页 `/bans`(大头照+ID+原因+时长);临时封禁(ban_until、管理后台天数输入、每小时自动解封);封禁/解封邮件通知
- **Microsoft 正版账号绑定**(Microsoft→XBL→XSTS→Minecraft profile 完整链路;待 Azure 配置联调)
- **功能补全七批次**:密码修改/申诉入口与管理员名单 UI;村谱/机器/申诉审核结果通知提交者;UGC 风控(评论与聊天限频、敏感词过滤、照片墙留言先审后发);问卷导出(CSV/JSON);申请进度时间线(/status 四步);公告草稿/定时发布;服务器离线告警(铃铛+邮件)
- **门户新页面**:公告页(资讯中心+更新日志)、聊天广场独立页(/chat,在线玩家列表)、封禁页
- **管理后台改版**:左侧菜单布局 + 文档管理(实时预览)+ 公告管理 + 管理员名单 + QQ 互通/BlessingSkin 互通设置卡 + 照片墙分类管理与留言审核 + 图标选择器
- **玩家端**:修改密码卡、QQ 绑定卡、铃铛通知中心、修改邮箱、头像上传
- **全插件文档库 `wiki/`**(31 篇):玩家册 6 篇 + 服主册 24 篇(后端/Paper/Velocity/AstrBot/皮肤站分册 + 故障排查)
- 群组服经济快照由主服推送(`economy.report: auto`)
- **大头照双层皮肤渲染**(替换 crafthead 外网依赖):新增 `GET /api/avatar/{name}?size=N`(公开,双层=基础脸+帽子层,最近邻放大,兼容 64x64/64x32);正版玩家直接取 Mojang 官方皮肤、非正版走皮肤站插件接口、未知名字程序绘制默认脸(Steve/Alex);两级缓存(浏览器 Cache-Control+ETag 1h、服务端 LRU);前端 AppAvatar 与全部头像位统一切换;皮肤站插件 1.1.2 新增皮肤材质接口
- **皮肤站配置页重设计 + UI 同步主站**(插件 1.2.1):玻璃风配置界面(对接指南+连接测试);「UI 同步主站」开关让皮肤站整体风格跟随主站品牌色/背景图

### Changed
- **全局品牌可配置**:门户配置新增品牌短名/副标语/Favicon/品牌主色,登录注册页、导航、页脚、document.title、favicon 全部跟随;主题色以 CSS 变量驱动全站 orange 色阶(含透明度写法),换品牌=管理后台改配置零代码
- 默认示例对齐 xmcraft.cn 生产内容(品牌全名/副标题/描述/公告/轮播/时间线)
- 前端视觉两期升级:公共组件抽取(AppAvatar/AppModal/AppPagination/EmptyState/StatCard)+ 液态玻璃视觉规范
- 顶部导航重构:一级菜单[首页 文档 公告 白名单 玩家 封禁 聊天 更多]+ SVG 图标 + 自适应单行 + 页面顶端全透明
- 用户区改版:hover 用户卡 + 头像统一 MC 头像;弹窗不点空白关闭 + ESC
- 注册页玩家类型三选一(正版/非正版/纯基岩),游戏名收紧为 3-16 位字母数字下划线
- Velocity 插件版本号与主版本对齐(1.2.0)

### Fixed
- **偶发路由跳转白屏**:页面切换的 `<transition>` 在特定时序下永久卡死(leave 不释放,后续导航全部无渲染,需刷新)——已移除路由过渡包装,页面瞬时切换;管理面板空白(setup 主体调用 TDZ、AppIcon 未定义引用、模板多余闭合)
- 时光照片墙嵌套事故修复 + 分类可添加;管理页标题行错位
- 基岩版取消验证变"等待验证"、页面滚动黑闪、控制台信息排序等联调反馈批次
- 皮肤站插件 1.0.0→1.1.0:zip 平铺结构、BS6 登录页按钮、enchants.config 类名、纯 SSO 注入

## [1.1.0] - 2026-09-06

### Added
-  部署与运维检查清单（首次部署核对/日常运维/故障排查/终端日志速查）
- AstrBot 重构方案定稿（docs/ASTRBOT_PLAN.md：QQ 验证绑定 + 群服消息互通，含现状实测与开源调研）
- 管理后台「QQ 互通」设置卡：启用开关 + API 令牌生成（`GET/PUT /api/admin/settings/astrbot`，token 脱敏回读）
- **QQ 验证绑定全流程**（docs/ASTRBOT_PLAN.md §5.2）：`POST /api/astrbot/bind/request` 申请 6 位验证码（5 分钟有效、每 QQ 1 次/分钟、5 次/天、重新申请覆盖旧码）；网页「个人中心 → QQ 绑定」卡与游戏内 `/xmw qq bind <码>` 双通道凭码确认；单绑定语义（新绑定自动清除该 QQ 旧绑定）；按提交者计错误次数，10 分钟窗口内错 3 次锁定
- 用户侧端点（JWT）：`GET /api/user/qq/status`（QQ 脱敏展示）、`POST /api/user/qq/bind`、`POST /api/user/qq/unbind`，绑定/解绑写入审计日志
- 服务器间端点 `POST /internal/v1/qq/bind`（X-Server-Token 鉴权），供 Paper 插件游戏内确认通道调用
- BindCodeService 状态机单元测试 9 例（验证码生命周期/频控/覆盖/锁定窗口/空码安全）
- **群服消息互通后端**（docs/ASTRBOT_PLAN.md §5.3）：QqBridgeService 双向队列（各 200 条，瞬态）+ 模板渲染（单遍扫描防占位符二次替换）+ 群绑定解析（mode: all/prefix、forward_join_quit）；游戏聊天/进出服、网页聊天自动出站到绑定群；`POST /api/astrbot/chat {group, sender_id, sender_name, message}` 群消息上行（群白名单/前缀校验，防回环：入站消息不写出站队列）；下行 `GET /api/astrbot/stream`（SSE，X-Accel-Buffering: no）+ `GET /api/astrbot/messages?since=`（轮询 fallback）；游戏收件箱轮询端点 `GET /internal/v1/messages/pending?since=`（X-Server-Token，M4 插件接入）；配置键 `astrbot.group_bindings`、`astrbot.forward.{game_to_qq,web_to_qq,qq_to_game}`、`astrbot.template.{qq_chat,qq_join,qq_quit,game_chat,web_chat}` 全部热生效
- SeqQueue 有界序号队列 + 渲染/群绑定解析单元测试 7 例
- **自研 AstrBot 对接插件** `astrbot-plugin/`（astrbot_plugin_dreamport v1.0.0）：`/dp` 指令（绑定/解绑/查询/状态/玩家/帮助）、绑定验证码私聊送达（私聊失败回退引导）、群消息上行（self_id 防回环 + 指令过滤 + 群白名单）、下行 SSE 实时发群（指数退避重连 + umo 学习缓存）；原创实现仅运行时调用 AstrBot 公开 API；部署与真机联调清单见 `astrbot-plugin/README.md`
- USER_GUIDE 新增第 10 章「QQ 互通（AstrBot）」；API_CONTRACT 机器人端点章节更新为 v1.1 契约（免验证 bind 移除、互通端点、internal 收件箱）
- **插件消息下行**（docs/ASTRBOT_PLAN.md §5.6）：Paper 插件新增游戏收件箱轮询（`features.receive-chat` 默认开、`features.message-poll-seconds` 默认 2s）→ 主线程 `broadcastMessage` 广播进游戏；首次拉取仅快进游标不回放历史；common 新增 `PendingMessagesResponse` DTO 与 `Protocol.MESSAGES_PENDING/QQ_BIND` 常量

### Changed
- AstrBot 集成 v1.1 门禁（docs/ASTRBOT_PLAN.md §5.4）：新增 `astrbot.enabled` 总开关（默认关闭，关闭时 `/api/astrbot/**` 全部 403）；开关与 `astrbot.api_token` 均可在管理后台配置
- `GET /api/astrbot/players` 契约定版为 `{count, players:[{name, server}], servers:[…]}`（原仅 `{servers}`）
- `POST /api/astrbot/unbind` 改为按 QQ 解绑（`{qq}`），无绑定时返回失败提示
- **聊天历史落库**（docs/CHAT_SERVERINFO_PLAN.md，M6）：新增 Flyway V3（`dp_chat_message` 表 + `dp_server` 注册表补列 + `dp_online_history` 采样表）；`ChatService` 从 dp_setting JSON 全量重写改为单条落库（四源 origin：game/web/qq/system，SSE payload 同步携带），消除写放大与 500 条上限；旧 `chat.history` 键停写弃用（按决策不迁移）
- `GET /api/chat/history` 重定义：`{success, data:{history, nextBefore}}`，支持 `?before=`（游标向前翻页）/`?limit=`（≤500）/`?origin=`（过滤四源），一律限 7 天窗口；此前响应结构 `{history}` 与前端读取 `data.data` 不匹配、历史从未成功加载（本次一并修复）
- 保留策略：聊天消息 7 天 + 5 万条硬顶，每小时定时清理
- ChatBox 历史浏览：进入加载最近 200 条、「加载更早」游标翻页（保持视口位置）、跨天消息显示日期、裸 fetch 全部收口至 `services/api.ts`（修复 Rules §7 违规）
- **服务器信息落库**（docs/CHAT_SERVERINFO_PLAN.md，M7）：心跳到达即 upsert `dp_server` 快照（name/role/version/online/max/last_heartbeat，实时保存重启不丢）；在线采样（5 分钟）落 `dp_online_history`（7 天保留）；`GET /api/server/player-history?days=7`（1–7，新增分服 `data.servers` 曲线，数据源改库）；`GET /api/server/status` 重启后心跳未到时从 `dp_server` 快照兜底（标记 `stale: true`，不再返回空白）
- 插件 `tasks.*` 配置接线（此前模板存在但未生效）：`heartbeat-interval`（≥10s）/`whitelist-poll-interval`（≥5s）/`economy-interval`（≥30s）可调，默认值不变
- Dashboard「在线人数趋势」图支持近 7 天（`PlayerChart :days="7"`，X 轴标签随天数自适应）
- **孤儿 UI 清理**：聊天室组件 ChatBox 挂载至个人中心（此前无任何页面引用，网页端从未有聊天入口）；删除孤儿页面 `Home.vue`（旧版简版首页，已被 Portal 取代）；注册 `/status` 路由并在导航「更多」菜单加入口（免登录申请状态查询）

### Fixed
- `/status` 申请状态查询页响应消费契约错误：期望 `{success, data}` 而后端返回裸 `{found, username, status, ...}`，导致即使可访问也永远提示"查询失败"；已按裸结构消费，并新增"未找到申请记录"提示与拒绝原因展示；查询改走 `api.getReviewStatus`（消除裸 fetch）

### Fixed
- `/api/astrbot/lookup/qq/{qq}`、`/api/astrbot/lookup/mc/{mc}` 参数绑定错误：误用 `@RequestParam` 导致路径风格恒 400、查询风格 404，端点完全不可调用；已修复为真路径参数，`lookup/mc` 响应补充 `bound` 字段
- 插件 `BackendClient.get()` 不携带服务器鉴权头，导致 `/internal/v1/**` 的 GET 类调用（whitelist 指令轮询、`/xmw list`、`/xmw info`）持续被 401 静默拒绝；新增 `getInternal()` 并全部改用，QQ 收件箱轮询同走该通道

### Removed
- 免验证直绑端点 `POST /api/astrbot/bind`（任何持 token 者可直接冒绑任意账号，无验证环节）；QQ 绑定将改走一次性验证码流程（方案 §5.2，后续里程碑）

## [1.0.0] - 2026-09-06

### 首个正式版（Stable）

自 v0.1.0 脚手架起共 57 个提交，完成 P0–P8 全部阶段：

- **架构**：独立后端（Spring Boot 3.4 / Java 21 虚拟线程）+ Paper 薄插件 + Velocity 代理端插件 + Vue 3 前端
- **数据**：dp_ 新 Schema（Flyway 管理）+ 旧库自动迁移器（同库/后台上传 .sql/文件存储），密码双算法无缝兼容
- **玩家侧**：门户官网、注册（验证码/邀请码）、问卷（四题型/AI 评分/人工复核）、ID 验证、邀请、通知、排行榜、村谱、公共机器、网页聊天
- **管理侧**：统一审核工作台（WS 实时推送）、问卷编辑器、门户/文档/下载可视化配置、系统设置（热生效）、维护模式、审计、导出、AstrBot 对接
- **插件**：进服拦截（缓存+fail_policy）、事件上报、经济快照、whitelist 指令队列、/xmw 全命令、bStats
- **质量**：104 条前端调用运行时探测 0 缺失；15 主路由 200；真实旧库迁移演练通过；JWT/限流/审计/CORS/三通道鉴权完整

### 已知待办
- Microsoft 正版 OAuth 绑定（可后续小版本加入）
- 生产环境压测（可选）

### Changed
- **v0.6.0 文档整理**：统一文档措辞、精简吸收项追踪、移除冗余比对文件

### Changed
- **v0.5.25 收尾**：
  - 删除死组件 ScoringModal.vue（问卷页使用内嵌评分弹窗）
  - 运行时全量路由探测 104 条前端调用 0 缺失；15 个主路由 200
  - 全量重建（前端 + server fat jar + Paper 插件 + Velocity 插件）通过

### Fixed
- **v0.5.24 生成邀请码报 Cannot read properties of undefined (reading code)**：
  /invite/generate 返回缺 data.code（前端读 res.data.code）；
  my-codes/pending 缺 {success, data} 包装。InviteService.Result 携带 code，
  generate 返回 data.code；my-codes/pending 统一 {success, data} 包装
  （E2E: 生成→data.code→my-codes 含新码→pending 数组）

### Changed
- **v0.5.23 邮件模板品牌化重设计**：
  - 统一骨架：左上角 logo（绝对地址）+ 站名页眉 + 橙色品牌条 + 内容区 + 站名/官网链接页脚
  - table 布局 + 内联样式（Gmail/Outlook/QQ 邮箱兼容，替换原 <style> 块方案）
  - 10 个模板全部重写：验证码/通过/拒绝/密码重置/问卷结果 × zh/en
    （新增 password_reset 模板，原为内联 HTML）
  - MailService 自动注入 {logo_cell}/{logo_url}/{server_name}/{site_url}
    （logo/站名读 portal.config；站点地址读 game.config.webRegisterUrl）
  - 无 logo 配置时页眉退化为仅站名文字

### Fixed
- **v0.5.22 生产环境问卷评分不实时（反向代理缓冲）**：
  - 生产实测（xmcraft.cn 真实浏览器）证实：16 个 SSE 事件在流结束时同一毫秒
    一次性到达 —— 反向代理默认缓冲整个 SSE 响应（本机直连为渐进到达）
  - 修复：/api/questionnaire/stream 响应加 X-Accel-Buffering: no
    （nginx 标准机制，对该响应禁用缓冲逐块转发）+ Cache-Control: no-store
  - 注意：若反向代理非 nginx，需在代理配置禁用响应缓冲（nginx: proxy_buffering off）

### Fixed
- **v0.5.21 问卷进度显示与前端缓存**：
  - 进度改为「已评分 X/Y 题」（原 0 起始 index 显示成 0/15 似卡死）
  - 评分中实时显示当前题干；逐题卡片增加题干行
  - 评分完成展示总评（summary）
  - SPA HTML 响应 no-store（SpaCacheHeaderFilter），部署新版本浏览器立即生效
  - 回滚误加的 /index.html 资源处理器（文件级 location 导致 SPA 全 404）

### Fixed
- **v0.5.20 问卷提交卡在 AI 分析（0/15）**：
  - 前端 SSE 解析用 "data: "（带空格），而 Spring SseEmitter 实际发送 "data:"（无空格）
    → 所有评分事件被前端丢弃，后端评分完成但前端永远卡在 0/15
  - 前端流式请求补 Authorization 头（原裸 fetch 不带令牌，后端判未登录直接断流）
  - 前端处理 error 事件与流中断（原直接忽略，永久转圈）
  - 流式路径去除重复评分（原先逐题 LLM 评分后 submit() 又全量重评一遍，LLM 调用翻倍），
    改为流内结果直接落库（saveScoredResult）；SSE 超时 120s → 不限时
  - question_scored 事件补 maxScoreTotal 字段（前端累计满分显示）
  - 邮件乱码修复：result_items 由 Java record toString 改为格式化 HTML
    （题干/得分/评语卡片），questionnaire_reasons 存 JSON 数组（管理后台详情可解析）

### Fixed
- **v0.5.19 选择题选项保存后消失**：save-bulk 的 BulkOption 记录缺
  @JsonAlias("text_zh")，前端发送的选项文本无法映射（textZh 恒为空），
  保存后选项文本全部丢失。已补别名；E2E 回读验证选项文本+分值完整保留，
  多选计分（选 2 项得 4/5）正确

### Fixed
- **v0.5.18 管理面板「验证页面」「数据迁移」不显示**：前次编辑误删问卷管理
  区块的闭合 div，导致这两个标签被嵌进问卷管理的 v-if 内部（仅问卷标签激活
  时才可能渲染）。已补回闭合 div 并移除尾部多余的对应闭合，模板深度平衡恢复

### Fixed
- **v0.5.16 完成验证后刷新仍显示等待验证**：
  - verified 改为身份验证语义（minecraftUuid != null），与白名单审核状态解耦
  - 验证成功状态流转：pending_verify → approved；pending 且问卷未启用 → approved
    （验证即完成白名单）；pending 且问卷启用 → 保持 pending（还需答题）
  - 幂等判定放宽：已有 UUID 且无新进服记录 → 「已完成验证」（原要求 approved）

### Fixed
- **v0.5.15 外观设置页消失**：问卷设置卡片模板引用 qnCfg/saveQnSettings，
  但 script 中实际定义为 questCfg（前次编辑锚点未匹配静默失败），
  渲染时抛 ReferenceError 导致整页白屏。已统一命名并补上独立的
  saveQuestSettings 函数（此前问卷保存被错位嵌入 saveRegisterSettings）

### Fixed
- **v0.5.14 ID 验证重构（UUID 比对优先）+ Economy 崩溃 + 问卷设置**：
  - 验证改为 UUID 比对优先：进服记录与绑定 UUID 不一致时拒绝（防同名冒充）；
    首次验证采用进服记录中的真实 UUID（原 setMinecraftId 伪造随机 UUID 已移除）；
    重复验证幂等返回「已完成验证」（原 400）
  - /api/user/minecraft/status 补 minecraftUuid 字段；verify 响应统一带 data.verified
    （修复 Verify.vue "Cannot read properties of undefined (reading 'verified')"）
  - /api/cmi/player 500（ClassCastException）：快照 JSON 反序列化为 LinkedHashMap
    后显式转换为 PlayerEconomy
  - 问卷设置面板：启用开关 + 通过线（dp_setting questionnaire.config 热生效）；
    问卷未启用时玩家侧显示友好提示卡而非踢回登录

### Changed
- **v0.5.12 README 重写**：面向用户的展示型 README（产品定位/功能总览/三步快速开始/
  迁移能力/FAQ 折叠/路线图），开发者向内容移至 docs/ 文档体系

### Fixed
- **管理员无法进入后台（v0.4.2）**：普通登录接口恒返回 isAdmin=false，前端路由守卫
  依据 localStorage isAdmin 拦截 /admin。修复为语义对齐旧版：dp_setting admins.list
  内的玩家登录时签发 admin 角色令牌并返回 isAdmin=true
- **自定义启动界面（v0.4.1）**：关闭 Spring 原生横幅与启动日志（logback 压制框架日志至
  WARN），启动时打印 DreamPort 字符画 Banner + 中文启动记录清单（数据库/数据表/旧库
  迁移/初始化状态/AI 评分/邮件/邀请/维护模式/运行环境/耗时 + 访问地址）；启动失败输出
  中文原因与排查提示（静态监听器，早于 bean 创建的失败也能捕获）
- **首次启动初始化向导（v0.4.0）**：检测到无用户时 `/setup` 引导创建管理员账号 +
  选择「全新部署」或「上传旧库 .sql 导入」；管理员玩家名与旧数据同名时自动「认领」
  （重置密码/邮箱、设为 approved、写入管理员名单）；完成后向导永久关闭（重复提交 403）
- **管理后台「迁移」标签**：上传旧库 .sql 一键迁移（暂存表隔离，失败不污染现网，
  dp_user 非空时幂等拒绝；播种演示账号自动清理后放行）
- **仅支持 MySQL**：移除 H2 依赖与双迁移目录，dump 语句原生执行

### Changed
- 数据源默认直连 MySQL（env/config.yml 可配）；删除 mysql profile 与 seed-demo
  演示播种（由初始化向导取代）；flyway locations 固定 db/migration/mysql
- config.yml 模板：数据库段为非注释 [必改] 项；占位密钥/令牌改为 ASCII 长串（避免
  JWT 弱密钥拒绝与 HTTP 头编码问题），TokenService 启动时检测占位值并告警
- USER_GUIDE 同步重写快速开始/部署/迁移章节
- **config.yml 单文件部署模式（v0.3.0）**：首次启动自动在工作目录生成带中文注释的部署
  配置（数据库/JWT/服务器令牌/SMTP/LLM/迁移路径一处搞定），编辑后重启即生效；
  无需环境变量。优先级：命令行 > 环境变量（保留支持）> config.yml > 内置默认。
  已实测：配置生成/端口覆盖/自定义 JWT 密钥全部生效
- `wl.ws-port` 可配置 WebSocket 端口
- `docs/USER_GUIDE.md` 使用文档：快速开始/生产部署(systemd/Docker/环境变量)/配置两层说明/
  插件三角色安装/旧版迁移与回滚/玩家与管理员功能指南/命令权限/FAQ/安全清单
- `application.yml` 基础设施凭据全面支持环境变量（WL_JWT_SECRET/WL_SERVER_TOKEN/WL_SMTP_*）
- 项目仓库初始化：git 管理（main 分支）、MIT LICENSE、语义化版本规范
- README：重写版架构简介、模块划分、关键设计决策、路线图（P0–P8）
- 版本基线 `0.1.0-dev`；首个可运行脚手架构建通过后打 `v0.1.0` 标签
- `docs/IMPLEMENTATION_PROGRESS.md`：128 项功能实现进度总表（P0–P8 逐项状态追踪，吸收项落位编号）
- `docs/PROJECT_DOCUMENTATION.md`：完整项目文档（概览/架构/模块/数据架构/API 契约/安全/配置/部署/谱系声明）
- `docs/API_CONTRACT.md`：API 契约冻结稿（对外 /api/** 与旧版逐字兼容 + /internal/v1 服务器端点）
- **P0+P1 完成**：Maven 多模块脚手架（dreamport-common/server/plugin，构建通过并打 `v0.1.0` 标签）
  - server：Spring Boot 3.4 + Java 21 虚拟线程；Flyway V1（dp_user/dp_audit_log/dp_setting/dp_server）；JWT（role claim）；密码双算法（旧 `$SHA$` 恒时验证 + 透明升级 bcrypt，已实测）；IP 限流；CORS；统一响应包装；`/internal/v1/{login-check,heartbeat}`（X-Server-Token 鉴权）；演示账号播种（dev）
  - common：协议 DTO（LoginCheck/Heartbeat/ErrorCode/Protocol 常量）
  - plugin：三角色骨架（primary/secondary/proxy）+ `/xmw status|reload`

### Changed
- 项目定名 **DreamPort**，中文名 **「夏日小镇 · 梦港」**：主品牌为服务器"夏日小镇"，项目名"梦港"（DreamPort 直译），"夏梦"（XiaMeng → XM）为作者署名；宣传语"进入夏日小镇，先入梦港"
- 命名规范确立：模块 `dreamport-common` / `dreamport-server` / `dreamport-plugin`，Java 包根 `cn.xmcraft.dreamport`，新数据库表前缀 `dp_`，插件名 `DreamPort`
- README 路线图并入参考吸收项（代理端拦截/评分人工复核/delete 命令/bStats/前端组件库与测试/语言切换/CI/config_help）；**Discord OAuth 经用户决策排除**

- **P2–P8 主体完成**（v0.2.0）：
  - V2 迁移：10 张业务表（邀请/通知/待登录/密码重置/申诉/村谱/机器/问卷×3）
  - 迁移器：9 旧表改名备份+列映射导入、users.json/audits.json、config.yml→dp_setting、幂等报告
  - P3：邮件（双语模板+日志模式）、邮箱验证码、算式图形验证码、密码重置、资料/头像、
    MC/基岩 ID 绑定验证（3 分钟窗口）、问卷（题库入库+YAML 导入、LLM 评分 confidence+
    manualReview+熔断、统一 SSE、申诉）、i18n 全覆盖
  - P4：审核+审计+WS 推送(18899)、邀请状态机、通知、村谱/机器（含截图上传）、玩家目录、
    聊天 SSE、维护模式持久化、多服状态聚合、经济快照榜单、文档中心（防路径穿越）、导出
  - P5：插件完整实现（登录校验+缓存+fail_policy、事件上报、经济采集 Vault/Essentials、
    whitelist 指令队列、/xmw 全命令含 delete、bStats 官方库）
  - P6：前端基线导入+语言切换+SPA 托管（WebStaticConfig 路由回退 + /uploads）
  - P7：AstrBot 兼容端点（X-API-Token）、审核状态查询 FIX(legacy)、config_help 双语
  - P8：Gitea Actions 构建/Release 工作流、Dockerfile、systemd 单元
  - 全链路实测：注册→问卷→审核→进服放行→ID 验证→邀请/村谱/机器/聊天/维护 全部通过

- **v0.2.1 真实数据迁移演练通过（8-2 ✅）**：用户提供的生产 xmc 库 dump（19 用户/71 审计/
  8 邀请/4 通知/4 密码重置/1672 进服记录）本地 MySQL 隔离导入 → DreamPort 自动迁移：
  9 张旧表改名备份、1778 行 1.6s 导入、$SHA$ 哈希逐字节保留（password_algo=legacy_sha256）、
  错误密码登录 401（算法验证）、真实用户进服决策 allow、问卷分数/答案/基岩名/门户内容全保留

### Fixed
- Flyway 接入非空旧库需 baseline-on-migrate: true + baseline-version: 0（演练发现，
  否则 V1 建表被基线吞掉导致 dp_user 缺失）
- 限流器初版差一错误（达到上限时仍放行），已修复并实测 429 生效（达到上限时仍放行），已修复并实测 429 生效
- Spring Data JDBC @Query DELETE 不生效 → 改 JdbcTemplate；进服记录清理条件
  由 login_time 改为 expire_time（修复刚保存记录被误删）
- /api/review/status 改读 query 参数（FIX(legacy)：旧版 X-Username 头与前端不匹配）
- 多服在线数聚合去除重复计数；chatCount/activeDays 假数据改为真实快照统计

### 备注
- 旧版项目（v1.0.0）已归档至 `../XMWhitelist-Legacy/`，作为功能对标与数据迁移的参考基线
