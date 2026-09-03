const CACHE_NAME = 'perfume-stalker-v1';

// 설치 시점 (아무것도 안 하지만 PWA 조건을 위해 필수)
self.addEventListener('install', (event) => {
    self.skipWaiting();
});

// 활성화 시점
self.addEventListener('activate', (event) => {
    event.waitUntil(clients.claim());
});

// 네트워크 가로채기 (일단 모든 통신을 그대로 통과시킴)
self.addEventListener('fetch', (event) => {
    event.respondWith(fetch(event.request));
});