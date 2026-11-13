/*
 * Aura Media & Background Enhancer
 *
 * 1. Background playback: YouTube pauses video when the page reports itself hidden. The page-visible
 *    `document.hidden` / `visibilityState` properties are overridden through `wrappedJSObject`
 *    (a content script's own `document` view is a separate Xray wrapper the page never sees).
 * 2. Hide Shorts: toggles an attribute that the stylesheet shorts.css keys off.
 *
 * Settings come from the app through native messaging; sensible defaults apply until the reply arrives.
 */
(function () {
  'use strict';

  var DEFAULTS = { backgroundPlay: true, hideShorts: false };
  var VISIBILITY_PROPS = {
    hidden: false,
    webkitHidden: false,
    visibilityState: 'visible',
    webkitVisibilityState: 'visible'
  };

  var backgroundPlay = DEFAULTS.backgroundPlay;

  function swallowVisibilityEvent(event) {
    if (backgroundPlay) {
      event.stopImmediatePropagation();
    }
  }

  function overrideVisibility(enabled) {
    var page = window.wrappedJSObject;
    if (!page || !page.document) {
      return;
    }
    var doc = page.document;
    Object.keys(VISIBILITY_PROPS).forEach(function (prop) {
      try {
        if (enabled) {
          var value = VISIBILITY_PROPS[prop];
          Object.defineProperty(doc, prop, {
            configurable: true,
            get: exportFunction(function () { return value; }, page)
          });
        } else {
          delete doc[prop];
        }
      } catch (e) {
        /* Property not configurable on this document; ignore. */
      }
    });
  }

  function applyConfig(config) {
    var merged = Object.assign({}, DEFAULTS, config || {});
    backgroundPlay = !!merged.backgroundPlay;
    overrideVisibility(backgroundPlay);
    if (document.documentElement) {
      document.documentElement.setAttribute('data-aura-hide-shorts', merged.hideShorts ? '1' : '0');
    }
  }

  ['visibilitychange', 'webkitvisibilitychange'].forEach(function (name) {
    document.addEventListener(name, swallowVisibilityEvent, true);
  });

  applyConfig(DEFAULTS);

  try {
    browser.runtime.sendNativeMessage('aura', { type: 'getConfig' }).then(applyConfig, function () {
      applyConfig(DEFAULTS);
    });
  } catch (e) {
    applyConfig(DEFAULTS);
  }
})();
