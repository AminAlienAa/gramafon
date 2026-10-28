/* گرامافون: نسخه‌ی ویندوز
   یه پنجره‌ی ساده که سایت خودت رو (آدرس داخل config.json) باز می‌کنه.
   برای همین هر آپدیتی که روی سایت بدی، بدون ساختن دوباره‌ی نصب‌کننده توی اپ هم میاد. */
const { app, BrowserWindow, shell, Menu, session, globalShortcut, components } = require('electron');
const path = require('path');
const config = require('./config.json');

// hosts allowed to open as in-app popups (Spotify login + the social sign-ins it offers)
const LOGIN_HOST = /(^|\.)(spotify\.com|scdn\.co|spotifycdn\.com|accounts\.google\.com|facebook\.com|appleid\.apple\.com|apple\.com)$/i;
function isLoginUrl(url) {
  if (url === 'about:blank') return true;   // window.open() first opens a blank window, then navigates it
  try {
    const u = new URL(url);
    return u.protocol === 'https:' && LOGIN_HOST.test(u.hostname);
  } catch { return false; }
}

const APP_URL = String(config.url || '');
let APP_ORIGIN = '';
try { APP_ORIGIN = new URL(APP_URL).origin; } catch {}

// look like a normal Chrome to Spotify / SoundCloud
app.userAgentFallback = app.userAgentFallback
  .replace(/\s?Electron\/\S+/, '')
  .replace(/\s?gramafon-desktop\/\S+/i, '')
  .replace(/\s?Gramafon\/\S+/i, '');

app.setAppUserModelId('app.gramafon.desktop');

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  let win = null;

  app.on('second-instance', () => {
    if (!win) return;
    if (win.isMinimized()) win.restore();
    win.focus();
  });

  const send = (key) => {
    if (!win) return;
    win.webContents.executeJavaScript(
      `document.dispatchEvent(new KeyboardEvent('keydown',{key:${JSON.stringify(key)},bubbles:true}))`
    ).catch(() => {});
  };

  // backup: Ctrl+Shift+S opens the Spotify login page inside the app (same session as the player)
  function openSpotifyLogin() {
    const w = new BrowserWindow({
      width: 520, height: 740, parent: win || undefined, autoHideMenuBar: true, title: 'Spotify',
      webPreferences: { contextIsolation: true, sandbox: true, nodeIntegration: false }
    });
    w.webContents.setWindowOpenHandler(({ url }) => (isLoginUrl(url) ? { action: 'allow' } : { action: 'deny' }));
    w.loadURL('https://accounts.spotify.com/login');
    w.webContents.on('did-navigate', (e, url) => {
      // logged in -> Spotify redirects away from /login; close and reload the player
      if (/^https:\/\/(open|www)\.spotify\.com\//i.test(url) || /accounts\.spotify\.com\/(en\/)?status/i.test(url)) {
        w.close();
        if (win) win.webContents.reload();
      }
    });
  }

  function createWindow() {
    win = new BrowserWindow({
      width: 1100,
      height: 780,
      minWidth: 380,
      minHeight: 560,
      backgroundColor: '#120a1a',
      title: 'گرامافون',
      icon: path.join(__dirname, 'build', 'icon.png'),
      autoHideMenuBar: true,
      show: false,
      webPreferences: {
        contextIsolation: true,
        sandbox: true,
        nodeIntegration: false,
        autoplayPolicy: 'no-user-gesture-required',  // lets the page start audio in its players
        backgroundThrottling: false                  // keep playing while minimized
      }
    });
    Menu.setApplicationMenu(null);
    win.once('ready-to-show', () => win.show());

    if (!APP_ORIGIN || /YOUR-SITE/i.test(APP_URL)) {
      win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(
        '<body dir="rtl" style="font:16px Tahoma,sans-serif;background:#120a1a;color:#f4ebe1;padding:40px;line-height:2">' +
        '<h2>آدرس سایت تنظیم نشده</h2><p>فایل <b>config.json</b> رو باز کن و به‌جای <b>YOUR-SITE-ADDRESS</b> آدرس سایتت رو بنویس، بعد اپ رو دوباره بساز.</p></body>'
      ));
      return;
    }

    win.loadURL(APP_URL);

    // links to other sites open in the normal browser,
    // EXCEPT the Spotify login popup (and Google/Facebook/Apple sign-in used by Spotify):
    // that one must open inside the app, so the login cookies land in the app's own session
    // (otherwise the embedded player stays logged out and only plays 30-second previews)
    const hookWindowOpen = (wc) => {
      wc.setWindowOpenHandler(({ url }) => {
        if (isLoginUrl(url)) {
          return {
            action: 'allow',
            overrideBrowserWindowOptions: {
              width: 520, height: 740, parent: win, autoHideMenuBar: true,
              backgroundColor: '#ffffff', title: 'Spotify',
              webPreferences: { contextIsolation: true, sandbox: true, nodeIntegration: false }
            }
          };
        }
        if (/^https?:/i.test(url)) shell.openExternal(url);
        return { action: 'deny' };
      });
    };
    hookWindowOpen(win.webContents);
    // the login popup can open further popups (e.g. "Continue with Google"): same rule
    win.webContents.on('did-create-window', (child) => {
      child.setMenuBarVisibility(false);
      hookWindowOpen(child.webContents);
    });
    win.webContents.on('will-navigate', (e, url) => {
      let origin = '';
      try { origin = new URL(url).origin; } catch {}
      if (origin !== APP_ORIGIN) {
        e.preventDefault();
        if (/^https?:/i.test(url)) shell.openExternal(url);
      }
    });

    // no internet: friendly page that retries by itself
    win.webContents.on('did-fail-load', (e, code, desc, url, isMainFrame) => {
      if (isMainFrame && code !== -3) win.loadFile(path.join(__dirname, 'offline.html'), { query: { u: APP_URL } });
    });

    // F5 / Ctrl+R reload · F11 fullscreen · Ctrl + / - / 0 zoom
    win.webContents.on('before-input-event', (e, input) => {
      if (input.type !== 'keyDown') return;
      const ctrl = input.control || input.meta;
      const k = input.key;
      const zoom = (d) => win.webContents.setZoomLevel(Math.max(-3, Math.min(4, win.webContents.getZoomLevel() + d)));
      if (k === 'F5' || (ctrl && k.toLowerCase() === 'r')) { win.webContents.reload(); e.preventDefault(); }
      else if (ctrl && input.shift && k.toLowerCase() === 's') { openSpotifyLogin(); e.preventDefault(); }
      else if (k === 'F11') { win.setFullScreen(!win.isFullScreen()); e.preventDefault(); }
      else if (ctrl && (k === '=' || k === '+')) { zoom(0.5); e.preventDefault(); }
      else if (ctrl && k === '-') { zoom(-0.5); e.preventDefault(); }
      else if (ctrl && k === '0') { win.webContents.setZoomLevel(0); e.preventDefault(); }
    });

    win.on('closed', () => { win = null; });
  }

  app.whenReady().then(async () => {
    // only what the players need (DRM playback, fullscreen); everything else is denied
    const allowed = new Set(['protected-media-identifier', 'fullscreen', 'clipboard-sanitized-write']);
    session.defaultSession.setPermissionRequestHandler((wc, permission, cb) => cb(allowed.has(permission)));

    // Widevine (needed for full Spotify playback) installs on first launch with the castLabs build
    try { if (components && components.whenReady) await components.whenReady(); } catch {}

    createWindow();

    // keyboard media keys control the player
    try {
      globalShortcut.register('MediaPlayPause', () => send(' '));
      globalShortcut.register('MediaNextTrack', () => send('n'));
      globalShortcut.register('MediaPreviousTrack', () => send('p'));
    } catch {}

    app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
  });

  app.on('will-quit', () => globalShortcut.unregisterAll());
  app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
}
