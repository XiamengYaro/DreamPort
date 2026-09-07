<?php

namespace Dreamport\OAuth;

use App\Models\Texture;
use App\Models\User;
use Exception;
use GuzzleHttp\Client;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;

/**
 * DreamPort OAuth2 登录 + 角色数据接口。
 *
 * 登录流程（authorization_code）：
 * /auth/login/dreamport → DreamPort /oauth2/authorize（SPA 授权确认页）
 * → /auth/login/dreamport/callback?code&state
 * → POST DreamPort /oauth2/token（code 换 access_token）
 * → GET DreamPort /oauth2/userinfo（Bearer 取 username/email）
 * → 皮肤站按 email 关联账号（不存在且开启自动注册则注册）→ 登录。
 */
class DreamportOAuthController
{
    public function redirect(Request $request)
    {
        $dpUrl = rtrim((string) option('dp_url'), '/');
        $clientId = (string) option('dp_client_id');
        if ($dpUrl === '' || $clientId === '') {
            return $this->fail('管理员尚未完成 DreamPort 登录配置，请联系皮肤站管理员。');
        }

        $state = Str::random(32);
        session(['dreamport_oauth_state' => $state]);

        $query = http_build_query([
            'client_id' => $clientId,
            'redirect_uri' => url('/auth/login/dreamport/callback'),
            'response_type' => 'code',
            'scope' => '',
            'state' => $state,
        ]);

        return redirect($dpUrl.'/oauth2/authorize?'.$query);
    }

    public function callback(Request $request)
    {
        $code = (string) $request->query('code', '');
        $state = (string) $request->query('state', '');
        $expectedState = (string) session('dreamport_oauth_state', '');
        session()->forget('dreamport_oauth_state');

        if ($code === '' || $expectedState === '' || !hash_equals($expectedState, $state)) {
            return $this->fail('授权状态校验失败，请从皮肤站登录页重新发起。');
        }

        $dpUrl = rtrim((string) option('dp_url'), '/');
        $clientId = (string) option('dp_client_id');
        $clientSecret = (string) option('dp_client_secret');
        if ($dpUrl === '' || $clientId === '' || $clientSecret === '') {
            return $this->fail('管理员尚未完成 DreamPort 登录配置，请联系皮肤站管理员。');
        }

        $client = new Client(['timeout' => 15, 'http_errors' => false]);
        try {
            $tokenResp = $client->post($dpUrl.'/oauth2/token', [
                'form_params' => [
                    'grant_type' => 'authorization_code',
                    'client_id' => $clientId,
                    'client_secret' => $clientSecret,
                    'redirect_uri' => url('/auth/login/dreamport/callback'),
                    'code' => $code,
                ],
            ]);
            $tokenData = json_decode((string) $tokenResp->getBody(), true);
            if ($tokenResp->getStatusCode() !== 200 || empty($tokenData['access_token'])) {
                return $this->fail('换取访问令牌失败（DreamPort 返回 HTTP '.$tokenResp->getStatusCode().'），请重试。');
            }

            $userResp = $client->get($dpUrl.'/oauth2/userinfo', [
                'headers' => ['Authorization' => 'Bearer '.$tokenData['access_token']],
            ]);
            $userInfo = json_decode((string) $userResp->getBody(), true);
            if ($userResp->getStatusCode() !== 200 || empty($userInfo['username'])) {
                return $this->fail('获取用户信息失败，请重试。');
            }
        } catch (Exception $e) {
            return $this->fail('无法连接 DreamPort：'.$e->getMessage());
        }

        $email = (string) ($userInfo['email'] ?? '');
        if ($email === '') {
            return $this->fail('该 DreamPort 账号未绑定邮箱，无法登录皮肤站。');
        }

        $user = User::where('email', $email)->first();
        if ($user === null) {
            if (!option('dp_auto_register')) {
                return $this->fail('皮肤站尚无与 DreamPort 账号（'.$email.'）同邮箱的账号，请先在本站注册，或联系管理员开启自动注册。');
            }
            $user = new User();
            $user->email = $email;
            $user->nickname = (string) ($userInfo['nickname'] ?? $userInfo['username']);
            $user->save();
            $user->changePassword(Str::random(32));
            $user->verified = true;
            $user->save();
        }

        Auth::login($user, true);

        return redirect('/user');
    }

    /**
     * DreamPort 服务端拉取某用户角色。
     * 契约：200 = 已关联（players 可为空数组），404 = 该邮箱在皮肤站无账号。
     */
    public function players(Request $request)
    {
        $secret = (string) option('dp_api_secret', '');
        $given = (string) $request->header('X-Dreamport-Secret', '');
        if ($secret === '' || !hash_equals($secret, $given)) {
            return response()->json(['success' => false, 'message' => 'invalid secret'], 403);
        }

        $email = trim((string) $request->query('email', ''));
        if ($email === '') {
            return response()->json(['success' => false, 'message' => 'email required'], 400);
        }

        $user = User::where('email', $email)->first();
        if ($user === null) {
            return response()->json(['success' => false, 'message' => 'not linked'], 404);
        }

        $baseUrl = rtrim(url('/'), '/');
        $players = [];
        foreach ($user->players as $player) {
            $skin = $player->tid_skin ? Texture::find($player->tid_skin) : null;
            $cape = $player->tid_cape ? Texture::find($player->tid_cape) : null;
            $players[] = [
                'pid' => $player->pid,
                'name' => $player->name,
                'model' => ($skin !== null && $skin->type === 'alex') ? 'slim' : 'classic',
                'skinUrl' => $skin ? $baseUrl.'/textures/'.$skin->hash : null,
                'capeUrl' => $cape ? $baseUrl.'/textures/'.$cape->hash : null,
            ];
        }

        return response()->json([
            'success' => true,
            'data' => ['linked' => true, 'players' => $players],
        ]);
    }

    private function fail(string $message)
    {
        return view('Dreamport\OAuth::error', ['message' => $message]);
    }
}
