package com.itachi.universe;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.arknox.youtube.YouTubePlayerListener;
import com.arknox.youtube.YouTubePlayerView;

import java.util.Map;

/**
 * Asks the server where the video is (and whether this student may watch it), then plays it:
 * YouTube links with the YouTubePlayerView library (Plyr), everything else with ExoPlayer (Media3).
 */
@UnstableApi
public class PlayerActivity extends BaseActivity {
    private LinearLayout rootCol;
    private FrameLayout videoHolder;
    private LinearLayout info;
    private ExoPlayer player;
    private YouTubePlayerView yt;
    private YtControls ytControls;
    private boolean fullscreen = false;
    private int lectureId;
    private String title, subtitle;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private TextView watermark;
    private boolean resumed = false;
    private float lastSavedAt = -100f;

    @Override protected boolean secure() { return true; }

    /** Saves the position every few seconds while an ExoPlayer video plays. */
    private final Runnable saver = new Runnable() {
        @Override public void run() {
            saveExoProgress();
            handler.postDelayed(this, 5000);
        }
    };

    private void saveExoProgress() {
        if (player == null) return;
        long dur = player.getDuration();
        if (dur > 0) Progress.save(this, lectureId, player.getCurrentPosition() / 1000f, dur / 1000f);
    }

    /** A faint Device ID that drifts around the video. If a recording leaks, it shows whose key it came from. */
    private void addWatermark() {
        if (watermark != null && watermark.getParent() != null) ((ViewGroup) watermark.getParent()).removeView(watermark);
        watermark = new TextView(this);
        watermark.setText(DeviceId.get(this));
        watermark.setTextColor(0x59FFFFFF);
        watermark.setTextSize(11);
        watermark.setShadowLayer(3f, 0f, 0f, 0xAA000000);
        videoHolder.addView(watermark, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        moveWatermark();
    }

    private final Runnable mover = this::moveWatermark;

    private void moveWatermark() {
        handler.removeCallbacks(mover);
        if (watermark == null || watermark.getParent() == null) return;
        int w = videoHolder.getWidth(), h = videoHolder.getHeight();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) watermark.getLayoutParams();
        lp.leftMargin = w > 200 ? (int) (Math.random() * (w - UI.dp(this, 130))) : UI.dp(this, 16);
        lp.topMargin = h > 100 ? (int) (Math.random() * (h - UI.dp(this, 40))) : UI.dp(this, 16);
        watermark.setLayoutParams(lp);
        handler.postDelayed(mover, 15000);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        lectureId = getIntent().getIntExtra("id", -1);
        title = getIntent().getStringExtra("title");
        subtitle = getIntent().getStringExtra("subtitle");
        if (title == null) title = "";
        if (subtitle == null) subtitle = "";

        rootCol = new LinearLayout(this);
        rootCol.setOrientation(LinearLayout.VERTICAL);

        videoHolder = new FrameLayout(this);
        videoHolder.setBackgroundColor(Color.BLACK);
        rootCol.addView(videoHolder);

        info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(UI.dp(this, 20), UI.dp(this, 18), UI.dp(this, 20), 0);
        info.addView(UI.tv(this, subtitle, 12, true, UI.TEAL));
        info.addView(UI.space(this, 6));
        info.addView(UI.tv(this, title, 21, true, UI.text(this)));
        rootCol.addView(info, UI.matchWrap());

        setScreen(rootCol);
        applyLayout();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (yt != null && yt.isInFullscreen()) yt.exitFullscreen();
                else if (fullscreen) setFullscreen(false);
                else finish();
            }
        });

        load();
    }

    // ---------- loading ----------
    private void load() {
        resumed = false;
        if (yt != null) { yt.release(); yt = null; }
        ytControls = null;
        videoHolder.removeAllViews();
        videoHolder.addView(new ProgressBar(this), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        run(() -> Api.playSource(lectureId, "video"), new Api.Callback<Models.PlaySource>() {
            @Override public void onSuccess(Models.PlaySource src) {
                videoHolder.removeAllViews();
                String yt = MediaLinks.youtubeId(src.url);
                if (yt != null) startYoutube(yt);
                else startExo(src);
            }
            @Override public void onError(Api.ApiException e) {
                videoHolder.removeAllViews();
                LinearLayout box = new LinearLayout(PlayerActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER);
                TextView msg = UI.tv(PlayerActivity.this, e.getMessage(), 14, false, Color.WHITE);
                msg.setGravity(Gravity.CENTER);
                msg.setPadding(UI.dp(PlayerActivity.this, 24), 0, UI.dp(PlayerActivity.this, 24), 0);
                box.addView(msg);
                box.addView(UI.textButton(PlayerActivity.this, "Try again", v -> load()));
                videoHolder.addView(box, new FrameLayout.LayoutParams(-1, -1));
            }
        });
    }

    // ---------- ExoPlayer ----------
    private void startExo(Models.PlaySource src) {
        Map<String, String> headers = src.headers;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
        PlayerView pv = new PlayerView(this);
        pv.setPlayer(player);
        pv.setControllerShowTimeoutMs(3000);
        pv.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        pv.setFullscreenButtonClickListener(isFull -> setFullscreen(isFull));
        videoHolder.addView(pv, new FrameLayout.LayoutParams(-1, -1));
        player.setMediaItem(MediaItem.fromUri(src.url));
        float from = Progress.resume(this, lectureId);
        if (from > 0) {
            player.seekTo((long) (from * 1000));
            toast("Resuming from " + Progress.clock(from));
        }
        player.prepare();
        player.setPlayWhenReady(true);
        handler.postDelayed(saver, 5000);
        addWatermark();
    }

    // ---------- YouTube ----------
    private void startYoutube(String videoId) {
        yt = new YouTubePlayerView(this);
        yt.setBackgroundColor(Color.BLACK);
        videoHolder.addView(yt, new FrameLayout.LayoutParams(-1, -1));
        yt.addListener(new YouTubePlayerListener() {
            @Override public void onPlaying(String videoId) {
                if (resumed || yt == null) return;
                resumed = true;
                float from = Progress.resume(PlayerActivity.this, lectureId);
                if (from > 0) {
                    yt.seekTo(from);
                    toast("Resuming from " + Progress.clock(from));
                }
            }
            @Override public void onTimeUpdate(float currentTime, float duration) {
                if (Math.abs(currentTime - lastSavedAt) >= 5f) {
                    lastSavedAt = currentTime;
                    Progress.save(PlayerActivity.this, lectureId, currentTime, duration);
                }
            }
            @Override public void onError(String message) {
                toast(message);
            }
        });

        // PLAYit-style controls (gestures, lock, speed, fit, fullscreen) on top of the video.
        ytControls = new YtControls(this, yt, title, new YtControls.Host() {
            @Override public boolean isFullscreen() { return fullscreen; }
            @Override public void toggleFullscreen() { setFullscreen(!fullscreen); }
            @Override public void onBack() { getOnBackPressedDispatcher().onBackPressed(); }
        });
        videoHolder.addView(ytControls, new FrameLayout.LayoutParams(-1, -1));

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        yt.loadVideo(videoId);
        addWatermark();
    }

    // ---------- fullscreen ----------
    private void setFullscreen(boolean full) {
        fullscreen = full;
        setRequestedOrientation(full ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        applyLayout();
    }

    private void applyLayout() {
        info.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        int h = fullscreen ? ViewGroup.LayoutParams.MATCH_PARENT
                : getResources().getDisplayMetrics().widthPixels * 9 / 16;
        // In portrait the width is the short side; in landscape we fill the screen anyway.
        if (!fullscreen) h = Math.min(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels) * 9 / 16;
        rootCol.removeView(videoHolder);
        rootCol.addView(videoHolder, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        holderBackground(fullscreen);

        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), rootCol);
        if (fullscreen) {
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            c.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void holderBackground(boolean full) {
        if (bg != null) bg.setBackgroundColor(full ? Color.BLACK : 0);
    }

    // ---------- lifecycle ----------
    @Override protected void onPause() {
        super.onPause();
        saveExoProgress();
        if (player != null) player.pause();
        if (yt != null) { yt.pause(); yt.onPause(); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (yt != null) yt.onResume();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        saveExoProgress();
        if (player != null) { player.release(); player = null; }
        if (yt != null) {
            YouTubePlayerView v = yt;
            yt = null;
            ytControls = null;
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent != null) parent.removeView(v);
            v.release();
        }
        super.onDestroy();
    }
}
