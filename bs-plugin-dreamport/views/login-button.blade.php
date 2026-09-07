<script>
(function () {
    function mount() {
        if (document.getElementById('dp-login-btn')) return;
        // 仅登录页展示(兼容子目录部署;注册/找回密码页不注入)
        if (location.pathname.indexOf('/auth/login') === -1) return;
        // BS v5- 为 AdminLTE .login-box > form;BS v6 登录页无 form 元素,落在 .login-card-body
        var host = document.querySelector('.login-box form')
            || document.querySelector('.login-box .login-card-body')
            || document.querySelector('.login-box');
        if (!host) return;
        var a = document.createElement('a');
        a.id = 'dp-login-btn';
        a.href = @json($url);
        a.className = 'btn btn-block btn-secondary';
        a.style.marginTop = '10px';
        a.textContent = '使用 DreamPort 账号登录';
        host.appendChild(a);
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', mount);
    } else {
        mount();
    }
})();
</script>
