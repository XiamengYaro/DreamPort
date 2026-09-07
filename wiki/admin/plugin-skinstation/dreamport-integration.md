# 皮肤站插件 · 对接 DreamPort

## DreamPort 端配置(管理后台 → 系统设置 → BlessingSkin 互通)

| 配置项 | 说明 |
|---|---|
| 启用 | 总开关;关闭时 OAuth2 授权接口报错、注册不开通、控制台角色卡隐藏 |
| 皮肤站地址 | 与皮肤站 `APP_URL` **同源**(协议+域名一致),末尾不带 `/` |
| Client ID / Client Secret | **自定义值**,与插件配置一致即可(无需 Passport/artisan) |
| API 共享密钥 | 与插件「角色数据接口密钥」一致;提供「生成」按钮 |

两端地址同源是硬要求:SSO 的 `redirect_uri` 由皮肤站按自身 `APP_URL` 生成,后端按「皮肤站地址 + `/auth/login/dreamport/callback`」做全等校验,协议(http/https)或域名不同会被拒绝。

## Passport 与「OAuth Client Core」都不需要

- 本方案的 OAuth2 Provider 是 **DreamPort**;BS 内置的 Laravel Passport 在此流程中不参与
- BS 官方的「OAuth 客户端核心」插件是让 BS 登录微软/LittleSkin 用的,与本互通无关
- Client ID/Secret 是**两端约定的自定义字符串**,不是从哪里"获取"的

## 联调步骤

1. 两端配置填齐(DreamPort 5 项 + 插件 5 项,密钥两两一致)
2. 登录页按钮出现 → 完整 SSO 走通
3. DreamPort 注册页选「非正版」注册新号 → 皮肤站用户中心出现账号(1000 积分+同名角色)
4. 用同款账密配启动器进服 → DreamPort 验证 ID
5. DreamPort 控制台「皮肤站角色」卡展示角色与皮肤/披风链接
6. 改一次 DreamPort 密码 → 皮肤站侧同步(重新登录启动器用新密码)

## 皮肤站角色卡(玩家侧呈现)

- 已关联:角色列表(3D 头像 `BS/avatar/player/{name}?3d=true&png=true`、皮肤/披风材质链接)
- 未关联:显示「一键开通」(输入 DP 密码,幂等)
- BS 接口故障:显示"皮肤站接口暂不可用"(DP 侧 5 分钟缓存,恢复后自动重试)

## 契约要点(排障用)

| 契约 | 值 |
|---|---|
| 授权码 | 5 分钟 TTL,单次消费,重放返回 `invalid_grant` |
| access_token | 即 DreamPort 用户 JWT(默认 7 天) |
| userinfo 返回 | username / email / nickname(优先游戏 ID) / minecraftName / minecraftUuid |
| players 接口 | 200=已关联(players 可为空);404=该邮箱无 BS 账号 |
| provision | 幂等:同 email 已存在则不重建账号,但会补同名角色;角色名被占返回 reason |
| update-email | 404(皮肤站无此账号)对 DP 是无操作成功;新邮箱被占返回失败 |
| 同步失败语义 | 互通未启用=无操作成功;传输失败=ok=false(改邮箱会阻断 DP 侧修改) |
