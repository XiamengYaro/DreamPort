# 皮肤站插件 · 安装与配置

## 环境要求

| 项 | 要求 |
|---|---|
| BlessingSkin | ≥ 5.0.0(6.0.2 实测) |
| PHP | ≥ 8.1(BS 自身要求;无需额外扩展,HTTP 用 BS 内置 Guzzle) |
| DreamPort | 后端已部署且管理后台可访问 |

## 安装

1. 从构建产物取 `bs-plugin-dreamport/dreamport-oauth-*.zip`(**zip 内含一层 `dreamport-oauth/` 目录**——BS 上传安装是原样解压到 `plugins/`,只认一级子目录里的 package.json)
2. BS 管理后台 → Plugins → **Upload Archive** 上传
3. 插件列表出现「DreamPort 登录互通」→ 点开关**启用**
4. 列表「配置」按钮进入配置页

> 上传显示成功但列表不出现 = zip 结构问题(1.0.0 版缺陷),用 1.0.1+ 的包;`plugins/` 根目录若散落了 bootstrap.php/package.json/src/views 文件,先删掉再重传。

## 升级

上传新版本 zip **覆盖原目录**,配置与启用状态保留(BS 按 manifest 版本自动更新记录)。

## 配置项详解

| 配置项 | 键 | 说明 |
|---|---|---|
| DreamPort 站点地址 | `dp_url` | 如 `https://xmcraft.cn`,末尾不带 `/` |
| Client ID | `dp_client_id` | 自定义字符串,与 DreamPort 后台一致 |
| Client Secret | `dp_client_secret` | 自定义随机串(建议 40 位),与 DreamPort 后台一致 |
| 自动注册 | `dp_auto_register` | SSO 登录时皮肤站无同邮箱账号则自动建号(初始积分+随机密码);**建议开启** |
| 纯 DreamPort 登录 | `dp_hide_password_login` | 隐藏登录页账密表单,只留 DreamPort 按钮;游戏内 Yggdrasil 认证不受影响 |
| 角色数据接口密钥 | `dp_api_secret` | 与 DreamPort 后台「API 共享密钥」一致;服务端接口全靠它鉴权 |

配置页由 `Option::form` 渲染,提交即存 BS `options` 表,热生效。

## 站点选项(BS 后台)

| 选项 | 建议 | 与本插件的关系 |
|---|---|---|
| `user_initial_score` | **1000** | provision 与 SSO 自动注册的初始积分都读它 |
| `player_name_rule` | `official` | 自动建角色按此规则校验(official=字母数字下划线);DreamPort 注册侧已同步收紧 |
| `player_name_length_min/max` | 3 / 16 | 角色名长度校验 |

## 验证安装

1. 登出皮肤站 → 登录页出现「**使用 DreamPort 账号登录**」按钮(未出现=未启用;BS6 兼容需 ≥1.0.2)
2. DreamPort 后台互通配置就绪后,完整走一遍 SSO:点按钮 → DreamPort 授权页「允许」→ 跳回皮肤站登录成功
3. 在 DreamPort 控制台「皮肤站角色」卡确认角色出现
