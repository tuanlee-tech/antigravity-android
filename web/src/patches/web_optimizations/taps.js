/**
 * patches/web_optimizations/taps.js - kill 300ms tap delay, enlarge mobile touch
 * targets, and apply persisted UI Scale (default 115% on mobile, 100% on Desktop Mode).
 */
'use strict';

function isDesktopModeActive() {
  try {
    if (window.Android && typeof window.Android.isDesktopMode === 'function') {
      return !!window.Android.isDesktopMode();
    }
    return localStorage.getItem('ag_desktop_mode') === 'true';
  } catch (_) {
    return false;
  }
}

function getSavedUiScale() {
  try {
    var raw = localStorage.getItem('ag_ui_scale');
    if (raw !== null) {
      var parsed = parseInt(raw, 10);
      if (parsed >= 100 && parsed <= 150) return parsed;
    }
  } catch (_) {}
  return 115;
}

function applyUiScale(explicitScale) {
  try {
    var desktop = isDesktopModeActive();
    var root = document.documentElement;
    if (!root) return;
    if (desktop) {
      root.classList.add('ag-desktop-mode');
    } else {
      root.classList.remove('ag-desktop-mode');
    }
    var scale;
    if (typeof explicitScale === 'number' && explicitScale >= 100 && explicitScale <= 150) {
      scale = explicitScale;
      try { localStorage.setItem('ag_ui_scale', String(scale)); } catch (_) {}
    } else {
      scale = desktop ? 100 : getSavedUiScale();
    }
    root.style.fontSize = (16 * scale / 100) + 'px';
  } catch (_) {}
}

window.__agApplyUiScale = applyUiScale;
window.__agGetUiScale = getSavedUiScale;

function installTaps(doc) {
  try {
    applyUiScale();
    var style = doc.createElement('style');
    style.setAttribute('data-agy', 'web-optimizations');
    style.textContent =
      'button,a,[role="button"],input,select,textarea{touch-action:manipulation;}' +
      'html:not(.ag-desktop-mode) button,' +
      'html:not(.ag-desktop-mode) [role="button"],' +
      'html:not(.ag-desktop-mode) [role="menuitem"],' +
      'html:not(.ag-desktop-mode) [role="option"]{' +
      'min-height:36px;' +
      '}' +
      'html:not(.ag-desktop-mode) [data-testid="model-selector-trigger"]{' +
      'min-height:38px;padding-left:10px;padding-right:10px;' +
      '}' +
      'html:not(.ag-desktop-mode) [data-testid="model-selector-panel"] [role="menuitem"],' +
      'html:not(.ag-desktop-mode) [data-testid="model-selector-item"]{' +
      'min-height:42px;padding-top:8px;padding-bottom:8px;' +
      '}';
    (doc.head || doc.documentElement).appendChild(style);
  } catch (e) {}
}
