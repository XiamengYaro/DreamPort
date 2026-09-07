# Paper 主插件 · 命令与 i18n

## 命令:`/xmw`

权限节点:`dreamport.use`(默认所有人,命令级)、`dreamport.admin`(默认 OP,代码内检查)。Tab 补全:第一参补 11 个子命令;`qq` 后只补 `bind`。

| 子命令 | 权限 | 说明 |
|---|---|---|
| `/xmw`(无参) | dreamport.use | 打印用法 |
| `/xmw status` | dreamport.use | 角色/后端地址/服务器 ID/fail-policy + 后端连通性(`/api/health`) |
| `/xmw version` | dreamport.use | 插件版本 |
| `/xmw reload` | dreamport.admin | 重载 config.yml 与 i18n |
| `/xmw list` | dreamport.admin | 待审核玩家列表(用户名+状态) |
| `/xmw info <玩家>` | dreamport.admin | 玩家详情(邮箱/状态/封禁原因) |
| `/xmw approve <玩家>` | dreamport.admin | 通过审核 |
| `/xmw reject <玩家> <原因>` | dreamport.admin | 拒绝(**必须带原因**) |
| `/xmw ban <玩家> <原因>` | dreamport.admin | 封禁(**必须带原因**;游戏内不支持天数,临时封禁去后台) |
| `/xmw unban <玩家>` | dreamport.admin | 解封 |
| `/xmw delete <玩家>` | dreamport.admin | 删除账号 |
| `/xmw qq bind <验证码>` | 仅玩家(控制台会拒绝) | 完成 QQ 绑定(配合 QQ 群 `/dp 绑定`) |

> 全部命令的后端调用走异步调度(Folia 兼容);`reject/ban` 不带原因会提示用法。

## 管理后台联动

- `approve/reject/ban/unban/delete` 直接调后端 admin-ops,产生审计日志并通知玩家(铃铛/邮件)
- 封禁带天数等高级操作请使用**管理后台**(游戏内命令保持最小能力)
- 管理后台的封禁/审核操作会通过**白名单指令队列**回流到 bukkit 白名单模式的服(插件轮询后以控制台身份执行)

## i18n

- 打包内置 `i18n/messages_zh.properties` 与 `messages_en.properties`(en 缺键回退中文)
- 12 个键:9 个状态踢出文案 + `maintenance.kick` + `verify.recorded` + `error.backend_down`
- `&` 色号自动转 `§`;缺键时显示键名本身(排障信号)

## 已知注意点

- `plugin.yml` 的 usage 只写了 `<status|reload>`,与实际 11 个子命令不同步(以本文档为准)
- 权限 `dreamport.admin` 为代码内检查;权限插件只需正确授发节点
