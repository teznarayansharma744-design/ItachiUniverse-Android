package com.itachi.universe;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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

import java.util.Map;

/**
 * Asks the server where the video is (and whether this student may watch it), then plays it:
 * YouTube links in a WebView, everything else with ExoPlayer (Media3).
 */
@UnstableApi
public class PlayerActivity extends BaseActivity {
    private static final String YT_BASE = "https://www.youtube-nocookie.com";

    private LinearLayout rootCol;
    private FrameLayout videoHolder;
    private LinearLayout info;
    private ExoPlayer player;
    private WebView web;
    private boolean fullscreen = false;
    private int lectureId;
    private String title, subtitle;

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
                if (fullscreen) setFullscreen(false);
                else finish();
            }
        });

        load();
    }

    // ---------- loading ----------
    private void load() {
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
        player.prepare();
        player.setPlayWhenReady(true);
    }

    // ---------- YouTube ----------
    @SuppressLint({"SetJavaScriptEnabled"})
    private void startYoutube(String videoId) {
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/124.0.0.0 Mobile Safari/537.36");
        web.setWebChromeClient(new WebChromeClient());
        // Stay on the video: no link inside the page may open another page.
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                String u = r.getUrl().toString();
                return !(u.startsWith(YT_BASE) || u.equals("about:blank"));
            }
        });
        videoHolder.addView(web, new FrameLayout.LayoutParams(-1, -1));

        TextView fs = UI.tv(this, "\u26F6", 22, false, Color.WHITE);
        fs.setGravity(Gravity.CENTER);
        fs.setBackground(UI.box(0x80000000, UI.dp(this, 12), 0, 0));
        fs.setOnClickListener(v -> setFullscreen(!fullscreen));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(UI.dp(this, 40), UI.dp(this, 40), Gravity.TOP | Gravity.END);
        fp.setMargins(0, UI.dp(this, 8), UI.dp(this, 8), 0);
        videoHolder.addView(fs, fp);

        String html = "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}"
                + "#p{position:absolute;left:0;top:0;width:100%;height:100%;border:0}</style></head><body>"
                + "<div id='p'></div><script src='https://www.youtube.com/iframe_api'></script><script>"
                + "function onYouTubeIframeAPIReady(){new YT.Player('p',{videoId:'" + videoId + "',host:'" + YT_BASE + "',"
                + "playerVars:{playsinline:1,rel:0,modestbranding:1,fs:0,autoplay:1,iv_load_policy:3},"
                + "events:{onReady:function(e){e.target.playVideo();}}});}"
                + "</script></body></html>";
        web.loadDataWithBaseURL(YT_BASE, html, "text/html", "utf-8", null);
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
        if (player != null) player.pause();
        if (web != null) web.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
