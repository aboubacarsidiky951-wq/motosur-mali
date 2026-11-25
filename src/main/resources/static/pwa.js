(function () {
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
      navigator.serviceWorker.register('/sw.js').catch(function () {});
    });
  }
  var deferred = null;
  var bar = document.getElementById('install-bar');
  var btn = document.getElementById('install-btn');
  var close = document.getElementById('install-close');
  var hint = document.getElementById('install-hint');
  var standalone = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
  var dismissed = false;
  try { dismissed = localStorage.getItem('ms-install-dismissed') === '1'; } catch (e) {}
  if (!bar || standalone || dismissed) return;
  var ua = navigator.userAgent || '';
  var ios = /iphone|ipad|ipod/i.test(ua);
  window.addEventListener('beforeinstallprompt', function (e) {
    e.preventDefault(); deferred = e; bar.hidden = false;
  });
  if (ios) { hint.hidden = false; btn.hidden = true; bar.hidden = false; }
  if (btn) btn.addEventListener('click', function () {
    if (!deferred) return;
    deferred.prompt();
    deferred.userChoice.then(function () { deferred = null; bar.hidden = true; });
  });
  if (close) close.addEventListener('click', function () {
    bar.hidden = true;
    try { localStorage.setItem('ms-install-dismissed', '1'); } catch (e) {}
  });
  window.addEventListener('appinstalled', function () { bar.hidden = true; });
})();
