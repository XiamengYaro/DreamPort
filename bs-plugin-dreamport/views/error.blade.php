<!DOCTYPE html>
<html lang="zh_CN">
<head>
    <meta charset="utf-8">
    <title>DreamPort 登录</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { font-family: -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif; background: #f4f6f9;
               display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
        .dp-card { background: #fff; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,.08);
                   padding: 32px 40px; max-width: 440px; text-align: center; }
        h2 { margin: 0 0 12px; font-size: 18px; color: #d9534f; }
        p { color: #555; font-size: 14px; line-height: 1.7; margin: 0 0 20px; word-break: break-all; }
        a { color: #007bff; text-decoration: none; font-size: 14px; }
    </style>
</head>
<body>
    <div class="dp-card">
        <h2>DreamPort 登录未能完成</h2>
        <p>{{ $message }}</p>
        <a href="{{ url('auth/login') }}">返回皮肤站登录页</a>
    </div>
</body>
</html>
