#!/usr/bin/env bash
# 发布 wiki/ 文档库到 GitHub Wiki(github.com/XiamengYaro/DreamPort.wiki)
# 前置:GitHub 仓库的 Wiki 已在网页端初始化过一次(访问仓库 Wiki 标签页 → Create the first page → 保存任意内容)
# 之后在本机重复执行本脚本即可持续更新。
set -euo pipefail
cd "$(dirname "$0")/.."

REPO_SLUG="${1:-XiamengYaro/DreamPort}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

python3 - "$WORK" <<'EOF'
import os, re, shutil, sys
dst = sys.argv[1]
M = {
 'README.md':'Home',
 'player/getting-started.md':'玩家-快速上手',
 'player/register-and-review.md':'玩家-注册问卷审核',
 'player/verify-identity.md':'玩家-ID验证',
 'player/skin-station.md':'玩家-皮肤站',
 'player/qq-and-chat.md':'玩家-QQ与聊天',
 'player/faq.md':'玩家-常见问题',
 'admin/deployment-overview.md':'服主-部署总览',
 'admin/backend/introduction.md':'后端-架构',
 'admin/backend/installation.md':'后端-部署',
 'admin/backend/configuration.md':'后端-配置',
 'admin/backend/admin-panel.md':'后端-管理后台',
 'admin/backend/api-reference.md':'后端-API',
 'admin/backend/database-and-tasks.md':'后端-数据库与任务',
 'admin/plugin-paper/introduction.md':'Paper插件-简介',
 'admin/plugin-paper/installation.md':'Paper插件-安装配置',
 'admin/plugin-paper/whitelist-and-maintenance.md':'Paper插件-进服校验',
 'admin/plugin-paper/chat-and-events.md':'Paper插件-聊天事件',
 'admin/plugin-paper/economy.md':'Paper插件-经济快照',
 'admin/plugin-paper/commands.md':'Paper插件-命令',
 'admin/plugin-velocity/introduction.md':'Velocity插件',
 'admin/plugin-astrbot/introduction.md':'AstrBot-简介安装',
 'admin/plugin-astrbot/configuration.md':'AstrBot-配置对接',
 'admin/plugin-astrbot/commands.md':'AstrBot-指令与群服桥',
 'admin/plugin-skinstation/introduction.md':'皮肤站-简介',
 'admin/plugin-skinstation/installation-and-config.md':'皮肤站-安装配置',
 'admin/plugin-skinstation/dreamport-integration.md':'皮肤站-对接DreamPort',
 'admin/plugin-skinstation/account-sync.md':'皮肤站-账号同步',
 'admin/plugin-skinstation/api-reference.md':'皮肤站-接口文档',
 'admin/plugin-skinstation/changelog.md':'皮肤站-更新日志',
 'admin/troubleshooting.md':'故障排查',
}
REPO = f'https://github.com/{os.environ.get("GH_SLUG", "XiamengYaro/DreamPort")}'
for src, page in M.items():
    text = open(os.path.join('wiki', src), encoding='utf-8').read()
    def link_sub(m):
        path = m.group(1).split('#')[0]
        if path in M: return f'({M[path]})'
        if path.startswith('../'): return f'({REPO}/blob/main/{path[3:]})'
        if path.endswith('.md') and not path.startswith('http'): return f'({REPO}/blob/main/{path})'
        return m.group(0)
    text = re.sub(r'\(([^)\s]+\.md[^)]*)\)', link_sub, text)
    open(os.path.join(dst, page + '.md'), 'w', encoding='utf-8').write(text)

sidebar = '**DreamPort 文档库**\n\n**玩家**\n- [[玩家-快速上手]]\n- [[玩家-注册问卷审核]]\n- [[玩家-ID验证]]\n- [[玩家-皮肤站]]\n- [[玩家-QQ与聊天]]\n- [[玩家-常见问题]]\n\n**服主**\n- [[服主-部署总览]]\n- [[后端-架构]] / [[后端-部署]] / [[后端-配置]]\n- [[后端-管理后台]] / [[后端-API]] / [[后端-数据库与任务]]\n- [[Paper插件-简介]] / [[Paper插件-安装配置]] / [[Paper插件-进服校验]] / [[Paper插件-聊天事件]] / [[Paper插件-经济快照]] / [[Paper插件-命令]]\n- [[Velocity插件]]\n- [[AstrBot-简介安装]] / [[AstrBot-配置对接]] / [[AstrBot-指令与群服桥]]\n- [[皮肤站-简介]] / [[皮肤站-安装配置]] / [[皮肤站-对接DreamPort]] / [[皮肤站-账号同步]] / [[皮肤站-接口文档]] / [[皮肤站-更新日志]]\n- [[故障排查]]\n'
open(os.path.join(dst, '_Sidebar.md'), 'w', encoding='utf-8').write(sidebar)
print(f'转换完成:{len(os.listdir(dst))} 个页面')
EOF

WIKI_DIR="$WORK/repo"
git clone "https://github.com/${REPO_SLUG}.wiki.git" "$WIKI_DIR" 2>/dev/null || git clone "git@github.com:${REPO_SLUG}.wiki.git" "$WIKI_DIR"
cp "$WORK"/*.md "$WIKI_DIR"/
cd "$WIKI_DIR"
git add -A
if git diff --cached --quiet; then echo "Wiki 已是最新,无需推送"; exit 0; fi
git commit -m "sync: DreamPort 文档库更新(源:仓库 wiki/)"
git push origin master
echo "✅ GitHub Wiki 已发布:https://github.com/${REPO_SLUG}/wiki"
