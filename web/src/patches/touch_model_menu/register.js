/**
 * patches/touch_model_menu/register.js - split touch handling on effort-group rows:
 *   - Tap model name (left/main area): let React's native onClick select the model immediately.
 *   - Tap thinking effort badge / chevron (right side): open the effort submenu.
 */
var effortStyleInjected = false;

function ensureEffortBadgeStyle() {
  if (effortStyleInjected) return;
  effortStyleInjected = true;
  try {
    var style = document.createElement('style');
    style.setAttribute('data-agy', 'touch-model-menu');
    style.textContent =
      '[data-ag-effort-row="1"] [data-ag-effort-pill="1"]{' +
      'padding:4px 8px;border-radius:6px;' +
      'border:1px solid rgba(148,163,184,0.35);' +
      'background:rgba(148,163,184,0.12);' +
      'opacity:0.95 !important;' +
      '}';
    (document.head || document.documentElement).appendChild(style);
  } catch (_) {}
}

function markEffortPill(row) {
  try {
    var baseSpan = row.querySelector(CONFIG.effortGroup);
    if (!baseSpan) return;
    var next = baseSpan.nextElementSibling;
    if (next && next.tagName === 'SPAN' && !next.getAttribute('data-ag-effort-pill')) {
      next.setAttribute('data-ag-effort-pill', '1');
    }
  } catch (_) {}
}

function isEffortTriggerTarget(row, event) {
  var target = event && event.target;
  if (target && target.closest) {
    if (target.closest('[data-ag-effort-pill="1"]')) return true;
    // Chevron SVG or right-side indicator after the model name span
    var baseSpan = row.querySelector(CONFIG.effortGroup);
    if (baseSpan && !baseSpan.contains(target) && target !== row) {
      var rectBase = baseSpan.getBoundingClientRect();
      var cx = typeof event.clientX === 'number' ? event.clientX : 0;
      if (cx > 0 && cx >= rectBase.right - 2) return true;
    }
  }
  try {
    var rect = row.getBoundingClientRect();
    var x = typeof event.clientX === 'number' ? event.clientX : 0;
    if (rect.width > 0 && x > 0) {
      var rightZone = Math.min(84, Math.max(56, rect.width * 0.34));
      return x >= rect.right - rightZone;
    }
  } catch (_) {}
  return false;
}

function makeHandler(row) {
  return function (event) {
    if (event.__agTouchSynthetic) return;
    if (typeof event.button === 'number' && event.button !== 0) return;
    if (!isEffortTriggerTarget(row, event)) {
      // User tapped the model name: let React's native onClick switch model immediately.
      return;
    }
    // User tapped the thinking effort badge / chevron on the right: open effort submenu.
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    openSubmenu(row);
  };
}

function registerRow(row) {
  if (!row || row.__agTouchManaged) return false;
  row.__agTouchManaged = true;
  row.setAttribute('data-ag-effort-row', '1');
  ensureEffortBadgeStyle();
  markEffortPill(row);
  var handler = makeHandler(row);
  row.addEventListener('pointerdown', handler, true);
  row.addEventListener('mousedown', handler, true);
  row.addEventListener('click', handler, true);
  state.rows++;
  return true;
}
