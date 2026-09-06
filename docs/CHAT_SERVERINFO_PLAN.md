# 网页消息历史 7 天 + 服务器信息落库 方案

> 状态:**已定稿**(v1.1,2026-09-06;§6 决策点已由作者确认)
> 分支:`dev` · 前置:M1–M5(AstrBot 互通)已合入 dev
> 需求(作者提出):①网页 web 端与服内消息互通 ②网页端可查看服务器内历史消息 ③插件采集服务器信息实时保存到数据库 ④网页端可查看近 7 天消息

---

## 1. 现状核对(2026-09-06 实测)

| # | 需求 | 现状 | 差距 |
|---|------|------|------|
| 1 | 网页↔服内互通 | **已具备**(M3/M4 交付):服内聊天/进出服 → 网页 SSE 实时;网页聊天 → 游戏收件箱轮询广播 + 同步进群 | 无剩余 |
| 2 | 网页查看服内历史 | `ChatBox.vue` 进入时一次性拉 `GET /api/chat/history` 并去重渲染;但数据源是 **dp_setting 键 `chat.history` 的 JSON 大字段** | 上限 500 条(几小时就滚没)、无时间维度、无分页;且每次广播**全量 DELETE+INSERT 重写整个 JSON**(写放大);ChatBox 用裸 `fetch` 违反 Rules §7「api.ts 唯一接口层」 |
| 3 | 服务器信息实时落库 | 插件心跳 60s 上报 → `ServerStatsService` **纯内存 Map**;在线人数采样每 5 分钟一条 → **内存 ArrayDeque 仅 24h**;重启全部清零 | 心跳快照、在线历史均未落库 |
| 4 | 近 7 天消息 | 无任何按时间保留的存储 | 需建表 + 定时清理 |

另:玩家排行榜(财富/时长)数据源为插件经济快照(dp_setting `economy.snapshot`,已有落库),不在本次范围。

## 2. 设计

### 2.1 数据库(Flyway `V3__chat_and_serverinfo.sql`)

```sql
-- 聊天消息(服内/网页/QQ/系统 四源统一,7 天保留)
CREATE TABLE dp_chat_message (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  origin      VARCHAR(16)  NOT NULL,            -- game | web | qq | system
  player      VARCHAR(64)  NOT NULL,            -- 显示名(游戏名/网页用户名/QQ 昵称/[系统])
  message     VARCHAR(512) NOT NULL,
  server_id   VARCHAR(64)  NULL,                -- 来源服(game 时有值)
  created_at  BIGINT       NOT NULL,            -- 毫秒 epoch(与全库一致)
  KEY idx_chat_created (created_at),
  KEY idx_chat_origin (origin, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 服务器当前快照(每服一行,心跳 upsert)
CREATE TABLE dp_server (
  server_id     VARCHAR(64) PRIMARY KEY,
  server_name   VARCHAR(64) NULL,
  role          VARCHAR(16)  NULL,
  version       VARCHAR(32)  NULL,
  online_players INT NOT NULL DEFAULT 0,
  max_players    INT NOT NULL DEFAULT 0,
  last_seen_at  BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 在线人数历史(5 分钟粒度采样,7 天保留,曲线数据源)
CREATE TABLE dp_online_history (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  server_id   VARCHAR(64) NOT NULL,
  online      INT NOT NULL,
  max_players INT NOT NULL,
  sampled_at  BIGINT NOT NULL,
  KEY idx_online_time (sampled_at),
  KEY idx_online_server (server_id, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

数据量评估:聊天按日均 2000 条计 7 天 1.4 万行;在线历史 3 服 × 288 点/天 × 7 天 ≈ 6000 行——规模无压力。

### 2.2 写入链路(后端)

- `ChatService.broadcast` 增加扩展签名 `broadcast(origin, player, message, serverId)`,在现有 SSE 推送后改为 **INSERT dp_chat_message**(单条,虚拟线程,替代原 dp_setting JSON 全量重写——同时消除写放大);旧签名保留并委托(origin 按调用方:插件 events→`game`,网页 send→`web`,AstrBot chat→`qq`,join/quit→`system`)。
- `dp_setting` 键 `chat.history` **停写**(旧 500 条历史处置见决策点 2)。
- `ServerStatsService.heartbeat()`:收到心跳即 **upsert dp_server**(当前快照,"实时保存");每服 60s 一条,`last_seen_at` 同步更新。
- 采样任务(已有,5 分钟)同时写 `dp_online_history`;内存 ArrayDeque 保留(热读加速),历史查询以库为准。
- 新增 `@Scheduled(hourly)` 清理:`dp_chat_message`/`dp_online_history` 删除 7 天前数据。

### 2.3 API

| 端点 | 变化 |
|------|------|
| `GET /api/chat/history` | **兼容保留**,数据源换 `dp_chat_message`:默认返回最近 200 条 `{history:[{player, message, timestamp, origin, server_id}]}`(新增字段向后兼容);500 条上限取消 |
| `GET /api/chat/history?before={id}&limit=200` | **新增**向前分页(取 id < before 的前 limit 条);`?origin=` 可过滤(如只看游戏聊天);一律限 7 天窗口 |
| `GET /api/server/player-history` | **兼容保留**默认近 24h;新增 `?days=7`(上限 7)→ `data.list` 按采样点返回 `{time, players}` 结构不变,另加 `data.servers:[{serverId, points}]` 分服曲线 |
| `GET /api/server/status` | 数据源增强:重启后心跳未到时从 `dp_server` 读最后快照(标 `stale: true`),不再返回空白 |

插件侧**无需改动**(60s 心跳即"实时";可选调频见决策点 5)。

### 2.4 前端

- **ChatBox.vue 历史浏览**:进入加载最近 200 条(时间戳显示精确到时分,跨天显示日期);"加载更早"按钮按 `before` 游标向前翻页(7 天窗口,到底提示);发送/接收逻辑不变;**裸 fetch 全部改走 `services/api.ts`**(修复 Rules §7 违规)。
- **Dashboard 服务器状态卡**:新增「近 7 天在线」小曲线(复用 `PlayerChart` 组件,数据 `player-history?days=7`,抽稀渲染)。
- `Status.vue` 为未挂路由的旧"申请状态查询"空壳页——**不启用、不删除**(本次不动,避免范围膨胀)。

## 3. 里程碑(均在 dev 分支)

| 里程碑 | 内容 | 可独立交付 |
|--------|------|-----------|
| M6 聊天历史落库 | V3 建表(dp_chat_message)、broadcast 写表改造 + origin/serverId 贯通、兼容 history API + before 分页、7 天清理任务、ChatBox 历史分页 + api.ts 收口 | ✅(即"近 7 天消息"上线) |
| M7 服务器信息落库 | V3 建表(dp_server/dp_online_history)、心跳 upsert、采样落库、player-history?days=、status stale 兜底、Dashboard 7 天曲线 | ✅ |
| M8 收尾 | 单测补齐(分页/清理/采样)、CHANGELOG、全量构建 | ✅ |

顺序理由:M6 是"近 7 天消息"的主体且自包含;M7 复用同一批基建(清理任务/建表模式);M8 收口。

## 4. 测试方案

- **单测**:history 分页游标语义(before/limit/7 天窗口)、origin 过滤、采样与清理边界。
- **集成**(备用端口 + 测试库):发消息 → 表落库 → history 返回含 origin/server_id → before 分页回溯 → 7 天外不返回;心跳 → dp_server upsert → player-history?days=7 出点;清理任务用注入时钟或直接 SQL 验证。
- **E2E**:网页 + 游戏双端互发消息,历史与实时均可见;重启后端 → 历史消息仍在(对比现状重启清零的改进点)。

## 5. 风险

| 风险 | 应对 |
|------|------|
| 聊天表膨胀 | 7 天清理 + 绝对上限兜底(见决策点 3) |
| 高峰期广播写入频率 | 单条 INSERT + 虚拟线程,量级(条/秒)远低于连接池能力;必要时合并批量写 |
| 旧 dp_setting 历史丢弃 | 最多丢最近几百条旧消息(见决策点 2) |
| `chat.history` 键被外部脚本消费 | 停写后该键不再更新;如有外部消费需改读新 API(CHANGELOG 注明) |

## 6. 决策点(已确认,2026-09-06)

1. **历史浏览位置**:ChatBox 内"加载更早"游标翻页,不建独立页。
2. **旧 `chat.history` 500 条**:直接放弃,不迁移。
3. **聊天表绝对上限**:加 50,000 条硬顶,超出按最旧删除。
4. **join/quit 系统消息**:照常进历史(origin=system),网页默认显示、API 可过滤。
5. **插件心跳频率**:`tasks.*` 配置段接线,心跳间隔可调(默认 60s 不变)。
