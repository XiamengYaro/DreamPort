# DreamPort 皮肤站插件(dreamport-oauth)

BlessingSkin 皮肤站插件:玩家使用 [DreamPort](https://github.com/XiamengYaro/DreamPort) 账号授权登录皮肤站(SSO),注册一键开通账号与角色,账号信息双向同步,皮肤站 UI 自动跟随主站品牌。

> 本插件安装在你的 **BlessingSkin** 皮肤站上(≥5.0.0,6.0.2 实测);与 DreamPort 后端通过共享密钥通信。

## 功能一览

| 功能 | 说明 |
|---|---|
| 🔐 SSO 登录 | 皮肤站登录页「使用 DreamPort 账号登录」,OAuth2 授权码流程(state 防 CSRF) |
| 🚀 注册一键开通 | 玩家在 DreamPort 注册(非正版)即自动开通皮肤站账号:同款密码 + 初始积分 + 同名角色 |
| 🔄 账号双向同步 | DreamPort 改密码/游戏名/邮箱 → 自动同步到皮肤站 |
| 🎨 UI 同步主站 | 皮肤站整体风格(深色玻璃 + 品牌主色 + 背景图)自动跟随 DreamPort 品牌配置 |
| 🧊 纯 SSO 模式 | 可隐藏登录页账密表单,网页只能 DreamPort 授权登录(游戏内认证不受影响) |
| 🖼 角色数据接口 | DreamPort 头像渲染服务按名字拉取皮肤材质 |
| 🧪 连接测试 | 配置页一键测试与 DreamPort 后端的连通性 |

## 安装

1. 下载 `dreamport-oauth-*.zip`(本目录,或 Release 附件)
2. 皮肤站管理后台 → Plugins → **Upload Archive** 上传(远程安装可用 Gitea raw 地址)
3. 启用「DreamPort 登录互通」→ 点「配置」填写参数(见下)

> zip 内含一层 `dreamport-oauth/` 目录——BS 只扫描 `plugins/` 一级子目录的 package.json,平铺包不会被识别(1.0.0 的坑,已修复)。

## 配置项

| 配置项 | 说明 |
|---|---|
| DreamPort 站点地址 | 主站公网地址,末尾不带 `/` |
| Client ID / Client Secret | **两端约定的自定义值**(与 DreamPort 后台一致即可,无需 Passport) |
| API 共享密钥 | 服务端间接口鉴权,与 DreamPort 后台一致 |
| 自动注册 | SSO 登录时皮肤站无同邮箱账号则自动建号(建议开启) |
| 纯 DreamPort 登录 | 隐藏登录页账密表单(游戏内 Yggdrasil 认证不受影响) |
| UI 同步主站 | 皮肤站整体风格跟随主站品牌色与背景(默认开) |

## 提供的接口(DreamPort 后端调用,`X-Dreamport-Secret` 鉴权)

| 端点 | 用途 |
|---|---|
| `GET /dreamport/api/players?email=` | 拉取某用户的皮肤站角色 |
| `GET /dreamport/api/skin?name=` | 按角色名返回皮肤材质(302 → textures) |
| `POST /dreamport/api/provision` | 注册一键开通(账号+角色,幂等) |
| `POST /dreamport/api/update-password` | 改密同步 |
| `POST /dreamport/api/update-player-name` | 角色改名同步 |
| `POST /dreamport/api/update-email` | 邮箱改绑同步 |

完整契约与两端对接步骤:仓库 `wiki/admin/plugin-skinstation/` 与 `docs/BLESSINGSKIN.md`。

## 更新日志

### 1.2.1
- 修复:UI 同步主站不生效 —— 1.2.0 打包时 bootstrap.php 的主题注入块因编辑脚本替换锚点未命中而静默丢失(zip 内无 RenderingHeader 注册);构建脚本新增关键代码断言防复发

### 1.2.0
- 配置页完全重设计:玻璃风界面 + 对接指南双栏卡 + 连接测试徽章;弃用 Option::form 默认渲染,保存走 `POST /dreamport/config/save`(role:admin)
- **UI 同步主站**(dp_theme_sync,默认开):全页注入深色玻璃主题(卡片/导航/表单/滚动条),JS 拉取主站 `/api/config` 同步品牌主色 50-950 色阶与背景图——主站换品牌皮肤站自动跟随
- 发行 zip 入库仓库,支持从 Gitea raw 远程安装

### 1.1.2
- 新增 `GET /dreamport/api/skin?name=`:按角色名返回皮肤材质(302 → textures),供 DreamPort 头像渲染服务使用

### 1.1.1
- 修复服务端 POST 接口被 CSRF 中间件 419 拒绝(`/dreamport/api/**` 移出 web 组)

### 1.1.0
- 注册一键开通(provision)+ 改密/改名/改邮箱同步接口;SSO 首登兜底补积分与角色;纯 SSO 开关

### 1.0.2 / 1.0.1 / 1.0.0
- BS6 登录页按钮兼容 / zip 结构与配置类名修复 / 首版 SSO
