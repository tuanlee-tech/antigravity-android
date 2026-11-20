/**
 * core/dialog-bridge.js - bridges window.dialog.showOpenDialog() to Android's
 * native ACTION_OPEN_DOCUMENT_TREE folder picker (AndroidBridge.openDirectoryPicker).
 */
'use strict';

(function installDialogBridge() {
  var pendingResolve = null;

  window.__onDirectoryPicked = function (path) {
    var resolve = pendingResolve;
    pendingResolve = null;
    if (typeof resolve === 'function') {
      resolve(typeof path === 'string' && path.length > 0 ? path : null);
    }
  };

  function showOpenDialog() {
    var bridge = window.Android || window.AndroidBridge;
    if (!bridge || typeof bridge.openDirectoryPicker !== 'function') {
      return Promise.resolve(null);
    }
    if (typeof pendingResolve === 'function') {
      var prev = pendingResolve;
      pendingResolve = null;
      prev(null);
    }
    return new Promise(function (resolve) {
      pendingResolve = resolve;
      try {
        bridge.openDirectoryPicker();
      } catch (e) {
        pendingResolve = null;
        resolve(null);
      }
    });
  }

  var existing = window.dialog && typeof window.dialog === 'object' ? window.dialog : {};
  if (typeof existing.showOpenDialog !== 'function') {
    existing.showOpenDialog = showOpenDialog;
  }
  window.dialog = existing;
})();
