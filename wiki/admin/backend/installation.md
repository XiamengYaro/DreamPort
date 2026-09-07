# 后端 · 部署与初始化

## 快速开始

```bash
# 0. 准备:Java 21(Temurin 推荐)、MySQL 8.x 已就绪
# 1. 获取 JAR(仓库构建或 Release 下载)
mvn clean package -DskipTests
# 2. 首次启动(工作目录即运行目录)
java -jar dreamport-server/target/dreamport-server-*.jar
# 3. 编辑工作目录下自动生成的 config.yml(见下一节),再次重启
# 4. 浏览器打开 http://主机:18898 → 首次会进入 /setup 初始化向导
```

> `config.yml` 首次启动自动生成,带中文注释,是**推荐的部署配置入口**;优先级:命令行参数 > 环境变量 > config.yml > 打包默认值。

## config.yml 必改项

```yaml
database:
  url: jdbc:mysql://127.0.0.1:3306/dreamport?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
  username: dreamport
  password: 你的数据库密码
security:
  jwt-secret: 换成至少32位的随机串   # 泄露=所有人可伪造登录
  jwt-ttl-days: 7
internal:
  server-token: 与插件配置一致的令牌  # Paper/Velocity 插件 backend.server-token
smtp:
  host: smtp.qq.com                  # 留空=日志模式(验证码只打印在日志)
  port: 465
  username: 发件邮箱
  password: SMTP 授权码
  from: no_reply@example.com
  ssl: true
ws-port: 18899
```

数据库账号需对目标库有 DDL 权限(Flyway 首次启动自动建表)。

## /setup 初始化向导

浏览器打开 `http://主机:18898`,首次访问进入 `/setup`:

- 站点基础信息(名称/Logo/描述/版本/服务器地址)
- **导入旧库(可选)**:从旧版 XMWhitelist 迁移——同库自动检测 / 上传 `.sql` dump / `users.json+audits.json` 文件三种方式
- 创建管理员账号

初始化完成标记存于 `dp_setting(setup.completed)`。

## 管理员

- **首个管理员**由 /setup 创建;后续在管理后台「系统配置 → 管理员名单」维护
- 名单内的账号用普通登录即为管理员(语义对齐旧版)
- 管理后台入口:`/admin`(需登录且在名单内)

## systemd 服务

参考仓库 `deploy/dreamport-server.service`:

```ini
[Unit]
Description=DreamPort Server
After=network.target mysql.service

[Service]
WorkingDirectory=/opt/dreamport
ExecStart=/usr/bin/java -jar dreamport-server.jar
Restart=on-failure
User=dreamport

[Install]
WantedBy=multi-user.target
```

> **WorkingDirectory 很重要**:`config.yml`、`static/uploads/`(头像/机器截图)、`docs/`(官网文档)、`email/`(邮件模板外置)都相对它存放。

## 升级

1. 备份数据库 + `static/uploads/`、`docs/`、`email/` 目录
2. 替换 JAR → 重启
3. 查看启动日志确认版本与 Flyway 迁移成功
4. 前端有更新时让用户浏览器强刷一次(HTML 已带 no-store,后续自动生效)

Flyway 升级失败会留下 `success=0` 记录与半建的表:修复 = 手工 DROP 半建表 + `DELETE FROM flyway_schema_history WHERE version='N' AND success=0` 再重启。

## 备份要点

- `dp_user` / `dp_setting` / `dp_chat_message` / `dp_photo_comment` / `dp_server` / `dp_online_history` 等表(见[数据库](database-and-tasks.md))
- 文件目录:`static/uploads/`(头像与图片)、`docs/`(官网文档库)、`email/`(自定义邮件模板)

## 健康检查

```bash
curl http://127.0.0.1:18898/api/health
# {"status":"ok","version":"1.1.0","virtualThread":true}
```
