# Paper 主插件 · 进服校验与维护模式

## 校验流程(LoginListener,事件优先级 HIGH)

1. **先记录后校验**:无条件异步上报 `login-record`(玩家名/uuid/ip)。这一步是网页 ID 验证的数据来源——即使该玩家还没通过白名单(login-check 会拒),进服记录也已落库,网页上的 3 分钟验证窗口才有效
2. `login-check`:带 username/uuid/ip 请求后端,同步等待(认证线程执行,不卡主线程;超时 `check.timeout-ms`)
3. 后端返回 `decision`:
   - `allow` → 放行
   - `deny` + `reasonKey` → 踢出,文案取 i18n
   - `maintenance=true` → 非 OP 直接踢出("维护中");**OP 永远放行**
4. 特殊文案:`login.not_registered` 的踢出界面会追加黄色一行注册地址(`web-register-url`);封禁原因不进 reasonKey(走审计),使用通用封禁文案

## fail-policy(后端不可达时)

| 值 | 行为 |
|---|---|
| `cache`(默认) | 有缓存(含过期)用缓存;完全没有则拒绝(error.backend_down) |
| `allow` | 一律放行(开放优先,适合后端短暂维护的服) |
| `deny` | 一律拒绝(安全优先) |

缓存:按**小写用户名**缓存最近一次校验结果,TTL = `check.cache-ttl-seconds`。后端恢复后心跳日志会出现 `[连接] 后端正常`。

## reasonKey 对照(后端状态 → 踢出文案)

| reasonKey | 玩家状态 |
|---|---|
| `login.not_registered` | 未注册(附注册地址) |
| `login.pending` | 已注册,问卷未完成 |
| `login.pending_review` | 问卷通过,待人工审核 |
| `login.invited_pending` | 邀请码注册待邀请人确认 |
| `login.pending_verify` / `verify.recorded` | 审核通过,待网页验证 ID |
| `login.rejected` | 已拒绝 |
| `login.banned` / `login.banned_reason` | 封禁中 |
| `maintenance.kick` | 维护模式 |
| `login.unknown_status` / null | 未知状态(联系管理员) |

## 临时封禁

后端侧封禁带 `banUntil`;**到期由后端定时任务(每小时)自动解封**并通知玩家,插件无需感知——解封后 login-check 自然放行。

## 基岩玩家

基岩 ID 由**后端**存储与加前缀(如 `.Steve`);插件把进服用户名原样上报,后端按前缀识别基岩身份。Floodgate/Geyser 自身按其官方方式接入,插件不参与前缀注入。

## 调试建议

- 改了后端白名单状态,插件侧缓存最长 `cache-ttl-seconds` 秒后生效;急用可重启插件或等缓存过期
- 后端日志 `[校验]` 行有每次决策详情(放行/拒绝+原因),配合插件 `check.timeout-ms` 排查超时
