(function () {
  'use strict';
  // Prevent visibilitychange-triggered auto-pause on mobile web media players when user enables background play
  Object.defineProperty(document, 'visibilityState', {
    get: function () { return 'visible'; },
    configurable: true
  });
  Object.defineProperty(document, 'hidden', {
    get: function () { return false; },
    configurable: true
  });
  document.addEventListener('visibilitychange', function (event) {
    event.stopImmediatePropagation();
  }, true);
})();
