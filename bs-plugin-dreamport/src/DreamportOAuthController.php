<?php

namespace Dreamport\OAuth;

use App\Events\PlayerWasAdded;
use App\Events\PlayerWillBeAdded;
use App\Models\Player;
use App\Models\User;
use App\Rules\PlayerName;
use Exception;
use GuzzleHttp\Client;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Event;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;

/**
 * DreamPort OAuth2 登录 + 账号开通/同步服务端接口。
 *
 * 登录流程(authorization_code):
 * /auth/login/dreamport → DreamPort /oauth2/authorize(SPA 授权确认页)
 * → /auth/login/dreamport/callback?code&state
 * → POST DreamPort /oauth2/token → GET /oauth2/userinfo
 * → 皮肤站按 email 关联账号(不存在且开启自动注册则注册)→ 登录
 *
 * 服务端接口(DreamPort 后端调用,X-Dreamport-Secret 共享密钥):
 * - GET  /dreamport/api/players          拉取某用户角色
 * - POST /dreamport/api/provision        开通账号+角色(注册一键开通;幂等)
 * - POST /dreamport/api/update-password  同步改密
 * - POST /dreamport/api/update-player-name 同步改名(旧角色改名,无角色则新建)
 * - POST /dreamport/api/update-email     同步改绑邮箱
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
            // 与 BS 官方注册对齐:初始积分读站点选项(默认 1000)
            $user->score = (int) option('user_initial_score', 1000);
            $user->save();
            // 密码随机(DreamPort 密码不可知):玩家改一次 DreamPort 密码即自动同步
            $user->changePassword(Str::random(32));
            $user->verified = true;
            $user->save();
            Event::dispatch(new \App\Events\UserRegistered($user));
        }

        Auth::login($user, true);

        // 兜底:账号存在但一个角色都没有时,自动创建同名角色(DreamPort 游戏名优先)
        if ($user->players()->count() === 0) {
            $name = (string) ($userInfo['minecraftName'] ?? '') ?: (string) $userInfo['username'];
            $this->ensurePlayer($user, $name);
        }

        return redirect('/user');
    }

    /**
     * DreamPort 头像渲染服务按名字取皮肤材质(原始 64x64 PNG,302 → /textures/{hash})。
     * 未找到角色或未设皮肤 → 404(DreamPort 侧回退默认脸)。
     */
    public function skin(Request $request)
    {
        $secret = (string) option('dp_api_secret', '');
        $given = (string) $request->header('X-Dreamport-Secret', '');
        if ($secret === '' || !hash_equals($secret, $given)) {
            return response()->json(['success' => false, 'message' => 'invalid secret'], 403);
        }
        $name = trim((string) $request->query('name', ''));
        if ($name === '') {
            return response()->json(['success' => false, 'message' => 'name required'], 400);
        }
        $player = \App\Models\Player::where('name', $name)->first();
        if ($player === null || !$player->tid_skin) {
            return response()->json(['success' => false, 'message' => 'no skin'], 404);
        }
        $texture = \App\Models\Texture::find($player->tid_skin);
        if ($texture === null) {
            return response()->json(['success' => false, 'message' => 'texture missing'], 404);
        }
        return redirect(rtrim(url('/'), '/').'/textures/'.$texture->hash, 302);
    }

    /**
     * DreamPort 服务端拉取某用户角色。
     * 契约:200 = 已关联(players 可为空数组),404 = 该邮箱在皮肤站无账号。
     */
    public function players(Request $request)
    {
        if (($resp = $this->guardSecret($request)) !== null) {
            return $resp;
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
            $players[] = $this->playerPayload($baseUrl, $player);
        }

        return response()->json([
            'success' => true,
            'data' => ['linked' => true, 'players' => $players],
        ]);
    }

    /**
     * 开通账号 + 同名角色(DreamPort 注册一键开通;幂等)。
     * 密码直接采用 DreamPort 注册密码,玩家凭同一套账密配置启动器。
     */
    public function provision(Request $request)
    {
        if (($resp = $this->guardSecret($request)) !== null) {
            return $resp;
        }

        $data = $request->json()->all();
        $email = trim((string) ($data['email'] ?? ''));
        $playerName = trim((string) ($data['playerName'] ?? ''));
        $nickname = trim((string) ($data['nickname'] ?? '')) ?: $playerName;
        $password = (string) ($data['password'] ?? '');
        if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return $this->apiFail('invalid email');
        }
        if ($playerName === '') {
            return $this->apiFail('playerName required');
        }
        if ($password === '') {
            return $this->apiFail('password required');
        }

        $user = User::where('email', $email)->first();
        $userCreated = false;
        if ($user === null) {
            $user = new User();
            $user->email = $email;
            $user->nickname = $nickname;
            $user->score = (int) option('user_initial_score', 1000);
            $user->avatar = 0;
            $user->permission = User::NORMAL;
            $user->register_at = \Carbon\Carbon::now();
            $user->last_sign_at = \Carbon\Carbon::now()->subDay();
            $user->ip = (string) ($data['ip'] ?? '') ?: '127.0.0.1';
            $user->save();
            $user->changePassword($password);
            $user->verified = true;
            $user->save();
            $userCreated = true;
            Event::dispatch(new \App\Events\UserRegistered($user));
        }

        $playerCreated = false;
        $reason = null;
        if ($user->players()->where('name', $playerName)->exists()) {
            // 已有同名角色:幂等成功
            $playerCreated = true;
        } else {
            [$playerCreated, $reason] = $this->ensurePlayer($user, $playerName);
        }

        return response()->json([
            'success' => true,
            'data' => [
                'userCreated' => $userCreated,
                'playerCreated' => $playerCreated,
                'playerName' => $playerName,
                'reason' => $reason,
            ],
        ]);
    }

    public function updatePassword(Request $request)
    {
        if (($resp = $this->guardSecret($request)) !== null) {
            return $resp;
        }
        $data = $request->json()->all();
        $user = User::where('email', trim((string) ($data['email'] ?? '')))->first();
        if ($user === null) {
            return response()->json(['success' => false, 'message' => 'not linked'], 404);
        }
        $password = (string) ($data['password'] ?? '');
        if ($password === '') {
            return $this->apiFail('password required');
        }
        $user->changePassword($password);

        return response()->json(['success' => true]);
    }

    public function updatePlayerName(Request $request)
    {
        if (($resp = $this->guardSecret($request)) !== null) {
            return $resp;
        }
        $data = $request->json()->all();
        $user = User::where('email', trim((string) ($data['email'] ?? '')))->first();
        if ($user === null) {
            return response()->json(['success' => false, 'message' => 'not linked'], 404);
        }
        $oldName = trim((string) ($data['oldName'] ?? ''));
        $newName = trim((string) ($data['newName'] ?? ''));
        if ($newName === '') {
            return $this->apiFail('newName required');
        }

        $player = $oldName === '' ? null : $user->players()->where('name', $oldName)->first();
        if ($player === null) {
            // 旧角色不存在:若用户一个角色都没有,直接按新名创建;否则不动(避免误改玩家自建角色)
            if ($user->players()->count() === 0) {
                [$ok, $reason] = $this->ensurePlayer($user, $newName);
                return response()->json(['success' => $ok, 'data' => ['renamed' => false, 'created' => $ok, 'reason' => $reason]]);
            }
            return response()->json(['success' => false, 'message' => 'player not found'], 404);
        }

        if (($err = $this->validatePlayerName($newName, $player->pid)) !== null) {
            return response()->json(['success' => false, 'message' => $err]);
        }
        $player->name = $newName;
        $player->save();

        return response()->json(['success' => true, 'data' => ['renamed' => true]]);
    }

    public function updateEmail(Request $request)
    {
        if (($resp = $this->guardSecret($request)) !== null) {
            return $resp;
        }
        $data = $request->json()->all();
        $oldEmail = trim((string) ($data['oldEmail'] ?? ''));
        $newEmail = trim((string) ($data['newEmail'] ?? ''));
        if (!filter_var($newEmail, FILTER_VALIDATE_EMAIL)) {
            return $this->apiFail('invalid newEmail');
        }
        $user = User::where('email', $oldEmail)->first();
        if ($user === null) {
            // 皮肤站尚无账号:改绑无从谈起,返回 404 让 DreamPort 记住新旧邮箱即可(后续 provision 用新邮箱)
            return response()->json(['success' => false, 'message' => 'not linked'], 404);
        }
        if (User::where('email', $newEmail)->exists()) {
            return $this->apiFail('new email already used');
        }
        $user->email = $newEmail;
        $user->save();

        return response()->json(['success' => true]);
    }

    // ---------- 内部工具 ----------

    private function guardSecret(Request $request)
    {
        $secret = (string) option('dp_api_secret', '');
        $given = (string) $request->header('X-Dreamport-Secret', '');
        if ($secret === '' || !hash_equals($secret, $given)) {
            return response()->json(['success' => false, 'message' => 'invalid secret'], 403);
        }

        return null;
    }

    private function apiFail(string $message)
    {
        return response()->json(['success' => false, 'message' => $message], 400);
    }

    /**
     * 校验角色名并创建(对齐 BS 官方 add player 规则,不扣积分)。
     * @return array{0: bool, 1: ?string} [是否成功, 失败原因]
     */
    private function ensurePlayer(User $user, string $name): array
    {
        if (($err = $this->validatePlayerName($name, null)) !== null) {
            return [false, $err];
        }
        Event::dispatch(new PlayerWillBeAdded($name));
        $player = new Player();
        $player->uid = $user->uid;
        $player->name = $name;
        $player->tid_skin = 0;
        $player->tid_cape = 0;
        $player->save();
        Event::dispatch(new PlayerWasAdded($player));

        return [true, null];
    }

    /** 官方规则校验:PlayerName + 站点长度限制 + 重名($excludePid 用于改名场景) */
    private function validatePlayerName(string $name, $excludePid): ?string
    {
        $validator = Validator::make(['name' => $name], [
            'name' => [
                'required',
                new PlayerName(),
                'min:'.option('player_name_length_min', 3),
                'max:'.option('player_name_length_max', 16),
            ],
        ]);
        if ($validator->fails()) {
            return 'player name not allowed by skin station rules';
        }
        $query = Player::where('name', $name);
        if ($excludePid !== null) {
            $query->where('pid', '!=', $excludePid);
        }
        if ($query->exists()) {
            return 'player name already taken';
        }

        return null;
    }

    private function playerPayload(string $baseUrl, Player $player): array
    {
        $skin = $player->tid_skin ? \App\Models\Texture::find($player->tid_skin) : null;
        $cape = $player->tid_cape ? \App\Models\Texture::find($player->tid_cape) : null;

        return [
            'pid' => $player->pid,
            'name' => $player->name,
            'model' => ($skin !== null && $skin->type === 'alex') ? 'slim' : 'classic',
            'skinUrl' => $skin ? $baseUrl.'/textures/'.$skin->hash : null,
            'capeUrl' => $cape ? $baseUrl.'/textures/'.$cape->hash : null,
        ];
    }

    private function fail(string $message)
    {
        return view('Dreamport\OAuth::error', ['message' => $message]);
    }
}
