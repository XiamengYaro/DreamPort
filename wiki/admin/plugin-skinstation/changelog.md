# 皮肤站插件 · 更新日志

## 1.2.1(2026-09-07)

- 修复:UI 同步主站不生效 —— 1.2.0 打包时 bootstrap.php 主题注入块静默丢失(编辑脚本替换锚点未命中且无断言);补回 RenderingHeader 注册块,build.sh 新增关键代码断言

## 1.2.0(2026-09-07)

- 配置页完全重设计:玻璃风界面 + 对接指南双栏卡 + 连接测试徽章(弃用 Option::form 默认渲染,保存走 `POST /dreamport/config/save`,role:admin)
- **UI 同步主站**(dp_theme_sync,默认开):全页注入深色玻璃主题(卡片/导航/表单/滚动条),JS 拉取主站 `/api/config` 同步 accent 50-950 色阶与背景图——主站改品牌皮肤站自动跟随
- 发行 zip 入库仓库(支持 Gitea raw 远程安装)

## 1.1.2(2026-09-07)

- 新增 `GET /dreamport/api/skin?name=`(共享密钥):按角色名返回皮肤材质(302 → `/textures/{hash}`),供 DreamPort 头像渲染服务取原始 64x64 皮肤;未找到角色/未设皮肤返回 404

## 1.1.1(2026-09-07)

- **修复:服务端接口全部 419 "CSRF token mismatch"** —— `/dreamport/api/**` 原挂在 Laravel `web` 中间件组(含 CSRF 校验),服务端 POST 无 CSRF token 被拒;现移出 web 组(接口本身以共享密钥鉴权,无会话依赖)。SSO 网页流程不受影响

## 1.1.0(2026-09-07)

**注册一键开通与账号双向同步**

- 新增服务端接口(`X-Dreamport-Secret`):
  - `POST /dreamport/api/provision`:注册一键开通(账号+初始积分+同名角色;密码采用 DreamPort 注册密码;幂等)
  - `POST /dreamport/api/update-password`:DreamPort 改密同步
  - `POST /dreamport/api/update-player-name`:DreamPort 改游戏名 → 角色改名(无角色则新建,不误改自建角色)
  - `POST /dreamport/api/update-email`:DreamPort 改邮箱同步
- SSO 首登兜底:自动注册账号写入 `user_initial_score` 初始积分(默认 1000)并触发 `UserRegistered`;登录后无角色自动补建同名角色(DreamPort 游戏名优先)
- 配置页新增「纯 DreamPort 登录」开关:隐藏登录页账密表单(游戏内 Yggdrasil 认证不受影响)
- 建角色走 BS 官方校验(`PlayerName` + 长度 + 唯一)与事件(`PlayerWillBeAdded/PlayerWasAdded`),不扣 `score_per_player`

## 1.0.2(2026-09-07)

- 修复 BS v6 登录页不显示「使用 DreamPort 账号登录」按钮:v6 登录页为 div+AJAX 结构、无 `<form>` 标签,注入选择器降级为 `.login-box form` → `.login-card-body` → `.login-box`,且仅 `/auth/login` 路径注入

## 1.0.1(2026-09-07)

- 修复 zip 结构:改为含顶层 `dreamport-oauth/` 目录(BS 上传原样解压到 plugins/,仅扫描一级子目录的 package.json;1.0.0 平铺包解压后插件列表不显示)
- 修复配置页 500:`enchants.config` 改为短类名(BS 会自动拼 namespace,完整类名会双重前缀)
- `require` 放宽为 `blessing-skin-server >= 5.0.0`(兼容 5.x/6.x)

## 1.0.0(2026-09-07)

- 首版:OAuth2 SSO 登录(authorization_code,state 防 CSRF,授权码 5 分钟单次消费)
- 账号按 email 关联;可选自动注册(随机密码)
- 角色数据接口 `GET /dreamport/api/players`(共享密钥)
- 登录页按钮注入(RenderingFooter)

---

升级方式:管理后台上传新版 zip 覆盖,配置与启用状态保留。完整集成指南见仓库 `docs/BLESSINGSKIN.md` 与本 wiki 各篇。
