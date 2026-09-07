# 后端 · 头像渲染服务

> v1.2 新增:全站大头照由后端本地渲染(**双层皮肤**:基础脸 + 帽子层叠加),替换 crafthead 外网依赖;正版直接取官方皮肤,非正版取皮肤站;两级缓存。

## 端点

```
GET /api/avatar/{name}?size=N
```

- `name`:游戏 ID(URL 编码);公开访问
- `size`:8–256,默认 64(建议 8 的倍数,最近邻渲染更锐利)
- 响应:PNG + `Cache-Control: public, max-age=3600` + `ETag`(皮肤内容 hash)
- 条件请求:`If-None-Match` 命中返回 304
- 限频:600 次/分钟/IP

## 皮肤来源解析(按游戏 ID,严格分流)

| 玩家身份 | 皮肤来源 | 说明 |
|---|---|---|
| **正版**(microsoftVerified=true) | Mojang session server(sessionserver.mojang.com,按已存 uuid) | **官方皮肤,绝不经手皮肤站** |
| 非正版(皮肤站玩家) | 皮肤站插件 `GET /dreamport/api/skin?name=` → 302 到 `/textures/{hash}` | 共享密钥鉴权;换肤最迟 1 小时生效 |
| 未注册/无皮肤 | 程序绘制默认脸(Steve/Alex 按名字 hash) | 8x8 不透明 |

皮肤站互通未启用或不可达时:正版已验证用户照常取官方皮肤,其余回退默认脸,不影响页面。

## 渲染细节

- 兼容 64x64(新版)与 64x32(旧版)皮肤;脸区坐标 (8,8,8,8),帽子层 (40,8,8,8)
- 帽子层按 alpha 叠加(透明像素透出基础脸)
- 最近邻插值放大,像素风不失真

## 缓存(两级)

| 层 | 策略 |
|---|---|
| 浏览器 | Cache-Control 1h + ETag 条件请求;皮肤更换最迟 1 小时生效 |
| 服务端 | 内存 LRU(name → 皮肤图 + hash,TTL 1h,上限 2000 玩家);渲染本身极快 |

## 前端接入

`AppAvatar` 组件与所有头像位(玩家档案/控制台/聊天消息/在线列表/封禁页/玩家列表)统一走
`/api/avatar/{name}?size=`,已移除 crafthead 外链。皮肤站角色卡的 3D 整身预览仍走皮肤站自带渲染。
