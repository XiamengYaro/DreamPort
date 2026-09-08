# DreamPort 部署与运维检查清单

> 供生产部署与日常运维逐项核对。适用版本 v1.2.0+

---

## 一、首次部署清单

### 数据库
- [ ] 创建数据库：`CREATE DATABASE dreamport CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
- [ ] 数据库账号使用强密码，且只授予 dreamport 库权限

### 群组服（Velocity + 多子服）
- [ ] 主服（数据权威）插件设 `role: primary`、子服设 `role: secondary`
- [ ] 确认仅主服日志出现"经济快照已上报"（子服应为"经济快照上报已跳过"）
- [ ] 如需后端硬保险：后端 config.yml 加 `wl.economy.accept-from: <主服serverId>`

### 后端（dreamport-server-1.2.0.jar）
- [ ] 首次启动生成 `config.yml`，完成全部 `[必改]` 项：
  - [ ] `spring.datasource`：MySQL 连接
  - [ ] `wl.security.jwt-secret`：`openssl rand -base64 48` 生成
  - [ ] `wl.internal.server-token`：与插件 `backend.server-token` 一致
- [ ] `wl.cors.allowed-origins` 从 `*` 改为实际站点域名
- [ ] `wl.seed-demo` 保持 `false`
- [ ] `wl.mail.*` 填入 SMTP（不填则验证码仅打印日志）
- [ ] `wl.web-register-url` 填实际站点地址（邮件 logo/链接依赖此值）
- [ ] 启动后完成 `/setup` 初始化向导（管理员账号 + 全新部署/导入旧库）

### 插件（dreamport-plugin / dreamport-plugin-proxy）
- [ ] Paper 子服装 `dreamport-plugin`，`backend.server-token` 与后端一致
- [ ] Velocity 代理装 `dreamport-plugin-proxy`（不可与 Paper 版混装）
- [ ] 群组服启用代理拦截时，子服设 `role: secondary`
- [ ] 游戏内 `/xmw status` 确认后端连通

### 网络与反代
- [ ] 18898 端口对外（或反代）；18899 仅管理侧可达
- [ ] 反代 SSE 不缓冲：`/api/questionnaire/stream` 等 location 配 `proxy_buffering off`
- [ ] 反代 WebSocket Upgrade 支持（18899 与 /api 聊天）

### 上线前安全
- [ ] 管理后台「系统配置」核对管理员名单与通知邮箱
- [ ] 邮件 logo 为 PNG/JPG（Outlook 不支持 webp）
- [ ] 数据库定时备份任务已配置（mysqldump + `static/uploads/` + `docs/` + `email/`）

---

## 二、日常运维

### 生成邀请码上限调整
管理后台 → 外观设置 → 邀请设置 → 每人上限/有效期，保存即生效。

### 问卷管理
- 题库：管理后台 → 问卷管理 → 结构化编辑器（增删改/排序/整卷保存）
- 启用开关与通过线：外观设置 → 问卷设置
- AI 评分：外观设置 → AI 评分设置（OpenAI 兼容接口；低置信度自动转人工复核）

### 数据迁移（旧版 XMWhitelist → DreamPort）
- 全新部署：`/setup` 向导选「导入旧库」上传 `.sql`
- 已运行：管理后台 → 数据迁移标签上传
- 迁移报告写入 `dp_setting`（migration.report），可在后台查看
- 回滚：`legacy_*_backup` 表改回原名 + 换回旧版 JAR

### 版本升级
1. 备份数据库与 `static/uploads/`、`docs/`、`email/` 三个目录
2. 替换 JAR → 重启 → 查看「启动记录」确认版本与数据完整
3. 前端有更新时浏览器强制刷新一次（之后 HTML no-store 自动生效）

### 故障排查速查
| 现象 | 排查 |
|------|------|
| 启动即失败 | 终端 `✗ 启动失败` 提示：MySQL 连接/端口占用/config.yml 语法 |
| 玩家进服提示验证服务不可用 | 后端宕机或网络不通；`/xmw status` 检查；紧急改 `fail-policy: allow` |
| 邮件发不出 | 日志模式说明 SMTP 未配置；发送失败查 SMTP 授权码/端口 |
| 管理后台进不去 | 登录账号须在「系统配置 → 管理员名单」中 |
| 页面样式异常 | 浏览器强刷（Ctrl+F5）；确认 JAR 内 static 为最新构建 |

---

## 三、终端日志速查

| 日志前缀 | 含义 |
|----------|------|
| `[访问]` | 每个 HTTP 请求（方法/路径/状态码/耗时/IP/用户） |
| `[修改]` | 管理操作与内容修改（操作者/动作/目标） |
| `[连接]` | 服务器上线/离线、插件↔后端连接状态变化 |
| `[心跳]` | 每 5 分钟各服在线摘要 |
| `[校验]` | 每次进服白名单校验决策（放行/拒绝+原因） |
| `[验证]` | 玩家 ID 验证成功记录 |
| `[迁移]` | 旧库迁移过程与结果 |
| `[种子]` | 默认内容生成（门户/题库） |
| `[Mail]` / `[Mail:日志模式]` | 邮件发送/打印 |
