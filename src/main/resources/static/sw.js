const CACHE = 'ponte-v3'; // bump a cada mudança de asset estático
const SHELL = [
  '/',
  '/index.html',
  '/styles.css',
  '/app.js',
  '/dashboard.html',
  '/dashboard.js',
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/icons/logo-mark.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== location.origin) return;

  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/uploads/')) {
    // network-first com fallback: a prancha carrega offline com os últimos dados.
    // Só respostas 2xx entram no cache — um 500 transitório não pode virar
    // o "último dado bom" servido offline.
    event.respondWith(
      fetch(event.request)
        .then((res) => {
          if (res.ok) {
            const copy = res.clone();
            caches.open(CACHE)
              .then((cache) => cache.put(event.request, copy))
              .catch(() => { /* falha ao cachear não afeta a resposta */ });
          }
          return res;
        })
        .catch(() => caches.match(event.request))
    );
  } else {
    // cache-first para o shell do app
    event.respondWith(
      caches.match(event.request).then((hit) => hit || fetch(event.request))
    );
  }
});
