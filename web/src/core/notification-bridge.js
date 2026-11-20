/**
 * core/notification-bridge.js - bridges browser window.Notification to native
 * Android notifications (AndroidBridge.notifyAgentEvent) so WebNotificationService
 * works inside Android WebView without an OS-unsupported Web Notification prompt.
 */
(function installNotificationBridge() {
  function AgyNotification(title, options) {
    options = options && typeof options === 'object' ? options : {};
    this.title = title == null ? 'Antigravity' : String(title);
    this.body = options.body == null ? '' : String(options.body);
    this.tag = options.tag == null ? '' : String(options.tag);
    this.silent = !!options.silent;
    this.onclick = null;
    this.onclose = null;
    this.onerror = null;
    this.onshow = null;

    try {
      var bridge = window.Android || window.AndroidBridge;
      var token = window.__AG_NATIVE_BRIDGE_TOKEN__ || '';
      if (bridge && typeof bridge.notifyAgentEvent === 'function' && token) {
        bridge.notifyAgentEvent(token, this.title, this.body, this.tag, this.silent);
      }
    } catch (e) {}
  }

  AgyNotification.prototype.close = function () {};
  AgyNotification.permission = 'granted';
  AgyNotification.maxActions = 0;
  AgyNotification.requestPermission = function (callback) {
    if (typeof callback === 'function') {
      try { callback('granted'); } catch (e) {}
    }
    return Promise.resolve('granted');
  };

  window.Notification = AgyNotification;
})();
