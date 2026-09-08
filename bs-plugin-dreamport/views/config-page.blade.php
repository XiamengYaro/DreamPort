<style>
  .dp-cfg { max-width: 980px; margin: 0 auto; color: #d6d3d1; font-family: Inter, system-ui, sans-serif; }
  .dp-cfg .dp-card { background: rgba(22,24,30,.72); border: 1px solid rgba(255,255,255,.14); border-radius: 20px;
    backdrop-filter: blur(18px) saturate(160%); box-shadow: 0 8px 32px rgba(0,0,0,.35); padding: 22px 26px; margin-bottom: 20px; }
  .dp-cfg h2 { color: #fff; font-size: 20px; margin: 0 0 4px; display: flex; align-items: center; gap: 10px; }
  .dp-cfg h2 .dp-dot { width: 10px; height: 10px; border-radius: 50%; background: #f97316; box-shadow: 0 0 10px #f97316; }
  .dp-cfg h3 { color: #fdba74; font-size: 15px; margin: 0 0 12px; }
  .dp-cfg .dp-sub { color: #a8a29e; font-size: 13px; margin: 0; }
  .dp-cfg .dp-guide { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
  .dp-cfg .dp-guide ol { margin: 8px 0 0; padding-left: 18px; }
  .dp-cfg .dp-guide li { margin-bottom: 8px; line-height: 1.7; font-size: 13px; color: #c7c2bd; }
  .dp-cfg .dp-guide code { background: rgba(249,115,22,.15); color: #fdba74; padding: 1px 6px; border-radius: 6px; font-size: 12px; }
  .dp-cfg .dp-test { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
  .dp-cfg .dp-btn { background: #f97316; color: #fff; border: none; border-radius: 12px; padding: 10px 22px;
    font-weight: 600; cursor: pointer; transition: all .2s; }
  .dp-cfg .dp-btn:hover { background: #ea580c; transform: translateY(-1px); }
  .dp-cfg .dp-btn:disabled { opacity: .5; cursor: wait; transform: none; }
  .dp-cfg .dp-badge { display: inline-flex; align-items: center; gap: 8px; padding: 8px 16px; border-radius: 12px;
    font-size: 13px; font-weight: 600; border: 1px solid transparent; }
  .dp-cfg .dp-badge.ok { background: rgba(16,185,129,.12); border-color: rgba(16,185,129,.4); color: #34d399; }
  .dp-cfg .dp-badge.bad { background: rgba(244,63,94,.12); border-color: rgba(244,63,94,.4); color: #fb7185; }
  .dp-cfg .dp-badge .dp-pulse { width: 8px; height: 8px; border-radius: 50%; background: currentColor; animation: dpPulse 1.2s infinite; }
  @keyframes dpPulse { 0%,100% { opacity: 1; } 50% { opacity: .3; } }
  .dp-cfg .dp-field { margin-bottom: 18px; }
  .dp-cfg .dp-field label { display: block; color: #e7e5e4; font-weight: 600; font-size: 14px; margin-bottom: 6px; }
  .dp-cfg .dp-field .dp-hint { color: #8f8a85; font-size: 12px; margin-top: 5px; line-height: 1.6; }
  .dp-cfg .dp-field input[type=text], .dp-cfg .dp-field input[type=url] {
    width: 100%; background: rgba(0,0,0,.35); border: 1px solid rgba(255,255,255,.16); border-radius: 12px;
    padding: 10px 14px; color: #fff; font-size: 14px; font-family: ui-monospace, monospace; transition: border-color .2s;
  }
  .dp-cfg .dp-field input:focus { outline: none; border-color: #f97316; box-shadow: 0 0 0 3px rgba(249,115,22,.22); }
  .dp-cfg .dp-field input::placeholder { color: #6b6560; }
  .dp-cfg .dp-check { display: flex; align-items: flex-start; gap: 12px; padding: 14px 16px; border-radius: 14px;
    background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.08); margin-bottom: 12px; cursor: pointer; transition: all .2s; }
  .dp-cfg .dp-check:hover { border-color: rgba(249,115,22,.4); }
  .dp-cfg .dp-check input { margin-top: 3px; width: 16px; height: 16px; accent-color: #f97316; }
  .dp-cfg .dp-check b { color: #e7e5e4; font-size: 14px; display: block; }
  .dp-cfg .dp-check span { color: #8f8a85; font-size: 12px; line-height: 1.6; display: block; margin-top: 2px; }
  .dp-cfg .dp-grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 22px; }
  .dp-cfg .dp-saved { background: rgba(16,185,129,.12); border: 1px solid rgba(16,185,129,.4); color: #34d399;
    padding: 10px 18px; border-radius: 12px; margin-bottom: 16px; font-size: 14px; }
  @media (max-width: 768px) { .dp-cfg .dp-guide, .dp-cfg .dp-grid2 { grid-template-columns: 1fr; } }
</style>

<div class="dp-cfg">
  @if (session('dp_saved'))
    <div class="dp-saved">✓ 配置已保存,即时生效</div>
  @endif

  <div class="dp-card">
    <h2><span class="dp-dot"></span>DreamPort 互通配置</h2>
    <p class="dp-sub">玩家使用 DreamPort 账号授权登录皮肤站;DreamPort 反向展示皮肤站角色与皮肤。</p>
    <div class="dp-test" style="margin-top: 16px;">
      <button type="button" class="dp-btn" id="dp-test-btn">测试 DreamPort 连接</button>
      <span id="dp-test-result"></span>
    </div>
  </div>

  <div class="dp-card">
    <h3>对接指南(两端各配一次,凭据保持一致)</h3>
    <div class="dp-guide">
      <div>
        <ol>
          <li>DreamPort 管理后台 → 系统设置 → <b>BlessingSkin 互通</b> → 启用</li>
          <li>填入本站地址(<code>{{ url('/') }}</code>)与本页下方的 Client ID / Secret / 共享密钥</li>
          <li>保存后玩家即可在登录页使用 DreamPort 账号登录</li>
        </ol>
      </div>
      <div>
        <ol>
          <li>本页填写 DreamPort 地址与**同一组** Client ID / Secret / 共享密钥</li>
          <li>Client ID / Secret 为两端约定的自定义值,<b>无需 Passport</b></li>
        </ol>
      </div>
    </div>
  </div>

  <div class="dp-card">
    <h3>连接参数</h3>
    <form method="POST" action="{{ url('/dreamport/config/save') }}">
      @csrf
      <div class="dp-grid2">
        <div class="dp-field">
          <label>DreamPort 站点地址</label>
          <input type="url" name="dp_url" value="{{ $dp_url }}" placeholder="https://dreamport.example.com">
          <div class="dp-hint">以 http(s):// 开头的完整地址,末尾不带 /</div>
        </div>
        <div class="dp-field">
          <label>Client ID</label>
          <input type="text" name="dp_client_id" value="{{ $client_id }}" placeholder="如 dreamport-oauth">
          <div class="dp-hint">两端约定的自定义标识</div>
        </div>
        <div class="dp-field">
          <label>Client Secret</label>
          <input type="text" name="dp_client_secret" value="" placeholder="{{ $has_client_secret ? '已配置 —— 留空保持不变' : '40 位随机串' }}">
          <div class="dp-hint">与 DreamPort 后台一致;留空 = 保持原值</div>
        </div>
        <div class="dp-field">
          <label>API 共享密钥</label>
          <input type="text" name="dp_api_secret" value="" placeholder="{{ $has_api_secret ? '已配置 —— 留空保持不变' : '与 DreamPort 后台一致' }}">
          <div class="dp-hint">角色数据接口鉴权;留空 = 保持原值</div>
        </div>
      </div>

      <h3 style="margin-top: 22px;">功能开关</h3>
      <label class="dp-check">
        <input type="checkbox" name="dp_auto_register" value="1" @if($auto_register) checked @endif>
        <span><b>自动注册</b>皮肤站不存在同邮箱账号时自动创建(初始积分取站点选项 user_initial_score)</span>
      </label>
      <label class="dp-check">
        <input type="checkbox" name="dp_hide_password_login" value="1" @if($hide_login) checked @endif>
        <span><b>纯 DreamPort 登录</b>隐藏登录页账密表单,网页只能用 DreamPort 授权登录(游戏内 Yggdrasil 认证不受影响)</span>
      </label>

      <button type="submit" class="dp-btn" style="margin-top: 8px;">保存配置</button>
    </form>
  </div>
</div>

<script>
(function () {
  var btn = document.getElementById('dp-test-btn');
  var out = document.getElementById('dp-test-result');
  if (!btn || !out) return;
  var dpUrl = '{{ rtrim($dp_url, "/") }}';
  btn.addEventListener('click', function () {
    if (!dpUrl) { out.innerHTML = '<span class="dp-badge bad">请先填写 DreamPort 站点地址</span>'; return; }
    btn.disabled = true;
    out.innerHTML = '<span class="dp-badge"><span class="dp-pulse"></span>测试中…</span>';
    fetch(dpUrl + '/api/health').then(function (r) { return r.json(); }).then(function (d) {
      out.innerHTML = '<span class="dp-badge ok"><span class="dp-pulse"></span>连接成功' +
        (d.version ? ' · 后端 v' + d.version : '') + '</span>';
    }).catch(function () {
      out.innerHTML = '<span class="dp-badge bad"><span class="dp-pulse"></span>连接失败 —— 地址不通或后端未启动</span>';
    }).finally(function () { btn.disabled = false; });
  });
})();
</script>
