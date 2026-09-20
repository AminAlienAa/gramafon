package app.gramafon.webview;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * گرامافون: پنجره‌ی وب‌ویو برای اندروید.
 * سایت خودت رو با مشخصات «دسکتاپ» باز می‌کنه تا پلیر اسپاتیفای پخش کامل بده.
 */
public class MainActivity extends Activity {

    private static final String PLACEHOLDER = "YOUR-SITE";

    private WebView web;
    private String siteUrl = "";
    private String siteHost = "";
    private String desktopUa = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        siteUrl = BuildConfig.SITE_URL;
        String host = Uri.parse(siteUrl).getHost();
        if (host != null) siteHost = host;

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        startKeepAlive();

        desktopUa = buildDesktopUserAgent();

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#120a1a"));
        setContentView(web, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        configure(web);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return handleNavigation(request.getUrl());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showOffline();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // Spotify's "log in" button opens a popup window: show it on top of the page
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                final WebView popup = new WebView(MainActivity.this);
                configure(popup);
                final Dialog dialog = new Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                dialog.setContentView(popup);

                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        if (!request.isForMainFrame()) return false;
                        Uri u = request.getUrl();
                        String scheme = u.getScheme() == null ? "" : u.getScheme();
                        if (scheme.equals("http") || scheme.equals("https") || scheme.equals("about")) return false;
                        openExternal(u);
                        return true;
                    }
                });
                popup.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView window) {
                        dialog.dismiss();
                    }
                });
                dialog.setOnDismissListener(d -> popup.destroy());
                dialog.setOnKeyListener((d, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                        if (popup.canGoBack()) popup.goBack();
                        else dialog.dismiss();
                        return true;
                    }
                    return false;
                });
                dialog.show();

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }

            // Widevine (needed for full Spotify playback) asks for this permission
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                List<String> allowed = new ArrayList<>();
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) allowed.add(r);
                }
                if (allowed.isEmpty()) request.deny();
                else request.grant(allowed.toArray(new String[0]));
            }
        });

        if (siteUrl.contains(PLACEHOLDER) || host == null) {
            showMessage("آدرس سایت تنظیم نشده",
                    "موقع ساخت برنامه، آدرس سایت رو به ورک‌فلو بده (Variables ← SITE_URL) و دوباره بسازش.");
        } else {
            web.loadUrl(siteUrl);
        }
    }

    private void configure(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(desktopUa);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);   // the Spotify player needs its own cookies
    }

    // desktop Chrome user-agent using this phone's real WebView version
    private String buildDesktopUserAgent() {
        String major = "126";
        Matcher m = Pattern.compile("Chrome/(\\d+)").matcher(WebSettings.getDefaultUserAgent(this));
        if (m.find()) major = m.group(1);
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + major + ".0.0.0 Safari/537.36";
    }

    private boolean isInternalHost(String host) {
        if (host == null) return false;
        return host.equals(siteHost)
                || host.equals("spotify.com") || host.endsWith(".spotify.com")
                || host.equals("soundcloud.com") || host.endsWith(".soundcloud.com");
    }

    /** true = we handled it (opened elsewhere), false = load it inside the app */
    private boolean handleNavigation(Uri u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob")) return false;
        if ((scheme.equals("http") || scheme.equals("https")) && isInternalHost(u.getHost())) return false;
        openExternal(u);
        return true;
    }

    private void openExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException ignored) {
            // no app can open it
        }
    }

    private void showOffline() {
        showMessage("اینترنت وصل نیست",
                "وصل که شد دکمه‌ی زیر رو بزن. اگه فیلترشکن لازمه، روشنش کن.");
    }

    private void showMessage(String title, String text) {
        String html = "<html dir='rtl'><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='margin:0;display:grid;place-items:center;min-height:100vh;background:#120a1a;color:#f4ebe1;"
                + "font:16px/1.9 sans-serif;text-align:center;padding:24px;box-sizing:border-box'><div>"
                + "<h2 style='margin:0 0 6px'>" + title + "</h2>"
                + "<p style='margin:0 0 22px;color:#b9a9c9'>" + text + "</p>"
                + "<button onclick=\"location.href='" + siteUrl + "'\" style='padding:12px 28px;border:0;border-radius:14px;"
                + "background:#ffb454;color:#1b1026;font:inherit;font-weight:700'>تلاش دوباره</button>"
                + "</div></body></html>";
        web.loadDataWithBaseURL(siteUrl, html, "text/html", "utf-8", null);
    }

    private void startKeepAlive() {
        Intent i = new Intent(this, KeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (RuntimeException ignored) {
            // music may stop when the screen is off, everything else still works
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();   // keep the Spotify login
        super.onPause();                       // the WebView is NOT paused, so the music keeps playing
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, KeepAliveService.class));
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
