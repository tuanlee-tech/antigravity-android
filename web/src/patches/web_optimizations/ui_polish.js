/**
 * patches/web_optimizations/ui_polish.js - Antigravity v2.33.0-inspired visual
 * polish for mobile WebView: refined Dark/Light surface tokens, sharp hairline
 * borders, folder picker quick-jump pills, and elevated mobile modals/sheets.
 */
'use strict';

function installUiPolish(doc) {
  try {
    if (doc.querySelector('style[data-agy="ui-polish"]')) return;
    var style = doc.createElement('style');
    style.setAttribute('data-agy', 'ui-polish');
    style.textContent =
      /* ---- Scoped Dark palette (#0e1014 cool charcoal) ---- */
      'body.theme-dark{' +
        '--background:#0e1014;' +
        '--foreground:#e6e9f0;' +
        '--primary:#3b82f6;' +
        '--primary-foreground:#ffffff;' +
        '--secondary:#212634;' +
        '--secondary-foreground:#a5afc0;' +
        '--muted:#161a23;' +
        '--muted-foreground:#7a8599;' +
        '--placeholder:#525d73;' +
        '--border:rgba(255,255,255,0.08);' +
        '--card:#151922;' +
        '--card-border:rgba(255,255,255,0.09);' +
        '--sidebar:#13161d;' +
        '--sidebar-secondary:#1b202b;' +
        '--sidebar-muted:#141821;' +
        '--link:#60a5fa;' +
        '--color-background:var(--background);' +
        '--color-foreground:var(--foreground);' +
        '--color-primary:var(--primary);' +
        '--color-primary-foreground:var(--primary-foreground);' +
        '--color-secondary:var(--secondary);' +
        '--color-secondary-foreground:var(--secondary-foreground);' +
        '--color-muted:var(--muted);' +
        '--color-muted-foreground:var(--muted-foreground);' +
        '--color-border:var(--border);' +
        '--color-card:var(--card);' +
        '--color-card-border:var(--card-border);' +
        '--color-sidebar:var(--sidebar);' +
        '--color-sidebar-secondary:var(--sidebar-secondary);' +
        '--color-sidebar-muted:var(--sidebar-muted);' +
        '--color-link:var(--link);' +
        'background-color:var(--background);' +
        'color:var(--foreground);' +
      '}' +

      /* ---- Scoped Light palette (clean cool white) ---- */
      'body.theme-light{' +
        '--background:#ffffff;' +
        '--foreground:#111318;' +
        '--primary:#2563eb;' +
        '--primary-foreground:#ffffff;' +
        '--secondary:#eef1f6;' +
        '--secondary-foreground:#475569;' +
        '--muted:#f4f6fa;' +
        '--muted-foreground:#64748b;' +
        '--placeholder:#94a3b8;' +
        '--border:rgba(15,23,42,0.08);' +
        '--card:#ffffff;' +
        '--card-border:rgba(15,23,42,0.10);' +
        '--sidebar:#f7f8fb;' +
        '--sidebar-secondary:#edf0f6;' +
        '--sidebar-muted:#f3f5f9;' +
        '--link:#2563eb;' +
        '--color-background:var(--background);' +
        '--color-foreground:var(--foreground);' +
        '--color-primary:var(--primary);' +
        '--color-primary-foreground:var(--primary-foreground);' +
        '--color-secondary:var(--secondary);' +
        '--color-secondary-foreground:var(--secondary-foreground);' +
        '--color-muted:var(--muted);' +
        '--color-muted-foreground:var(--muted-foreground);' +
        '--color-border:var(--border);' +
        '--color-card:var(--card);' +
        '--color-card-border:var(--card-border);' +
        '--color-sidebar:var(--sidebar);' +
        '--color-sidebar-secondary:var(--sidebar-secondary);' +
        '--color-sidebar-muted:var(--sidebar-muted);' +
        '--color-link:var(--link);' +
        'background-color:var(--background);' +
        'color:var(--foreground);' +
      '}' +

      /* ---- Folder Picker quick-jump bar & mobile modal placement ---- */
      'html:not(.ag-desktop-mode) .items-start[class*="z-[5100]"],' +
      'html:not(.ag-desktop-mode) .items-start[class*="z-[4800]"]{' +
        'padding-top:max(6vh,calc(env(safe-area-inset-top,0px) + 20px)) !important;' +
        'background:rgba(0,0,0,0.42);' +
      '}' +
      'div:has(> .agy-folder-shortcuts){' +
        'opacity:1 !important;' +
        'padding:6px 8px !important;' +
        'border-radius:12px !important;' +
        'background:var(--card) !important;' +
        'border-color:var(--card-border) !important;' +
      '}' +
      '.agy-folder-shortcuts{' +
        'display:flex;' +
        'flex-wrap:wrap;' +
        'align-items:center;' +
        'gap:6px;' +
        'width:100%;' +
      '}' +
      '.agy-folder-chip{' +
        'display:inline-flex;' +
        'align-items:center;' +
        'justify-content:center;' +
        'min-height:30px !important;' +
        'padding:4px 10px;' +
        'border-radius:8px;' +
        'font-size:12px;' +
        'font-weight:500;' +
        'line-height:1.2;' +
        'cursor:pointer;' +
        'border:1px solid var(--border);' +
        'background:var(--secondary);' +
        'color:var(--secondary-foreground);' +
        'transition:transform 120ms ease,background-color 140ms ease,color 140ms ease,border-color 140ms ease;' +
      '}' +
      '.agy-folder-chip:active{transform:scale(0.96);}' +
      '.agy-folder-chip.is-active{' +
        'background:rgba(59,130,246,0.16);' +
        'border-color:rgba(59,130,246,0.45);' +
        'color:var(--link);' +
      '}' +
      '.agy-folder-chip.is-system{' +
        'margin-left:auto;' +
        'background:var(--primary);' +
        'border-color:transparent;' +
        'color:var(--primary-foreground);' +
      '}' +

      /* ---- Compact mobile login / onboarding / picker card sizing ---- */
      'html:not(.ag-desktop-mode) .entrance-auth-panel{' +
        'width:100%;' +
        'max-width:100%;' +
        'padding-left:20px;' +
        'padding-right:20px;' +
        'margin-top:1.25rem !important;' +
        'box-sizing:border-box;' +
      '}' +
      'html:not(.ag-desktop-mode) .entrance-title{' +
        'font-size:1.25rem !important;' +
        'line-height:1.35 !important;' +
        'padding-left:20px;' +
        'padding-right:20px;' +
      '}' +
      'html:not(.ag-desktop-mode) [class~="w-[480px]"]{' +
        'width:calc(100vw - 40px) !important;' +
        'max-width:340px !important;' +
        'padding:16px !important;' +
        'scrollbar-gutter:auto !important;' +
        'border-radius:16px !important;' +
        'box-sizing:border-box !important;' +
      '}' +
      'html:not(.ag-desktop-mode) #advanced-sso-panel{' +
        'margin-left:-16px !important;' +
        'margin-right:-16px !important;' +
        'padding-left:16px !important;' +
        'padding-right:16px !important;' +
      '}' +

      /* ---- Elevated FastPick / Dialog / Code / Scrollbar polish ---- */
      '[data-testid="fastpick"]{' +
        'border-radius:14px !important;' +
        'background:var(--card) !important;' +
        'border-color:var(--card-border) !important;' +
      '}' +
      'body.theme-dark [data-testid="fastpick"]{' +
        'box-shadow:0 20px 48px rgba(0,0,0,0.52),0 0 0 1px rgba(255,255,255,0.06) !important;' +
      '}' +
      'body.theme-light [data-testid="fastpick"]{' +
        'box-shadow:0 16px 40px rgba(15,23,42,0.14),0 0 0 1px rgba(15,23,42,0.06) !important;' +
      '}' +
      'pre{border-radius:10px;}' +
      '::-webkit-scrollbar{width:5px;height:5px;}' +
      '::-webkit-scrollbar-track{background:transparent;}' +
      '::-webkit-scrollbar-thumb{background:rgba(128,128,128,0.28);border-radius:999px;}';

    (doc.head || doc.documentElement).appendChild(style);
  } catch (e) {}
}
