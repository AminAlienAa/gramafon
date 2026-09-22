package app.gramafon.webview;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

/**
 * فقط برنامه رو زنده نگه می‌داره تا موقع خاموش شدن صفحه موزیک قطع نشه، و یه اعلان واقعی
 * پخش (با کاور، اسم آهنگ و دکمه‌های قبلی/پخش/بعدی) نشون می‌ده. اطلاعاتش رو از خود سایت
 * می‌گیره (PlaybackBridge)، پس با هر آهنگی که توی گرامافون عوض بشه، اعلان هم عوض می‌شه.
 */
public class KeepAliveService extends Service {

    static final String CHANNEL_ID = "gramafon_playback";
    private static final int NOTIF_ID = 1;

    static final String ACTION_TOGGLE = "app.gramafon.ACTION_TOGGLE";
    static final String ACTION_NEXT = "app.gramafon.ACTION_NEXT";
    static final String ACTION_PREV = "app.gramafon.ACTION_PREV";

    private MediaSession mediaSession;
    private PlaybackBridge.State lastState = new PlaybackBridge.State();
    private Bitmap lastArt = null;
    private boolean foregroundStarted = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "پخش گرامافون", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }

        mediaSession = new MediaSession(this, "gramafon");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { PlaybackBridge.sendToggle(); }
            @Override public void onPause() { PlaybackBridge.sendToggle(); }
            @Override public void onSkipToNext() { PlaybackBridge.sendNext(); }
            @Override public void onSkipToPrevious() { PlaybackBridge.sendPrev(); }
        });
        mediaSession.setActive(true);

        startForeground(NOTIF_ID, buildNotification());
        foregroundStarted = true;
        PlaybackBridge.attachService(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_TOGGLE.equals(action)) PlaybackBridge.sendToggle();
        else if (ACTION_NEXT.equals(action)) PlaybackBridge.sendNext();
        else if (ACTION_PREV.equals(action)) PlaybackBridge.sendPrev();
        return START_STICKY;
    }

    /** از PlaybackBridge صدا زده می‌شه، هر بار وضعیت پخش عوض بشه. */
    void onStateChanged(PlaybackBridge.State state, Bitmap art) {
        lastState = state;
        lastArt = art;
        updateSession();
        Notification n = buildNotification();
        if (foregroundStarted) {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, n);
        } else {
            startForeground(NOTIF_ID, n);
            foregroundStarted = true;
        }
    }

    private void updateSession() {
        long actions = PlaybackState.ACTION_PLAY_PAUSE
                | (lastState.canNext ? PlaybackState.ACTION_SKIP_TO_NEXT : 0)
                | (lastState.canPrev ? PlaybackState.ACTION_SKIP_TO_PREVIOUS : 0);
        int playState = lastState.playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(playState, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build());

        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, lastState.title == null ? "" : lastState.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, lastState.artist == null ? "" : lastState.artist);
        if (lastArt != null) meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, lastArt);
        mediaSession.setMetadata(meta.build());
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentPi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String title = lastState.title == null || lastState.title.isEmpty() ? "گرامافون" : lastState.title;
        String text = lastState.artist == null ? "" : lastState.artist;

        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentPi)
                .setOngoing(lastState.playing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        if (lastArt != null) b.setLargeIcon(lastArt);

        int prevIdx = -1, nextIdx = -1;
        int actionIndex = 0;
        if (lastState.canPrev) {
            b.addAction(mediaAction(android.R.drawable.ic_media_previous, "قبلی", ACTION_PREV));
            prevIdx = actionIndex++;
        }
        b.addAction(mediaAction(
                lastState.playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                lastState.playing ? "توقف" : "پخش",
                ACTION_TOGGLE));
        int playIdx = actionIndex++;
        if (lastState.canNext) {
            b.addAction(mediaAction(android.R.drawable.ic_media_next, "بعدی", ACTION_NEXT));
            nextIdx = actionIndex++;
        }

        int[] compact = prevIdx >= 0 && nextIdx >= 0
                ? new int[]{prevIdx, playIdx, nextIdx}
                : new int[]{playIdx};
        b.setStyle(new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(compact));

        return b.build();
    }

    private Notification.Action mediaAction(int icon, String label, String action) {
        Intent i = new Intent(this, KeepAliveService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(
                this, action.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build();
    }

    @Override
    public void onDestroy() {
        PlaybackBridge.detachService(this);
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }
}
