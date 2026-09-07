# 皮肤站插件 · 简介与架构

**dreamport-oauth** 是安装在 [BlessingSkin](https://github.com/bs-community/blessing-skin-server) 上的 PHP 插件(要求 BS ≥ 5.0.0,6.0.2 实测),实现 **DreamPort 账号体系与皮肤站的深度互通**:

## 功能清单

| 功能 | 说明 |
|---|---|
| 🔐 SSO 登录 | 皮肤站登录页「使用 DreamPort 账号登录」,OAuth2 授权码流程(state 防 CSRF) |
| 🧬 注册一键开通 | DreamPort 注册非正版账号时,后端自动在皮肤站建账号(密码=DP 密码)+ 1000 积分 + 同名角色 |
| 🔄 账号双向同步 | DP 改密码/游戏名/邮箱 → 自动同步到皮肤站 |
| 🧑‍🤝‍🧑 角色数据回传 | 皮肤站角色(名称/皮肤/披风)回传 DreamPort,控制台可视化 |
| 🧾 纯 SSO 模式 | 可隐藏皮肤站登录页账密表单,网页只留 DreamPort 登录(游戏内 Yggdrasil 不受影响) |
| 🖼 登录页按钮注入 | 自动在登录页注入按钮(兼容 BS v5/v6 两种 DOM) |

## 账号映射规则

**以 email 为唯一关联键**(MySQL collation 大小写不敏感):

- DreamPort 用户名 = 注册时填的 Minecraft ID = 皮肤站角色名,三方同名
- 皮肤站不存在同 email 账号时,按插件配置「自动注册」决定自动建号或拒绝登录

## 数据流

```
【SSO 登录】
BS 登录页按钮 → DP /oauth2/authorize(SPA 授权确认页)
  → 允许 → 携 code 回 BS /auth/login/dreamport/callback
  → BS 插件 POST DP /oauth2/token(换 JWT) → GET /oauth2/userinfo(email)
  → 按 email 找 BS 账号(无则自动注册:初始积分+随机密码) → Auth::login

【注册一键开通】
玩家在 DP 注册(非正版) → DP 后端 POST BS /dreamport/api/provision
  (email/playerName/password/IP,X-Dreamport-Secret 鉴权)
  → 建账号(score=user_initial_score,verified=true) + 同名角色(官方校验+事件,不扣分)

【角色回传】
DP 控制台/玩家档案 → DP 后端 GET BS /dreamport/api/players?email=
  → 角色名/皮肤 hash/披风 hash → 前端用 BS 公开头像接口渲染

【同步】
DP 改密码/游戏名/邮箱 → DP 后端 POST BS update-password / update-player-name / update-email
```

## 服务端接口鉴权

所有 `/dreamport/api/**` 以请求头 `X-Dreamport-Secret` 校验共享密钥(常量时间比较);密钥在两端管理界面各自配置,保持一致即可。

## 文件结构

```
dreamport-oauth/
├── bootstrap.php          # 路由注册 + 登录页按钮注入(RenderingFooter)
├── package.json           # 插件元数据(name/namespace/enchants.config)
├── src/
│   ├── DreamportOAuthController.php   # SSO + provision/同步接口
│   └── Configuration.php              # 配置页(render)
└── views/
    ├── config.blade.php               # 配置表单
    ├── login-button.blade.php         # 登录页注入脚本(含纯 SSO 隐藏)
    └── error.blade.php                # SSO 失败页
```
