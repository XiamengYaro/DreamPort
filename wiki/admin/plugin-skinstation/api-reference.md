# 皮肤站插件 · 接口文档

插件对外暴露两类接口:给 DreamPort 后端调用的**服务端接口**(共享密钥)与给玩家的 **SSO 网页流程**。

## 服务端接口(`/dreamport/api/**`)

鉴权:请求头 `X-Dreamport-Secret: <共享密钥>`(常量时间比较)。JSON 请求/响应。统一包装 `{success, data|message}`;403=密钥错误,404=账号不存在,400=参数/规则错误。

### GET /dreamport/api/players?email={email}

拉取某 DreamPort 用户的皮肤站角色(DreamPort 侧缓存 5 分钟)。

```jsonc
// 200 已关联(players 可为空数组)
{ "success": true, "data": { "linked": true, "players": [
  { "pid": 3, "name": "Steve", "model": "classic",
    "skinUrl": "https://skin.example.com/textures/<hash>",
    "capeUrl": null } ] } }
// 404 该邮箱在皮肤站无账号(未关联)
{ "success": false, "message": "not linked" }
```

### POST /dreamport/api/provision

注册一键开通(幂等)。请求体:

```json
{ "email": "a@mail.com", "playerName": "Steve",
  "nickname": "Steve", "password": "明文密码", "ip": "1.2.3.4" }
```

行为:无同 email 账号 → 建号(email/nickname/score=user_initial_score/avatar=0/permission=NORMAL/register_at/last_sign_at/ip/verified=true,`changePassword(password)`,触发 `UserRegistered`);随后确保同名角色(官方 `PlayerName` 校验+长度+重名检查,`new Player(uid,name,tid 0/0)`,触发 `PlayerWillBeAdded/PlayerWasAdded`,不扣积分)。

```jsonc
// 200
{ "success": true, "data": { "userCreated": true, "playerCreated": true,
  "playerName": "Steve", "reason": null } }
// 200 幂等/角色被占等
{ "success": true, "data": { "userCreated": false, "playerCreated": false,
  "playerName": "Steve", "reason": "player name already taken" } }
// 400 参数错误 / 403 密钥错误
```

### POST /dreamport/api/update-password

```json
{ "email": "a@mail.com", "password": "新密码" }
```
200 `{"success":true}`;404 未关联;400 缺密码。

### POST /dreamport/api/update-player-name

```json
{ "email": "a@mail.com", "oldName": "Old", "newName": "New" }
```
- 旧角色存在 → 校验新名(重名排除自身)→ 改名:`200 {"success":true,"data":{"renamed":true}}`
- 旧角色不存在且用户无任何角色 → 按新名新建:`{"data":{"renamed":false,"created":true}}`
- 旧角色不存在但有其他角色 → 404(不误改玩家自建角色)
- 新名违规/被占 → 200+`{"success":false,"message":"player name already taken"}`

### POST /dreamport/api/update-email

```json
{ "oldEmail": "old@mail.com", "newEmail": "new@mail.com" }
```
200 成功改绑;404 皮肤站无旧账号(DreamPort 侧视为无操作);400 新邮箱被占/格式错误。

## SSO 网页流程(插件提供的页面路由)

| 路由 | 说明 |
|---|---|
| `GET /auth/login/dreamport` | 生成 state 存 session → 302 到 DreamPort `/oauth2/authorize?client_id&redirect_uri&state` |
| `GET /auth/login/dreamport/callback` | 校验 state → POST DreamPort `/oauth2/token`(code 换 JWT)→ GET `/oauth2/userinfo` → 按 email 关联/自动注册 → `Auth::login` → `/user` |
| `GET /admin/plugins/config/dreamport-oauth` | 配置页(仅启用后可开) |

错误路径渲染独立错误页(状态校验失败/配置缺失/连接失败/未注册且未开自动注册)。

## 对接的 DreamPort 端点(插件作为客户端调用)

| 端点 | 说明 |
|---|---|
| `POST /oauth2/token` | `grant_type=authorization_code` + client_id/secret/redirect_uri/code → `{access_token(JWT), token_type, expires_in}` |
| `GET /oauth2/userinfo` | `Authorization: Bearer` → `{username, email, nickname, minecraftName, minecraftUuid}` |
| `GET /api/oauth2/authorize-info` / `POST /api/oauth2/authorize` | 由 DreamPort 前端授权确认页调用(浏览器侧) |
