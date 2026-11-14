/*
 * Aura Media & Background Enhancer
 *
 * 1. Background playback: YouTube pauses video when the page reports itself hidden. The page-visible
 *    `document.hidden` / `visibilityState` properties are overridden through `wrappedJSObject`
 *    (a content script's own `document` view is a separate Xray wrapper the page never sees).
 * 2. Hide Shorts: toggles an attribute that the stylesheet aura.css keys off.
 * 3. "Video paused. Continue watching?": YouTube's idle timer pauses playback and asks for confirmation.
 *    The page's last-activity timestamp is refreshed and, if the prompt still appears, it is confirmed
 *    automatically and playback resumes (only while background play is enabled).
 * 4. Picture-in-Picture: the app asks (over a native port) to stretch the playing video over the page,
 *    and receives the video's real size so the PiP window gets the right aspect ratio.
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

  /* ---- Picture-in-Picture support (top frame only) ---- */
  var port = null;

  function playingVideo() {
    var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
    var active = videos.filter(function (v) { return !v.paused && !v.ended; })[0];
    return active || videos[0] || null;
  }

  function setPipLayout(enabled) {
    var root = document.documentElement;
    Array.prototype.forEach.call(document.querySelectorAll('video.aura-pip-video'), function (v) {
      v.classList.remove('aura-pip-video');
    });
    if (!enabled) {
      root.classList.remove('aura-pip');
      return;
    }
    var video = playingVideo();
    if (video) {
      video.classList.add('aura-pip-video');
      root.classList.add('aura-pip');
    }
  }

  function reportVideoSize(video) {
    if (port && video && video.videoWidth > 0 && video.videoHeight > 0) {
      try {
        port.postMessage({ type: 'videoSize', width: video.videoWidth, height: video.videoHeight });
      } catch (e) { /* port closed */ }
    }
  }

  if (window === window.top) {
    try {
      port = browser.runtime.connectNative('aura');
      port.onMessage.addListener(function (message) {
        if (message && message.type === 'pipLayout') {
          setPipLayout(!!message.enabled);
        }
      });
      port.onDisconnect.addListener(function () { port = null; });
    } catch (e) {
      port = null;
    }

    ['loadedmetadata', 'resize'].forEach(function (name) {
      document.addEventListener(name, function (event) {
        if (event.target && event.target.tagName === 'VIDEO') {
          reportVideoSize(event.target);
        }
      }, true);
    });
  }

  /* ---- "Continue watching?" prompt ---- */
  var PROMPT_TEXT = /continue watching|still watching|video paused|are you still there/i;
  var CONFIRM_BUTTON = /^\s*(yes|continue|continue watching|resume|ok|confirm)\b/i;
  var dismissTimer = null;

  function refreshActivity() {
    if (!backgroundPlay) {
      return;
    }
    try {
      // YouTube's idle detection compares against this "last activity" timestamp.
      window.wrappedJSObject._lact = Date.now();
    } catch (e) { /* not available yet */ }
  }

  function dismissContinuePrompt() {
    dismissTimer = null;
    if (!backgroundPlay) {
      return;
    }
    var containers = document.querySelectorAll(
      'yt-confirm-dialog-renderer, ytm-confirm-dialog-renderer, tp-yt-paper-dialog, ' +
      'ytm-popup-container, ytd-popup-container, [role="dialog"], [role="alertdialog"], dialog'
    );
    for (var i = 0; i < containers.length; i++) {
      var container = containers[i];
      if (!PROMPT_TEXT.test(container.textContent || '')) {
        continue;
      }
      var buttons = container.querySelectorAll('button, [role="button"], yt-button-renderer, ytm-button-renderer');
      var target = null;
      for (var j = 0; j < buttons.length; j++) {
        if (CONFIRM_BUTTON.test(buttons[j].textContent || '')) {
          target = buttons[j];
          break;
        }
      }
      target = target || buttons[0];
      if (target) {
        target.click();
        var video = playingVideo();
        if (video && video.paused) {
          var resume = video.play();
          if (resume && resume.catch) {
            resume.catch(function () { /* needs a user gesture; nothing more to do */ });
          }
        }
      }
      return;
    }
  }

  function scheduleDismiss() {
    if (dismissTimer === null) {
      dismissTimer = setTimeout(dismissContinuePrompt, 400);
    }
  }

  setInterval(refreshActivity, 30000);
  refreshActivity();
  new MutationObserver(scheduleDismiss).observe(document.documentElement, { childList: true, subtree: true });

  applyConfig(DEFAULTS);

  try {
    browser.runtime.sendNativeMessage('aura', { type: 'getConfig' }).then(applyConfig, function () {
      applyConfig(DEFAULTS);
    });
  } catch (e) {
    applyConfig(DEFAULTS);
  }
})();
