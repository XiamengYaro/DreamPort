<?php

namespace Dreamport\OAuth;

use Option;

/**
 * 插件配置页（package.json enchants.config 指向本类的 render 方法）。
 */
class Configuration
{
    public function render()
    {
        $form = Option::form('dreamport_oauth', 'DreamPort 互通配置', function ($form) {
            $form->text('dp_url', 'DreamPort 站点地址')
                ->hint('以 https:// 开头的完整地址，末尾不带 /')
                ->placeholder('https://dreamport.example.com');
            $form->text('dp_client_id', 'Client ID')
                ->hint('在 DreamPort 后台「系统设置 → BlessingSkin 互通」查看');
            $form->text('dp_client_secret', 'Client Secret')
                ->hint('与 Client ID 一同在 DreamPort 后台获取');
            $form->checkbox('dp_auto_register', '自动注册')
                ->label('皮肤站不存在同邮箱账号时自动创建')
                ->description('关闭后，未注册的用户将无法通过 DreamPort 登录皮肤站');
            $form->checkbox('dp_hide_password_login', '纯 DreamPort 登录')
                ->label('隐藏登录页的账号密码表单')
                ->description('开启后皮肤站网页只能用 DreamPort 授权登录（游戏内 Yggdrasil 认证不受影响）；请确保已开启自动注册或已完成注册一键开通');
            $form->text('dp_api_secret', '角色数据接口密钥')
                ->hint('与 DreamPort 后台「BlessingSkin 互通」中的 API 共享密钥保持一致，用于本站向 DreamPort 提供角色数据');
        })->handle();

        return view('Dreamport\OAuth::config', ['form' => $form]);
    }
}
