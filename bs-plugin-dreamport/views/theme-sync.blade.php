<style id="dp-theme-css">
  :root {
    --dp-accent-50: #fff7ed; --dp-accent-100: #ffedd5; --dp-accent-200: #fed7aa;
    --dp-accent-300: #fdba74; --dp-accent-400: #fb923c; --dp-accent-500: #f97316;
    --dp-accent-600: #ea580c; --dp-accent-700: #c2410c; --dp-accent-800: #9a3412;
    --dp-accent-900: #7c2d12; --dp-accent-950: #431407;
  }
  #dp-bg-layer { position: fixed; inset: 0; z-index: -2; background-size: cover;
    background-position: center; filter: brightness(.32) saturate(1.1); pointer-events: none; }
  body.layout-top-nav, body.login-page, body.register-page {
    background: #0f1218 !important; color: #d6d3d1 !important;
    font-family: Inter, -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif !important;
  }
  .content-wrapper, .wrapper { background: transparent !important; }
  body.layout-top-nav .content-wrapper, body.layout-top-nav .main-footer { background: transparent !important; }
  .card, .box, .login-card-body, .register-card-body {
    background: rgba(22, 24, 30, .74) !important;
    border: 1px solid rgba(255, 255, 255, .14) !important;
    border-radius: 20px !important;
    backdrop-filter: blur(18px) saturate(160%);
    -webkit-backdrop-filter: blur(18px) saturate(160%);
    box-shadow: 0 8px 32px rgba(0, 0, 0, .35) !important;
  }
  .card-header, .box-header, .card-footer, .box-footer {
    background: transparent !important; border-color: rgba(255, 255, 255, .08) !important; color: #e7e5e4 !important;
  }
  body, .content-wrapper, .card-body, .box-body { color: #d6d3d1 !important; }
  h1, h2, h3, h4, h5, h6, .card-title, .box-title { color: #fff !important; }
  .main-header.navbar {
    background: rgba(15, 18, 24, .78) !important;
    backdrop-filter: blur(18px) saturate(160%);
    -webkit-backdrop-filter: blur(18px) saturate(160%);
    border-bottom: 1px solid rgba(255, 255, 255, .08) !important;
  }
  .main-header .nav-link, .main-header a, .main-header .navbar-text { color: rgba(255, 255, 255, .85) !important; }
  .main-header .nav-link:hover, .main-header a:hover { color: var(--dp-accent-300) !important; }
  .btn-primary { background: var(--dp-accent-500) !important; border-color: var(--dp-accent-600) !important; color: #fff !important; }
  .btn-primary:hover, .btn-primary:focus, .btn-primary:active {
    background: var(--dp-accent-600) !important; border-color: var(--dp-accent-700) !important;
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--dp-accent-500) 30%, transparent) !important;
  }
  .btn-outline-primary { color: var(--dp-accent-400) !important; border-color: var(--dp-accent-500) !important; }
  .btn-default, .btn-secondary {
    background: rgba(255, 255, 255, .08) !important; border-color: rgba(255, 255, 255, .18) !important; color: #e7e5e4 !important;
  }
  .btn-default:hover, .btn-secondary:hover { background: rgba(255, 255, 255, .14) !important; }
  .form-control, .form-select, select, input[type=text]:not(.dp-fm), input[type=email], input[type=password], input[type=number], textarea {
    background: rgba(0, 0, 0, .35) !important; border: 1px solid rgba(255, 255, 255, .16) !important;
    color: #fff !important; border-radius: 12px !important;
  }
  .form-control:focus, input:focus, select:focus, textarea:focus {
    border-color: var(--dp-accent-500) !important;
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--dp-accent-500) 25%, transparent) !important;
    background: rgba(0, 0, 0, .45) !important; color: #fff !important;
  }
  .form-control::placeholder, input::placeholder, textarea::placeholder { color: #6b6560 !important; }
  a { color: var(--dp-accent-400) !important; }
  a:hover { color: var(--dp-accent-300) !important; }
  .main-footer { background: transparent !important; border-top: 1px solid rgba(255, 255, 255, .06) !important; color: rgba(255, 255, 255, .45) !important; }
  .table { color: #d6d3d1 !important; }
  .table td, .table th { border-color: rgba(255, 255, 255, .08) !important; background: transparent !important; }
  .table thead th { color: #a8a29e !important; }
  .badge, .label { border-radius: 8px !important; }
  .alert { border-radius: 14px !important; border: 1px solid rgba(255, 255, 255, .12) !important; }
  .modal-content, .dropdown-menu { background: rgba(22, 24, 30, .95) !important; border: 1px solid rgba(255, 255, 255, .14) !important; border-radius: 16px !important; color: #d6d3d1 !important; }
  .dropdown-item { color: #d6d3d1 !important; }
  .dropdown-item:hover { background: rgba(255, 255, 255, .08) !important; color: #fff !important; }
  .nav-pills .nav-link { color: #d6d3d1 !important; border-radius: 12px !important; }
  .nav-pills .nav-link.active { background: var(--dp-accent-500) !important; color: #fff !important; }
  ::selection { background: color-mix(in srgb, var(--dp-accent-500) 40%, transparent); }
  input, select, textarea { color-scheme: dark; }
  /* 滚动条 */
  ::-webkit-scrollbar { width: 10px; height: 10px; }
  ::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, .16); border-radius: 8px; }
  ::-webkit-scrollbar-track { background: transparent; }
</style>
<div id="dp-bg-layer"></div>
<script>
(function () {
  var DP = @json($dpUrl);
  if (!DP) return;
  function hexToHsl(hex) {
    var m = hex.replace('#', '');
    var r = parseInt(m.slice(0, 2), 16) / 255, g = parseInt(m.slice(2, 4), 16) / 255, b = parseInt(m.slice(4, 6), 16) / 255;
    var max = Math.max(r, g, b), min = Math.min(r, g, b), l = (max + min) / 2;
    if (max === min) return [0, 0];
    var d = max - min, s = l > 0.5 ? d / (2 - max - min) : d / (max + min), h;
    if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
    else if (max === g) h = ((b - r) / d + 2) / 6;
    else h = ((r - g) / d + 4) / 6;
    return [h * 360, s];
  }
  function hslRgb(h, s, l) {
    function f(n) { var k = (n + h / 30) % 12; var a = s * Math.min(l, 1 - l);
      var v = l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1)); return Math.round(v * 255); }
    return 'rgb(' + f(0) + ',' + f(8) + ',' + f(4) + ')';
  }
  var SHADES = [['50', .97], ['100', .94], ['200', .87], ['300', .78], ['400', .67], ['500', .58],
                ['600', .51], ['700', .44], ['800', .36], ['900', .29], ['950', .18]];
  fetch(DP + '/api/config').then(function (r) { return r.json(); }).then(function (res) {
    if (!res.success) return;
    var portal = res.data.portal || {};
    var accent = portal.accent || '#f97316';
    if (/^#[0-9a-fA-F]{6}$/.test(accent)) {
      var hs = hexToHsl(accent), root = document.documentElement.style;
      SHADES.forEach(function (s) { root.setProperty('--dp-accent-' + s[0], hslRgb(hs[0], hs[1], s[1])); });
    }
    var bg = res.data.background && res.data.background.image;
    var layer = document.getElementById('dp-bg-layer');
    if (layer && bg) layer.style.backgroundImage = 'url(' + (bg.indexOf('http') === 0 ? bg : DP + bg) + ')';
  }).catch(function () { /* 主站不可达时保持默认主题 */ });
})();
</script>
