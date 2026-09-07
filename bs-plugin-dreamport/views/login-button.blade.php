<script>
(function () {
    var HIDE_PASSWORD_LOGIN = @json((bool) option('dp_hide_password_login', false));
    function mount() {
        if (document.getElementById('dp-login-btn')) return;
        // 仅登录页展示(兼容子目录部署;注册/找回密码页不注入)
        if (location.pathname.indexOf('/auth/login') === -1) return;
        // BS v5- 为 AdminLTE .login-box > form;BS v6 登录页无 form 元素,落在 .login-card-body
        var form = document.querySelector('.login-box form');
        var body = document.querySelector('.login-box .login-card-body') || document.querySelector('.login-box');
        if (!body) return;

        if (HIDE_PASSWORD_LOGIN && body) {
            // 纯 SSO 模式:隐藏账密表单的全部子元素,只保留 DreamPort 按钮
            Array.prototype.forEach.call(body.children, function (el) { el.style.display = 'none'; });
        }

        var a = document.createElement('a');
        a.id = 'dp-login-btn';
        a.href = @json($url);
        a.className = 'btn btn-block btn-secondary';
        a.style.marginTop = '10px';
        a.textContent = '使用 DreamPort 账号登录';
        if (HIDE_PASSWORD_LOGIN && body) {
            body.appendChild(a);
        } else if (form) {
            form.appendChild(a);
        } else if (body) {
            body.appendChild(a);
        }
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', mount);
    } else {
        mount();
    }
})();
</script>
