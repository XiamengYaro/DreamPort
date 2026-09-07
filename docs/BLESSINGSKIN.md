# DreamPort × BlessingSkin 互通指南

> DreamPort 作 **OAuth2 Provider**（身份权威），BlessingSkin 皮肤站通过本仓库交付的
> `dreamport-oauth` 插件接入：非正版玩家注册 DreamPort 时一键开通皮肤站账号与同名角色，
> 账号密码与 DreamPort 保持同步；玩家用 DreamPort 账号授权登录皮肤站网页（纯 SSO），
> DreamPort 反向获取玩家在皮肤站的角色（皮肤/披风）。插件当前版本 v1.1.1。

---

## 1. 身份链路与数据流

| 身份 | 载体 | 用途 |
|---|---|---|
| 游戏身份 | 皮肤站**角色**（角色名 = 游戏 ID） | 启动器以皮肤站账密 + authlib-injector 进服 |
| 门户身份 | DreamPort 账号 | 注册/问卷/审核/白名单/社区/经济 |

两者以 **email 关联**；DreamPort 用户名 = 注册时填的 Minecraft ID = 皮肤站角色名，三方同名。

```
玩家注册 DreamPort(选「非正版」)
  └→ DP 后端 provision(共享密钥) → BS 建 账号(密码=DP密码) + 1000积分 + 同名角色
玩家配启动器(同一套账密) → 进服 → DP 验证 MC ID → 闭环
玩家浏览器 SSO 登录 BS 网页 → /auth/login/dreamport → DP /oauth2/authorize 授权页
  → code 换 token → userinfo(email) → 关联/登录
DP 控制台「皮肤站角色」卡 ← GET /api/user/bs/players → BS 插件接口(共享密钥,5分钟缓存)
```

## 2. 注册联动与账号同步

**注册三类型**（DreamPort 注册页「玩家类型」）：

| 类型 | 流程 | 皮肤站 |
|---|---|---|
| 正版 Java | 现状流程（填正版 ID → 验证） | 不开通 |
| 非正版 Java（皮肤站） | 填游戏 ID → 注册即开通皮肤站（账号+1000 积分+同名角色+同款密码） | 一键开通 |
| 纯基岩版 | 填基岩 ID（自动加前缀） | 不开通 |

Java 类玩家均可勾选「同时注册基岩版」。

**同步规则**（DreamPort 修改后自动同步到皮肤站，均以 email 定位、共享密钥保护）：

| DreamPort 操作 | 皮肤站动作 | 失败处理 |
|---|---|---|
| 修改密码 | 同步改 BS 密码 | 不阻断本站，提示可稍后重试 |
| 修改 Minecraft ID | BS 角色改名（无角色则新建；新名被占则提示） | 不阻断本站，提示可稍后重试 |
| 修改邮箱 | 先改 BS 邮箱，**成功才改本站**（email 是关联键） | 阻断并提示原因 |

**存量账号**：互通上线前的 DreamPort 用户没有皮肤站账号 → 控制台「皮肤站角色」卡点
「一键开通」（输入当前 DreamPort 密码校验）；或去皮肤站点一次「使用 DreamPort 账号登录」
（自动注册的账号密码随机，改一次 DreamPort 密码即同步）。

**网页纯 SSO 模式**：插件配置开启「隐藏登录页账密表单」后，皮肤站网页只能用 DreamPort
授权登录；游戏内 Yggdrasil 认证不受影响（仍用账密）。

## 3. DreamPort 端配置（管理后台）

位置：**管理后台 → 系统设置 → BlessingSkin 互通**

| 配置项 | 说明 |
|---|---|
| 启用 | 总开关；关闭时授权接口返回错误、注册不开通、控制台角色卡隐藏 |
| 皮肤站地址 | 皮肤站公网地址，末尾不带 `/`（与皮肤站 `APP_URL` 同源，如 `http://skin.xmcraft.cn`） |
| Client ID / Client Secret | **自定义值**，与皮肤站插件配置保持一致即可（无需 Passport） |
| API 共享密钥 | 服务端间接口密钥，与皮肤站插件配置保持一致；可用「生成」按钮 |

相关端点（供排查）：

| 端点 | 认证 | 用途 |
|---|---|---|
| `GET /api/oauth2/authorize-info` | JWT | 授权页校验 client/redirect |
| `POST /api/oauth2/authorize` | JWT | 签发一次性授权码（5 分钟 TTL） |
| `POST /oauth2/token` | client_id+secret | code 换 access_token（即站内 JWT） |
| `GET /oauth2/userinfo` | Bearer | 返回 username/email/nickname/minecraftName |
| `GET /api/user/bs/players` | JWT | 当前用户在皮肤站的角色（缓存 5 分钟） |
| `POST /api/user/bs/provision` | JWT+密码校验 | 一键开通/重试（幂等） |

## 4. BlessingSkin 端配置

### 4.1 安装 dreamport-oauth 插件

1. 从构建产物中取 `bs-plugin-dreamport/dreamport-oauth-1.1.1.zip`（`scripts/build.sh` 自动打包；zip 内含一层 `dreamport-oauth/` 插件目录）
2. 皮肤站管理后台 → Plugins → Upload Archive 上传 → 启用
3. 点插件「配置」页，填写：
   - **DreamPort 站点地址**：如 `https://xmcraft.cn`
   - **Client ID / Client Secret**：与 DreamPort 后台一致（自定义值）
   - **自动注册**：建议开启（存量用户 SSO 首登兜底）
   - **纯 DreamPort 登录**：隐藏登录页账密表单（按需）
   - **角色数据接口密钥**：与 DreamPort 后台「API 共享密钥」一致

保存后，皮肤站登录页会出现「使用 DreamPort 账号登录」按钮（未出现 = 插件未启用或版本过旧）。

### 4.2 站点选项

- **Score Options → user_initial_score**：新账号初始积分（默认 1000，provision 与 SSO 自动注册均读取此项）
- 建议确认 **player_name_rule = official**（默认），游戏 ID 仅允许字母数字下划线

### 4.3 验证

1. DreamPort 注册页选「非正版」注册新号 → 皮肤站用户中心应出现该账号（1000 积分+同名角色）
2. 用该账密配置启动器（authlib-injector 指向皮肤站）→ 进服
3. DreamPort 验证 MC ID → 修改 DreamPort 密码 → 皮肤站同步改密
4. 登出皮肤站 → 登录页点「使用 DreamPort 账号登录」→ 授权 → 回到皮肤站完成登录
5. DreamPort 控制台「皮肤站角色」卡显示角色与皮肤/披风链接

## 5. 故障排查

| 现象 | 可能原因 |
|---|---|
| 上传插件提示成功但插件列表不显示 | zip 是平铺结构（旧版 1.0.0 打包缺陷）。清理 plugins/ 下误散落的文件后，用 1.1.1+ 的 zip 重新上传 |
| 配置页打不开/500 | enchants.config 写了完整类名（1.0.1 已修正）；或插件未启用 |
| 登录页看不到「使用 DreamPort 账号登录」 | 插件未启用（<1.0.2 在 BS6 无 `<form>` 登录页不渲染）；或页面被浏览器缓存 |
| 授权页提示「redirect_uri 不受支持」 | 两端地址协议/域名不一致（DreamPort 后台的皮肤站地址必须与皮肤站 `APP_URL` 同源） |
| 提示「client_id 不匹配」 | 两端 Client ID 不一致 |
| 提示「授权状态校验失败」 | state 过期或会话丢失（cookie 未下发） |
| 注册时皮肤站未开通 | 互通未启用/地址或密钥未配置；或游戏名含非法字符；控制台可「一键开通」重试 |
| 角色名被占用 | 皮肤站已存在同名角色；换个游戏 ID 或联系管理员处理 |
| 控制台显示「皮肤站接口暂不可用」 | 插件未装/未启用、密钥不一致、皮肤站宕机（5 分钟缓存后自动重试） |

## 6. 安全说明

- 授权码一次性、5 分钟过期；state 防 CSRF；secrets 比较为常量时间
- access_token 即 DreamPort JWT（等价于该用户的一次登录会话），仅下发给用户本人授权过的皮肤站
- 所有服务端接口以共享密钥保护；provision 密码仅走 DreamPort→皮肤站服务端信道（与皮肤站自身注册收明文密码同级），插件侧立即哈希落库
- 插件 PHP 源码在本仓库 `bs-plugin-dreamport/`，要求皮肤站 `>=5.0.0`
