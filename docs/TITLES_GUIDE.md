# 称号系统指南（Titles）

> 对应 v1.4.0。称号从定义（dp_setting JSON）→ 拥有/佩戴记录（dp_user_titles / dp_title_equipped）→ 展示（网页 + 游戏内 PAPI/GUI）全链路。
> 成就引擎（dp_achievement_progress）按指标达标自动授予称号并站内通知。

---

## 1. 数据模型

| 表/键 | 说明 |
|---|---|
| `dp_setting.titles.config` | 称号定义 JSON（code/name/desc/color/**gameColor**/enabled） |
| `dp_setting.achievements.config` | 成就定义 JSON（id/name/metric/target/reward/enabled） |
| `dp_user_titles` | 拥有记录（username + title_code + obtained_at，唯一键防重） |
| `dp_title_equipped` | 当前佩戴（每用户一条，upsert） |
| `dp_achievement_progress` | 成就进度（progress/completed/completed_at） |

成就指标（metric）：`playtime_total`(秒) / `register_days`(注册天数) / `invite_count`(成功邀请数) / `points_total`(累计获得积分)。
服务端每 30 分钟全量评估，新达标自动授予称号并发站内铃铛通知。

## 2. 玩家玩法

- **游戏内**：`/titles` 打开佩戴 GUI（点击佩戴/脱下）；称号经 PlaceholderAPI 变量显示在聊天/Tab（由外部聊天插件消费）：
  - `%dreamport_title%` → 着色 `[称号] ` 前缀
  - `%dreamport_title_raw%` → 仅着色称号名
  - `%dreamport_title_code%` → 称号代码
- **网页**：右上角用户菜单 →「我的称号」直达自己的资料页「我的称号与成就」面板——已拥有点击佩戴/脱下、未解锁灰显、成就进度条；佩戴变更游戏内 ≤60 秒同步（插件周期刷新）。

## 3. 管理员配置（后台「称号与成就」Tab）

- **称号定义**：code / 名称 / 颜色(网页) / **游戏内颜色(留空跟随网页色)** / 启用 / 描述；可增删
- **成就定义**：指标 / 门槛 / 奖励称号(下拉选现有称号 code) / 启用
- **手动授予/撤销**：按玩家用户名 + 称号 code

约束：code/name 非空、颜色须 `#rrggbb`（畸形保存返回 400 并提示）；成就 reward 必须引用存在的称号 code；撤销会校验玩家存在与已拥有。

## 4. 测试方法（验证称号/变量/颜色是否生效）

### 4.1 后端 API（最快，无需进游戏）
```bash
# 服务与配置读取是否生效（公开端点，data.title 应为佩戴称号）
curl http://<host>:18898/api/players/profile/<玩家名>

# 插件接口链路（模拟插件，需 server-token，见生产 config.yml wl.internal.server-token）
curl -H "X-Server-Token: <token>" \
  "http://<host>:18898/internal/v1/title/active?username=<玩家名>"
# 预期:{"success":true,"code":"dragon_killer","name":"弑龙者","color":"#AA00AA"}

# 玩家视角（登录 JWT）
curl -H "Authorization: Bearer <JWT>" http://<host>:18898/api/titles/mine
```

### 4.2 游戏内（验证真实显示）
```
/papi parse me %dreamport_title%      # 期望输出带颜色的 [称号] 前缀
/papi parse me %dreamport_title_code%
/papi parse me %dreamport_title_raw%
/titles                               # 佩戴 GUI
/xmw status                           # 插件-后端连接状态
```
排障：
- `papi parse` **原样返回变量** → 插件旧版 / 服务器未装 PlaceholderAPI（softdepend）
- **返回空串** → 后端未返回佩戴：server-token 不一致、后端未部署、config.yml 与插件 backend 配置不匹配
- `profile` 无 `title` 字段 → 后端未部署最新版（配置读取修复）
- 有变量但无颜色 → 聊天插件剥颜色码 / 未渲染 PAPI 颜色（客户端需 MC 1.16+）

## 5. playertitle 旧数据迁移记录（2026-09-09）

旧称号插件 `title` 库迁移至 DreamPort：

| 旧称号(&色码) | 新 code | 颜色(hex) | 描述 |
|---|---|---|---|
| `&6屠龙者` (id 2) | `dragon_slayer` | `#FFAA00` | 终末之战的胜利者 |
| `&5弑龙者` (id 3) | `dragon_killer` | `#AA00AA` | 精英巨龙的征服者 |
| `&c管理组` (id 4) | `admin_team` | `#FF5555` | XMCraft管理组最高权限账号 |

已执行：写入 `titles.config`（仅 3 个新称号，**不含默认 7 个**，默认称号由用户在后台自行维护）→ `dp_user_titles` 导入 11 条 → `dp_title_equipped` 导入 6 条（原 `is_use=1`）。旧插件 `title_coin/reward_log/buff/particle` 无对应概念不迁移。

> 注意：迁移后默认 7 个称号不再配置，原有 `loyal/recruiter` 记录保留但定义消失后不再展示；默认成就的 reward 引用失效，达标不再自动发称号，需用户自行配置新称号/成就体系。
