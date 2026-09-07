<?php

namespace Dreamport\OAuth;

/**
 * 插件配置页(package.json enchants.config 指向本类)。
 * v1.2.0:自建玻璃风配置界面(含对接指南与连接测试),弃用 Option::form 默认渲染;
 * 保存走 POST /dreamport/config/save(role:admin)。
 */
class Configuration
{
    public function render()
    {
        return view('Dreamport\OAuth::config-page', [
            'dp_url' => (string) option('dp_url', ''),
            'client_id' => (string) option('dp_client_id', ''),
            'has_client_secret' => (string) option('dp_client_secret', '') !== '',
            'auto_register' => (bool) option('dp_auto_register', false),
            'hide_login' => (bool) option('dp_hide_password_login', false),
            'theme_sync' => (bool) option('dp_theme_sync', true),
            'has_api_secret' => (string) option('dp_api_secret', '') !== '',
        ]);
    }
}
