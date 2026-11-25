/* MotoSûr Mali : service worker. Ne met JAMAIS l'API en cache (données sensibles). */
const VERSION = 'motosur-v1';
const SHELL = ['/offline.html', '/icons/icon-192.png', '/icons/icon-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;
  if (url.pathname.startsWith('/api/')) return;          // jamais de cache pour l'API
  if (url.pathname.startsWith('/maintenance') || url.pathname.startsWith('/espace')
      || url.pathname.startsWith('/invitation')) return;  // pages réservées : toujours le réseau

  if (req.mode === 'navigate') {
    e.respondWith(fetch(req).catch(() => caches.match('/offline.html')));
    return;
  }
  // fichiers du site : le réseau d'abord, le cache seulement si hors ligne
  e.respondWith(
    fetch(req).then((res) => {
      if (res.ok && (url.pathname.startsWith('/icons/') || /\.(css|js|png|svg)$/.test(url.pathname))) {
        const copy = res.clone();
        caches.open(VERSION).then((c) => c.put(req, copy));
      }
      return res;
    }).catch(() => caches.match(req))
  );
});
