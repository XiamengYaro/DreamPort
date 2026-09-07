# Paper 主插件 · 经济快照

插件定期汇总玩家经济与时长数据上报后端,驱动官网玩家档案(余额/在线时长/最近登录)与排行榜。

## 开关与频率

```yaml
economy:
  report: auto      # auto | on | off
tasks:
  economy-interval: 300   # 秒,下限 30
```

| 值 | 行为 |
|---|---|
| `auto`(推荐) | 仅 `role: primary` 上报——**群组服避免多子服快照互相覆盖** |
| `on` | 无论角色都上报 |
| `off` | 不上报 |

被跳过时日志输出:`经济快照上报已跳过(economy.report=…, role=…)——由群组主服负责推送`。

## 数据来源(优先级)

1. **Vault**:反射读取 `net.milkbowl.vault.economy.Economy`(经 ServicesManager,不强制依赖);遍历 `Bukkit.getOfflinePlayers()` 取余额
2. **EssentialsX 回退**:无 Vault 时读 `plugins/Essentials/userdata/<uuid>.json` 的 `money` 与 `onlinetime`

两条路径都会尽量带上 Essentials 的在线时长。

## 上报内容

```json
{ "players": [
  { "name": "Steve", "balance": 12345.6,
    "playtimeSeconds": 86400, "playtimeDays": 1.0,
    "lastLogin": 1725600000000 }
] }
```

→ `POST /internal/v1/economy/snapshot`;后端汇总进玩家档案(`CommunityController` 的 playerProfile)与经济相关展示。完成后日志:`经济快照已上报(N 名玩家)`。

## 玩家端展示

- 控制台/玩家档案页:硬币余额、总游戏时长、最近登录
- 官网玩家页:同源数据

## 排查

| 现象 | 检查 |
|---|---|
| 官网余额一直是 0 | `economy.report` 是否 off/auto+非主服;Vault 是否有经济插件注册;后端日志有无快照接收 |
| 数据只有部分玩家 | Vault 路径遍历 OfflinePlayers,离线很久的玩家在 Essentials 文件模式下仍会读到 |
| 频率太低 | `tasks.economy-interval` 调小(下限 30s) |
