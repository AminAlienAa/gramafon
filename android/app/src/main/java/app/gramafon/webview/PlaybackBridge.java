package app.gramafon.webview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * پل بین صفحه‌ی وب (که می‌گه الان چی و چطور پخش می‌شه) و اعلان اندروید
 * (که دکمه‌های پخش/توقف و بعدی/قبلی رو نشون می‌ده).
 * یه شیء ساده و ثابته، چون هم اکتیویتی و هم سرویس باید بهش دسترسی داشته باشن.
 */
final class PlaybackBridge {

    /** آخرین وضعیتی که صفحه‌ی وب فرستاده. */
    static final class State {
        String title = "";
        String artist = "";
        String artworkUrl = null;
        String source = "";
        boolean playing = false;
        boolean canNext = false;
        boolean canPrev = false;
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static WeakReference<MainActivity> activityRef;
    private static WeakReference<KeepAliveService> serviceRef;

    private static State state = new State();
    private static String cachedArtUrl = null;
    private static Bitmap cachedArt = null;

    private PlaybackBridge() {}

    static void attachActivity(MainActivity a) { activityRef = new WeakReference<>(a); }
    static void detachActivity(MainActivity a) {
        if (activityRef != null && activityRef.get() == a) activityRef = null;
    }

    static void attachService(KeepAliveService s) {
        serviceRef = new WeakReference<>(s);
        s.onStateChanged(state, currentArt());  // let a freshly (re)started service catch up
    }
    static void detachService(KeepAliveService s) {
        if (serviceRef != null && serviceRef.get() == s) serviceRef = null;
    }

    private static Bitmap currentArt() {
        return state.artworkUrl != null && state.artworkUrl.equals(cachedArtUrl) ? cachedArt : null;
    }

    /** از AndroidBridge.postState (روی نخ اصلی) صدا زده می‌شه. */
    static void updateFromJson(String json) {
        State s = new State();
        try {
            JSONObject o = new JSONObject(json);
            s.title = o.optString("title", "");
            s.artist = o.optString("artist", "");
            s.artworkUrl = o.isNull("artwork") ? null : o.optString("artwork", null);
            s.source = o.optString("source", "");
            s.playing = o.optBoolean("playing", false);
            s.canNext = o.optBoolean("canNext", false);
            s.canPrev = o.optBoolean("canPrev", false);
        } catch (JSONException e) {
            return;
        }
        state = s;

        KeepAliveService svc = serviceRef != null ? serviceRef.get() : null;
        if (svc == null) return;

        if (s.artworkUrl != null && !s.artworkUrl.equals(cachedArtUrl)) {
            cachedArtUrl = s.artworkUrl;
            cachedArt = null;
            fetchArt(s.artworkUrl, svc);
        } else if (s.artworkUrl == null) {
            cachedArtUrl = null;
            cachedArt = null;
        }
        svc.onStateChanged(state, currentArt());
    }

    private static void fetchArt(final String url, final KeepAliveService svcAtRequestTime) {
        new Thread(() -> {
            Bitmap bmp = null;
            try {
                HttpURLConnection c = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                try (InputStream in = c.getInputStream()) {
                    bmp = BitmapFactory.decodeStream(in);
                }
            } catch (Exception ignored) {
                // no artwork, no big deal — the notification still shows title/artist
            }
            final Bitmap result = bmp;
            MAIN.post(() -> {
                if (result != null && url.equals(cachedArtUrl)) {
                    cachedArt = result;
                    KeepAliveService svc = serviceRef != null ? serviceRef.get() : null;
                    if (svc != null) svc.onStateChanged(state, result);
                }
            });
        }).start();
    }

    // notification button taps go back into the page's own controls
    static void sendToggle() { runJs("window.gramafonControl && window.gramafonControl.toggle();"); }
    static void sendNext()   { runJs("window.gramafonControl && window.gramafonControl.next();"); }
    static void sendPrev()   { runJs("window.gramafonControl && window.gramafonControl.prev();"); }

    private static void runJs(String js) {
        MainActivity a = activityRef != null ? activityRef.get() : null;
        if (a != null) a.runJs(js);
    }
}
