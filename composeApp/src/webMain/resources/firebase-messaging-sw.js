// Service worker for Web Push. It must be served from the site root, which is why it sits next to
// index.html rather than in a subfolder: a worker can only control pages at or below its own path.
//
// The browser runs this file outside the page, so it cannot read index.html's config and needs its
// own copy. Keep the two in step. Everything here stays commented out until a Firebase project is
// configured, so an unconfigured build registers a worker that does nothing instead of throwing.
// See docs/notifications.md.

// self.importScripts('https://www.gstatic.com/firebasejs/13.0.0/firebase-app-compat.js');
// self.importScripts('https://www.gstatic.com/firebasejs/13.0.0/firebase-messaging-compat.js');
//
// firebase.initializeApp({
//     apiKey: 'REPLACE_ME',
//     authDomain: 'REPLACE_ME.firebaseapp.com',
//     projectId: 'REPLACE_ME',
//     messagingSenderId: 'REPLACE_ME',
//     appId: 'REPLACE_ME'
// });
//
// // Messages that arrive while no tab is focused. A message carrying a `notification` block is
// // shown by the SDK itself; this handler covers the data-only ones.
// firebase.messaging().onBackgroundMessage(function (payload) {
//     var data = payload.data || {};
//     var title = (payload.notification && payload.notification.title) || data.title;
//     if (!title) return;
//     self.registration.showNotification(title, {
//         body: (payload.notification && payload.notification.body) || data.body,
//         // One notification per kind, so a stream of order updates does not bury everything else.
//         tag: data.type || 'tana_default'
//     });
// });

// Clicking a notification focuses an open tab instead of opening a second copy of the app.
self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    event.waitUntil(
        self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (clientList) {
            for (var i = 0; i < clientList.length; i++) {
                if ('focus' in clientList[i]) return clientList[i].focus();
            }
            return self.clients.openWindow ? self.clients.openWindow('/') : undefined;
        })
    );
});
