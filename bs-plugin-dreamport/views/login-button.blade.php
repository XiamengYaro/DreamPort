<script>
(function () {
    document.addEventListener('DOMContentLoaded', function () {
        var form = document.querySelector('.login-box form');
        if (!form || document.getElementById('dp-login-btn')) return;
        var a = document.createElement('a');
        a.id = 'dp-login-btn';
        a.href = @json($url);
        a.className = 'btn btn-block btn-secondary';
        a.style.marginTop = '10px';
        a.textContent = '使用 DreamPort 账号登录';
        form.appendChild(a);
    });
})();
</script>
