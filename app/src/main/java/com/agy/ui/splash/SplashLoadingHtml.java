package com.agy.ui.splash;

import android.webkit.WebView;

import com.agy.runtime.RuntimeConfig;

/**
 * Minimalist Antigravity splash screen: centered Antigravity vector mark,
 * three-dot loading bounce, and a subtle bottom version pill (`v<version>`).
 * Colors are pre-resolved for the current mode; no prefers-color-scheme query
 * is used because WebView resolves that from OS night mode rather than app mode.
 */
public final class SplashLoadingHtml {

    private SplashLoadingHtml() {
    }

    public static String getLoadingHtml(String foregroundColor, String backgroundColor) {
        return getLoadingHtml(foregroundColor, backgroundColor, RuntimeConfig.APP_VERSION_NAME);
    }

    public static String getLoadingHtml(String foregroundColor, String backgroundColor,
                                        String versionName) {
        String fg = foregroundColor == null ? "#f1f3f6" : foregroundColor;
        String bg = backgroundColor == null ? "#0e1014" : backgroundColor;
        String ver = (versionName == null || versionName.trim().isEmpty())
                ? RuntimeConfig.APP_VERSION_NAME
                : versionName.trim();
        String verLabel = escape("v" + ver);

        return "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">\n"
                + "<style>\n"
                + "  :root {\n"
                + "    --bg: " + bg + ";\n"
                + "    --fg: " + fg + ";\n"
                + "  }\n"
                + "  html, body {\n"
                + "    margin: 0;\n"
                + "    padding: 0;\n"
                + "    height: 100%;\n"
                + "    width: 100%;\n"
                + "    background: var(--bg);\n"
                + "    color: var(--fg);\n"
                + "    font-family: system-ui, -apple-system, BlinkMacSystemFont, sans-serif;\n"
                + "    display: flex;\n"
                + "    align-items: center;\n"
                + "    justify-content: center;\n"
                + "    overflow: hidden;\n"
                + "    -webkit-user-select: none;\n"
                + "  }\n"
                + "  @keyframes splash-enter {\n"
                + "    from { opacity: 0; transform: translateY(4px) scale(0.96); }\n"
                + "    to { opacity: 1; transform: translateY(0) scale(1); }\n"
                + "  }\n"
                + "  @keyframes dot-bounce {\n"
                + "    0%, 100% { transform: translateY(0); opacity: 0.45; }\n"
                + "    18% { transform: translateY(-3px); opacity: 0.95; }\n"
                + "    36% { transform: translateY(0); opacity: 0.45; }\n"
                + "  }\n"
                + "  .splash-center {\n"
                + "    display: flex;\n"
                + "    flex-direction: column;\n"
                + "    align-items: center;\n"
                + "    justify-content: center;\n"
                + "    gap: 18px;\n"
                + "    animation: splash-enter 360ms cubic-bezier(0.16, 1, 0.3, 1) forwards;\n"
                + "  }\n"
                + "  .splash-mark {\n"
                + "    width: 48px;\n"
                + "    height: 48px;\n"
                + "    display: block;\n"
                + "    opacity: 0.92;\n"
                + "  }\n"
                + "  .dots {\n"
                + "    display: inline-flex;\n"
                + "    align-items: center;\n"
                + "    gap: 5px;\n"
                + "    height: 14px;\n"
                + "  }\n"
                + "  .animate-dot-bounce {\n"
                + "    display: inline-block;\n"
                + "    width: 4px;\n"
                + "    height: 4px;\n"
                + "    border-radius: 50%;\n"
                + "    background-color: currentColor;\n"
                + "    animation: dot-bounce 1.5s ease-in-out infinite;\n"
                + "  }\n"
                + "  .version-badge {\n"
                + "    position: fixed;\n"
                + "    left: 50%;\n"
                + "    bottom: 28px;\n"
                + "    bottom: max(28px, calc(env(safe-area-inset-bottom, 0px) + 20px));\n"
                + "    transform: translateX(-50%);\n"
                + "    font-size: 11.5px;\n"
                + "    font-weight: 500;\n"
                + "    letter-spacing: 0.04em;\n"
                + "    font-variant-numeric: tabular-nums;\n"
                + "    color: var(--fg);\n"
                + "    opacity: 0.56;\n"
                + "    background: rgba(128, 128, 128, 0.08);\n"
                + "    border: 1px solid rgba(128, 128, 128, 0.16);\n"
                + "    padding: 4px 11px;\n"
                + "    border-radius: 999px;\n"
                + "    line-height: 1.2;\n"
                + "  }\n"
                + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div class=\"splash-center\" aria-label=\"Loading\">\n"
                + "    <svg class=\"splash-mark\" viewBox=\"0 0 96 96\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n"
                + "      <defs>\n"
                + "        <linearGradient id=\"agyMarkGrad\" x1=\"16\" y1=\"8\" x2=\"80\" y2=\"90\" gradientUnits=\"userSpaceOnUse\">\n"
                + "          <stop offset=\"0%\" stop-color=\"currentColor\" stop-opacity=\"0.96\"/>\n"
                + "          <stop offset=\"100%\" stop-color=\"#60a5fa\" stop-opacity=\"0.86\"/>\n"
                + "        </linearGradient>\n"
                + "      </defs>\n"
                + "      <path d=\"M85.9734 89.3438C91.2234 93.2812 99.0984 90.6562 91.8796 83.4375C70.2234 62.4375 74.8171 4.6875 47.9109 4.6875C21.0046 4.6875 25.5984 62.4375 3.94213 83.4375C-3.93287 91.3125 4.59838 93.2812 9.84838 89.3438C30.1921 75.5625 28.8796 51.2812 47.9109 51.2812C66.9421 51.2812 65.6296 75.5625 85.9734 89.3438Z\" fill=\"url(#agyMarkGrad)\"/>\n"
                + "    </svg>\n"
                + "    <span class=\"dots\">\n"
                + "      <span class=\"animate-dot-bounce\" style=\"animation-delay:0ms;\"></span>\n"
                + "      <span class=\"animate-dot-bounce\" style=\"animation-delay:120ms;\"></span>\n"
                + "      <span class=\"animate-dot-bounce\" style=\"animation-delay:240ms;\"></span>\n"
                + "    </span>\n"
                + "  </div>\n"
                + "  <div class=\"version-badge\">" + verLabel + "</div>\n"
                + "</body>\n"
                + "</html>\n";
    }

    public static void load(WebView splashView, String foregroundColor, String backgroundColor) {
        load(splashView, foregroundColor, backgroundColor, RuntimeConfig.APP_VERSION_NAME);
    }

    public static void load(WebView splashView, String foregroundColor, String backgroundColor,
                            String versionName) {
        if (splashView == null) {
            return;
        }
        splashView.loadDataWithBaseURL(null,
                getLoadingHtml(foregroundColor, backgroundColor, versionName),
                "text/html", "utf-8", null);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
