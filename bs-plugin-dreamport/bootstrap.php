<?php

/**
 * DreamPort 登录互通 —— Blessing Skin 插件入口。
 *
 * 功能：
 * 1. /auth/login/dreamport          重定向到 DreamPort 的 OAuth2 授权页（SPA 路由 /oauth2/authorize）
 * 2. /auth/login/dreamport/callback 授权码回调：code 换 token → 拉取用户信息 → 按 email 关联/注册 → 登录
 * 3. /dreamport/api/players         DreamPort 服务端按 email 拉取角色（X-Dreamport-Secret 共享密钥）
 * 4. RenderingFooter 注入登录页按钮
 *
 * 配置见插件管理 → 本插件「配置」页。
 */

use App\Events\RenderingFooter;
use App\Services\Hook;
use Illuminate\Contracts\Events\Dispatcher;

return function (Dispatcher $events) {
    Hook::addRoute(function ($routes) {
        $routes->group([
            'middleware' => ['web'],
            'prefix' => 'auth/login/dreamport',
            'namespace' => 'Dreamport\\OAuth',
        ], function ($route) {
            $route->get('/', 'DreamportOAuthController@redirect');
            $route->get('callback', 'DreamportOAuthController@callback');
        });

        // 服务端接口(DreamPort 后端调用):共享密钥鉴权,无会话 ——
        // 不能挂 web 组,否则 Laravel 的 VerifyCsrfToken 会以 419 拒绝所有 POST
        $routes->group([
            'prefix' => 'dreamport/api',
            'namespace' => 'Dreamport\\OAuth',
        ], function ($route) {
            $route->get('players', 'DreamportOAuthController@players');
            $route->get('skin', 'DreamportOAuthController@skin');
            $route->post('provision', 'DreamportOAuthController@provision');
            $route->post('update-password', 'DreamportOAuthController@updatePassword');
            $route->post('update-player-name', 'DreamportOAuthController@updatePlayerName');
            $route->post('update-email', 'DreamportOAuthController@updateEmail');
        });
    });

    // 登录页注入按钮（.login-box 仅存在于登录页）
    $events->listen(RenderingFooter::class, function (RenderingFooter $event) {
        $event->addContent(
            view('Dreamport\OAuth::login-button', ['url' => url('/auth/login/dreamport')])->render()
        );
    });
};
