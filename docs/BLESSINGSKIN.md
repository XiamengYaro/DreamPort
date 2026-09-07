# DreamPort × BlessingSkin 互通指南

> DreamPort 作 **OAuth2 Provider**（身份权威），BlessingSkin 皮肤站通过本仓库交付的
> `dreamport-oauth` 插件接入：玩家用 DreamPort 账号授权登录皮肤站，DreamPort 反向获取
> 玩家在皮肤站的角色（皮肤/披风）。当前版本 v1.0.x（插件）。

---

## 1. 整体数据流

```
玩家浏览器                 DreamPort 后端(:18898)              BlessingSkin(插件 dreamport-oauth)
   │                            │                                    │
   │  ① 点「使用 DreamPort 登录」 │                                    │
   │ ────────────────────────────────────────────────────────────────→ │  /auth/login/dreamport
   │  ② 302 → /oauth2/authorize（授权确认页,需已登录 DreamPort）        │
   │ ───────────────────────────────→ │                                │
   │  ③ 点「允许授权」→ 一次性 code   │                                │
   │ ←─────────────────────────────── │                                │
   │  ④ 302 → /auth/login/dreamport/callback?code&state               │
   │ ────────────────────────────────────────────────────────────────→ │
   │                            │  ⑤ POST /oauth2/token(code换JWT)     │
   │                            │ ←────────────────────────────────── │
   │                            │  ⑥ GET /oauth2/userinfo(Bearer)      │
   │                            │ ←────────────────────────────────── │
   │                            │        ⑦ 按 email 关联/注册 → 登录    │
   │                            │                                      │
   │  ⑧ Dashboard「皮肤站角色」卡 │  GET /api/user/bs/players            │
   │ ────────────────────────────────→ │ GET {bs}/dreamport/api/players │
   │                            │ ──────────────────────────────────→ │ (X-Dreamport-Secret)
```

**账号关联规则**：以 **email** 为唯一映射 —— DreamPort 账号与皮肤站账号同邮箱即视为同一人。
皮肤站不存在同邮箱账号时，按插件配置页的「自动注册」开关决定自动建号（verified=true、随机密码）还是拒绝登录。

**角色数据**：皮肤站登录成功即建立映射；DreamPort 控制台按需调皮肤站插件接口拉取（结果缓存 5 分钟）。
皮肤/披风渲染直接用皮肤站公开接口，无需认证：
- 3D 大头照：`{皮肤站}/avatar/player/{角色名}?3d=true&png=true&size=96`
- 材质文件：`{皮肤站}/textures/{hash}`

---

## 2. DreamPort 端配置（管理后台）

位置：**管理后台 → 系统设置 → BlessingSkin 互通**

| 配置项 | 说明 |
|---|---|
| 启用 | 总开关；关闭时授权接口返回错误、控制台角色卡隐藏 |
| 皮肤站地址 | 皮肤站公网地址，末尾不带 `/`（与皮肤站 `APP_URL` 同源，如 `http://skin.xmcraft.cn`） |
| Client ID / Client Secret | **自定义值**，与皮肤站插件配置保持一致即可（无需 Passport） |
| API 共享密钥 | 服务端间接口密钥，与皮肤站插件配置保持一致；可用「生成」按钮 |

保存后即可。相关端点（供排查）：

| 端点 | 认证 | 用途 |
|---|---|---|
| `GET /api/oauth2/authorize-info` | JWT | 授权页校验 client/redirect |
| `POST /api/oauth2/authorize` | JWT | 签发一次性授权码（5 分钟 TTL） |
| `POST /oauth2/token` | client_id+secret | code 换 access_token（即站内 JWT） |
| `GET /oauth2/userinfo` | Bearer | 返回 username/email/nickname/minecraftName |
| `GET /api/user/bs/players` | JWT | 当前用户在皮肤站的角色（缓存 5 分钟） |

---

## 3. BlessingSkin 端配置

### 3.1 配置插件（无需 Passport、无需 PHP 命令）

> 本方案中 client_id / client_secret **不是从任何地方"获取"的**，而是两端约定的自定义字符串：
> DreamPort 后台填一份、皮肤站插件配置里填一份，保持一致即可。BS 内置的 Passport 在此流程中不参与，
> 「OAuth Client Core」插件也与本互通无关（那是皮肤站用微软/LittleSkin 账号登录用的）。

皮肤站管理后台 → Plugins → DreamPort 登录互通 → 配置：

| 配置项 | 填什么 |
|---|---|
| DreamPort 站点地址 | DreamPort 的公网地址（如 `https://xmcraft.cn`，末尾不带 `/`） |
| Client ID | 自定义标识（如 `dreamport-oauth`），与 DreamPort 后台一致 |
| Client Secret | 自定义密钥（40 位随机串），与 DreamPort 后台一致 |
| 自动注册 | 建议开启：皮肤站无同邮箱账号时自动建号 |
| 角色数据接口密钥 | 与 DreamPort 后台「API 共享密钥」完全一致 |

保存后，皮肤站登录页底部会出现「使用 DreamPort 账号登录」按钮（未出现 = 插件未启用）。

### 3.2 安装 dreamport-oauth 插件

1. 从构建产物中取 `bs-plugin-dreamport/dreamport-oauth-*.zip`（`scripts/build.sh` 自动打包；zip 内含一层 `dreamport-oauth/` 插件目录）
2. 皮肤站管理后台 → 插件管理 → 上传并安装 → 启用
3. 点插件「配置」页，填写：
   - **DreamPort 站点地址**：如 `https://dreamport.example.com`
   - **Client ID / Client Secret**：§3.1 获得
   - **自动注册**：按需开启（皮肤站无同邮箱账号时自动建号）
   - **角色数据接口密钥**：与 DreamPort 后台「API 共享密钥」一致

### 3.3 验证

1. 登出皮肤站，登录页应出现「使用 DreamPort 账号登录」按钮（或直接访问 `/auth/login/dreamport`）
2. 跳转 DreamPort 授权页 → 允许 → 回到皮肤站完成登录
3. 回到 DreamPort 控制台，「皮肤站角色」卡应显示角色与皮肤/披风链接

> 注意两端地址的**协议与域名必须完全一致**：DreamPort 后台的「皮肤站地址」必须与皮肤站 `APP_URL`
> 同源（本站为 `http://skin.xmcraft.cn`），插件配置的「DreamPort 站点地址」同理，否则 redirect_uri 校验会拒绝。

---

## 4. 故障排查

| 现象 | 可能原因 |
|---|---|
| 上传插件提示成功但插件列表不显示 | zip 是平铺结构（旧版 1.0.0 打包缺陷）：解压后 package.json 散落在 plugins/ 根目录而非插件子目录。清理 plugins/ 下误散落的 bootstrap.php、package.json、src/、views/ 后，改用 1.0.1+ 的 zip（内含 dreamport-oauth/ 目录）重新上传 |
| 配置页打不开/500 | enchants.config 写了完整类名（BS 会自动拼 namespace），1.0.1 已修正 |
| 授权页提示「redirect_uri 不受支持」 | 皮肤站 `url()` 生成的回调与 Passport 注册的不一致（检查 APP_URL/反代头） |
| 提示「client_id 不匹配」 | 两端 Client ID 不一致 |
| 提示「授权状态校验失败」 | state 过期或会话丢失（cookie 未下发,检查跨站场景） |
| 皮肤站报「无法连接 DreamPort」 | DreamPort 地址不通/证书问题 |
| 控制台显示「尚未关联皮肤站账号」 | 该邮箱在皮肤站无账号且未开自动注册；先去皮肤站登录一次 |
| 控制台显示「皮肤站接口暂不可用」 | 插件未装/未启用、密钥不一致、皮肤站宕机（5 分钟缓存后自动重试） |

## 5. 安全说明

- 授权码一次性、5 分钟过期；state 防 CSRF；secrets 比较为常量时间
- access_token 即 DreamPort JWT（等价于该用户的一次登录会话），仅下发给用户本人授权过的皮肤站
- 角色数据接口以共享密钥保护；email 仅在密钥校验通过后返回
- 插件 PHP 源码在本仓库 `bs-plugin-dreamport/`，与皮肤站版本要求 `^5.0.0`
