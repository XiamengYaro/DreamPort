# 部署总览

DreamPort 是「**独立后端 + 多端薄插件**」架构:后端承载全部业务,各端只做薄薄的对接。

## 组件拓扑

```
                        ┌──────────────────────────┐
   玩家浏览器 ──────────→ │  DreamPort 后端 :18898    │ ←── 管理后台(SPA 同端口)
   (官网/控制台)          │  REST API + 静态前端      │
                        │  WebSocket :18899         │
   MC 服务器(Paper) ───→ │  MySQL (3306)             │
   (dreamport-plugin)    │  邮件 SMTP(可选)          │
                        └────────────┬─────────────┘
   Velocity 代理 ──────────────────→ │                ↑ SSE / REST
   (dreamport-plugin-proxy)          │                │
                        ┌────────────┴─────────────┐  │
   AstrBot(QQ 机器人)─→ │  外部服务                  │  │
   (astrbot-plugin)      │  BlessingSkin 皮肤站 ─────┘──┘(dreamport-oauth 插件)
```

所有组件都与后端通信,彼此之间**不直接通信**。

## 端口一览

| 端口 | 组件 | 说明 |
|---|---|---|
| 18898 | DreamPort 后端 | REST API + 官网前端(静态托管)+ 管理后台 |
| 18899 | DreamPort 后端 | WebSocket(实时推送) |
| 3306 | MySQL | 数据存储 |
| 6199 | AstrBot aiocqhttp | OneBot v11 反向 WS(QQ 协议端接入) |
| 皮肤站 80/443 | BlessingSkin | 独立部署,与 DreamPort 同主机或异机均可 |

## 四套令牌(别搞混)

| 令牌 | 谁配置 | 谁使用 | 请求头 |
|---|---|---|---|
| **服务器令牌** | 后端 `wl.internal.server-token`(config.yml) | Paper 主插件 `backend.server-token`、Velocity `backend.server-token` | `X-Server-Id` + `X-Server-Token` |
| **AstrBot API 令牌** | 后端管理后台 → 系统设置 → QQ 互通 | AstrBot 插件 `api_token` | `X-API-Token` |
| **皮肤站共享密钥** | 后端管理后台 → 系统设置 → BlessingSkin 互通(`apiSecret`) | 皮肤站插件配置「角色数据接口密钥」 | `X-Dreamport-Secret` |
| **OAuth2 client 凭据** | 后端管理后台 → BlessingSkin 互通(clientId/Secret) | 皮肤站插件配置(Client ID/Secret) | 无(参数传输) |

> 插件与后端配对失败的绝大多数问题,都是令牌不一致。

## 推荐部署顺序

1. **后端**:装 Java 21 + MySQL → 启动 JAR → 编辑生成的 `config.yml` → 重启 → `/setup` 初始化 → [详见](backend/installation.md)
2. **Paper 插件**:MC 服务器放插件 → 改 `config.yml`(backend.url/token/server-id/role)→ 重启 → [详见](plugin-paper/installation.md)
3. **Velocity 插件**(仅群组服):代理端放插件 → 改 `config.properties` → [详见](plugin-velocity/introduction.md)
4. **AstrBot 插件**(可选):部署 AstrBot → 装插件 → 后台开「QQ 互通」→ [详见](plugin-astrbot/introduction.md)
5. **皮肤站插件**(可选,非正版服需要):部署 BlessingSkin → 装插件 → 后台开「BlessingSkin 互通」→ [详见](plugin-skinstation/introduction.md)

## 环境要求

| 组件 | 要求 |
|---|---|
| 后端 | Java 21(Temurin 推荐)、MySQL 8.x、1G+ 内存 |
| Paper 插件 | Paper 1.20+(API 1.20),支持 Folia |
| Velocity 插件 | Velocity 3.3+ |
| AstrBot 插件 | AstrBot(新版),QQ 协议端经 OneBot v11 接入 |
| 皮肤站插件 | BlessingSkin ≥ 5.0.0(6.0.2 实测),PHP ≥ 8.1 |

## 构建产物

仓库根目录执行:

```bash
mvn clean package          # 后端 JAR + Paper 插件 JAR
cd frontend && npm ci && npm run build   # 前端(build.sh 会自动同步进 JAR)
./scripts/build.sh         # 一键全量:前端 → 后端 JAR → 皮肤站插件 zip
```

产物:

| 文件 | 用途 |
|---|---|
| `dreamport-server/target/dreamport-server-*.jar` | 后端(内嵌官网前端) |
| `dreamport-plugin/target/dreamport-plugin-*.jar` | Paper 插件 |
| `dreamport-plugin-proxy/target/dreamport-plugin-proxy-*.jar` | Velocity 插件 |
| `bs-plugin-dreamport/dreamport-oauth-*.zip` | 皮肤站插件 |
| `astrbot-plugin/` | AstrBot 插件(目录直接安装) |
