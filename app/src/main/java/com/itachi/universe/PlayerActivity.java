package com.itachi.universe;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ProgressBar;
import android.widget.TextView;

import android.content.res.Configuration;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.arknox.youtube.YouTubePlayerListener;
import com.arknox.youtube.YouTubePlayerView;

import java.util.ArrayList;
import java.util.List;
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
    private boolean hasPdf;
    private PdfPanel pdfPanel;
    private final ActivityResultLauncher<String> pdfSaver =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"),
                    uri -> { if (pdfPanel != null) pdfPanel.saveTo(uri); });

    // the other lectures in this folder, shown under the video
    private int[] ids;
    private String[] titles;
    private boolean[] frees, pdfFlags;
    private int index;
    private TextView titleView;
    private LinearLayout tabBar, listCol, markCol;
    private FrameLayout content;
    private ScrollView listScroll, markScroll;
    private static final int T_LECT = 0, T_MARK = 1, T_PDF = 2;
    private int tab = T_LECT;
    private final List<Integer> tabIds = new ArrayList<>();
    private FrameLayout nextOverlay;
    private int countdown;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (nextOverlay == null) return;
            countdown--;
            if (countdown <= 0) { switchTo(index + 1); return; }
            updateCountdown();
            handler.postDelayed(this, 1000);
        }
    };
    private int lectureId;
    private String title, subtitle;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private TextView watermark;
    private boolean resumed = false;
    private float lastSavedAt = -100f;

    @Override protected boolean secure() { return true; }
    @Override protected int maxContentDp() { return 0; }

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
        hasPdf = getIntent().getBooleanExtra("pdf", false);

        ids = getIntent().getIntArrayExtra("ids");
        titles = getIntent().getStringArrayExtra("titles");
        frees = getIntent().getBooleanArrayExtra("frees");
        pdfFlags = getIntent().getBooleanArrayExtra("pdfs");
        if (ids == null || titles == null || frees == null || pdfFlags == null
                || ids.length != titles.length || ids.length != frees.length || ids.length != pdfFlags.length) {
            ids = new int[]{lectureId};
            titles = new String[]{title};
            frees = new boolean[]{true};
            pdfFlags = new boolean[]{hasPdf};
        }
        index = 0;
        for (int k = 0; k < ids.length; k++) if (ids[k] == lectureId) index = k;
        tab = ids.length > 1 ? T_LECT : T_MARK;

        rootCol = new LinearLayout(this);
        rootCol.setOrientation(LinearLayout.VERTICAL);

        videoHolder = new FrameLayout(this);
        videoHolder.setBackgroundColor(Color.BLACK);
        rootCol.addView(videoHolder);

        info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(UI.dp(this, 20), UI.dp(this, 14), UI.dp(this, 20), 0);
        info.addView(UI.tv(this, subtitle, 12, true, UI.TEAL));
        info.addView(UI.space(this, 4));
        titleView = UI.tv(this, title, 20, true, UI.text(this));
        info.addView(titleView);
        rootCol.addView(info, UI.matchWrap());

        tabBar = new LinearLayout(this);
        tabBar.setPadding(UI.dp(this, 16), UI.dp(this, 12), UI.dp(this, 16), UI.dp(this, 8));
        rootCol.addView(tabBar, UI.matchWrap());

        content = new FrameLayout(this);
        listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(UI.dp(this, 16), UI.dp(this, 4), UI.dp(this, 16), UI.dp(this, 24));
        listScroll.addView(listCol);
        markScroll = new ScrollView(this);
        markScroll.setVerticalScrollBarEnabled(false);
        markCol = new LinearLayout(this);
        markCol.setOrientation(LinearLayout.VERTICAL);
        markCol.setPadding(UI.dp(this, 16), UI.dp(this, 4), UI.dp(this, 16), UI.dp(this, 24));
        markScroll.addView(markCol);
        content.addView(listScroll, new FrameLayout.LayoutParams(-1, -1));
        content.addView(markScroll, new FrameLayout.LayoutParams(-1, -1));
        rootCol.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        renderPlaylist();
        renderBookmarks();
        buildTabs();

        setScreen(rootCol);
        watchRotation();
        applyLayout();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (panel != null) closePlaylistPanel();
                else if (yt != null && yt.isInFullscreen()) yt.exitFullscreen();
                else if (fullscreen || landscape()) setFullscreen(false);
                else finish();
            }
        });

        load();
    }

    // ---------- the area under the video: lectures, bookmarks, PDF ----------
    private void buildTabs() {
        tabIds.clear();
        if (ids.length > 1) tabIds.add(T_LECT);
        tabIds.add(T_MARK);
        if (hasPdf) tabIds.add(T_PDF);
        if (!tabIds.contains(tab)) tab = tabIds.get(0);
        tabBar.removeAllViews();
        int n = tabIds.size();
        int[] ic = new int[n];
        String[] names = new String[n];
        for (int k = 0; k < n; k++) {
            int id = tabIds.get(k);
            ic[k] = id == T_LECT ? R.drawable.ic_library : id == T_MARK ? R.drawable.ic_bookmark : R.drawable.ic_pdf;
            names[k] = id == T_LECT ? "Lectures " + ids.length
                    : id == T_MARK ? "Bookmarks" + (Bookmarks.count(this, lectureId) > 0 ? " " + Bookmarks.count(this, lectureId) : "")
                    : "PDF";
        }
        PillTabs pt = new PillTabs(this, ic, names, k -> { tab = tabIds.get(k); showTab(); });
        pt.select(tabIds.indexOf(tab), false);
        tabBar.addView(pt, new LinearLayout.LayoutParams(-1, -2));
        showTab();
    }

    private void showTab() {
        View shown = tab == T_LECT ? listScroll : tab == T_MARK ? markScroll : pdfPanel;
        listScroll.setVisibility(tab == T_LECT ? View.VISIBLE : View.GONE);
        markScroll.setVisibility(tab == T_MARK ? View.VISIBLE : View.GONE);
        if (tab == T_PDF && hasPdf && pdfPanel == null) {
            pdfPanel = new PdfPanel(this, lectureId, title, true);
            pdfPanel.setSubtitle(subtitle);
            pdfPanel.setOnDownload(() -> pdfSaver.launch(pdfPanel.fileName()));
            content.addView(pdfPanel, new FrameLayout.LayoutParams(-1, -1));
        }
        if (pdfPanel != null) pdfPanel.setVisibility(tab == T_PDF ? View.VISIBLE : View.GONE);
        if (tab == T_PDF) shown = pdfPanel;
        if (shown != null) {
            shown.setAlpha(0f);
            shown.setTranslationY(UI.dp(this, 12));
            shown.animate().alpha(1f).translationY(0f).setDuration(340).setInterpolator(Motion.SMOOTH).start();
        }
    }

    private void disposePdf() {
        if (pdfPanel == null) return;
        pdfPanel.release();
        content.removeView(pdfPanel);
        pdfPanel = null;
    }

    // ---------- landscape playlist panel ----------
    private FrameLayout panel;

    /** Slides a list of every lecture in this folder in from the right, over the video. */
    private void showPlaylistPanel() {
        if (panel != null) return;
        final FrameLayout overlay = new FrameLayout(this);
        panel = overlay;
        View scrim = new View(this);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(v -> closePlaylistPanel());
        overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(new GlassDrawable(this, UI.dp(this, 22), 0, 1.4f));
        card.setPadding(UI.dp(this, 14), UI.dp(this, 14), UI.dp(this, 14), UI.dp(this, 10));
        card.setClickable(true);
        TextView head = UI.tv(this, "Playlist", 16, true, UI.text(this));
        card.addView(head);
        if (!subtitle.isEmpty()) card.addView(UI.tv(this, subtitle, 11, true, UI.TEAL));
        card.addView(UI.space(this, 8));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        View current = null;
        for (int k = 0; k < ids.length; k++) {
            final int at = k;
            boolean now = k == index;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(UI.dp(this, 10), UI.dp(this, 9), UI.dp(this, 10), UI.dp(this, 9));
            row.setBackground(UI.box(now ? 0x3319C3B1 : (UI.dark(this) ? 0x14FFFFFF : 0x12000000), UI.dp(this, 14), 0, 0));
            if (now) row.addView(UI.iconView(this, R.drawable.ic_play, 16, UI.TEAL), UI.lp(UI.dp(this, 26), UI.dp(this, 16)));
            else {
                TextView num = UI.tv(this, String.valueOf(k + 1), 13, true, UI.dim(this));
                num.setGravity(Gravity.CENTER);
                row.addView(num, UI.lp(UI.dp(this, 26), -2));
            }
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t = UI.tv(this, titles[k], 14, true, UI.text(this));
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(t);
            if (!subtitle.isEmpty()) {
                TextView sub = UI.tv(this, subtitle, 10, false, UI.dim(this));
                sub.setSingleLine(true);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                col.addView(sub);
            }
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            if (lockedAt(k)) row.addView(UI.iconView(this, R.drawable.ic_lock, 14, UI.dim(this)));
            else if (Progress.watched(this, ids[k])) row.addView(UI.iconView(this, R.drawable.ic_check, 14, UI.TEAL));
            row.setOnClickListener(v -> { closePlaylistPanel(); if (at != index) switchTo(at); });
            Motion.press(row);
            LinearLayout.LayoutParams lp = UI.matchWrap();
            lp.bottomMargin = UI.dp(this, 6);
            list.addView(row, lp);
            if (now) current = row;
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        card.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        int w = Math.min(UI.dp(this, 340), (int) (getResources().getDisplayMetrics().widthPixels * 0.5f));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(w, -1, Gravity.END);
        cp.setMargins(0, UI.dp(this, 8), UI.dp(this, 8), UI.dp(this, 8));
        overlay.addView(card, cp);
        videoHolder.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(260).start();
        card.setTranslationX(w);
        card.animate().translationX(0f).setDuration(420).setInterpolator(Motion.SMOOTH).start();
        final View cur = current;
        if (cur != null) sv.post(() -> sv.scrollTo(0, Math.max(0, cur.getTop() - UI.dp(this, 40))));
    }

    private void closePlaylistPanel() {
        final FrameLayout p = panel;
        if (p == null) return;
        panel = null;
        p.animate().alpha(0f).setDuration(200).withEndAction(() -> videoHolder.removeView(p)).start();
    }

    private boolean autoNext() { return getSharedPreferences("player", MODE_PRIVATE).getBoolean("autonext", true); }

    private boolean lockedAt(int k) { return Access.isDemo() && !frees[k]; }

    private void renderPlaylist() {
        listCol.removeAllViews();
        if (ids.length < 2) return;
        TextView auto = UI.tv(this, "", 13, true, UI.TEAL);
        auto.setPadding(UI.dp(this, 4), UI.dp(this, 4), UI.dp(this, 4), UI.dp(this, 10));
        Runnable paint = () -> auto.setText(autoNext() ? "Auto-play next lecture: ON  (tap to turn off)" : "Auto-play next lecture: OFF  (tap to turn on)");
        paint.run();
        auto.setOnClickListener(v -> {
            getSharedPreferences("player", MODE_PRIVATE).edit().putBoolean("autonext", !autoNext()).apply();
            paint.run();
        });
        listCol.addView(auto);
        View current = null;
        for (int k = 0; k < ids.length; k++) {
            final int at = k;
            boolean now = k == index;
            LinearLayout row = UI.card(this, 16, 12, now ? new int[]{(UI.TEAL & 0x00FFFFFF) | 0x59000000} : null,
                    v -> { if (at != index) switchTo(at); });
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (now) row.addView(UI.iconView(this, R.drawable.ic_play, 18, UI.TEAL), UI.lp(UI.dp(this, 30), UI.dp(this, 18)));
            else {
                TextView num = UI.tv(this, String.valueOf(k + 1), 14, true, UI.dim(this));
                num.setGravity(Gravity.CENTER);
                row.addView(num, UI.lp(UI.dp(this, 30), -2));
            }
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(UI.tv(this, titles[k], 15, true, UI.text(this)));
            if (!subtitle.isEmpty()) col.addView(UI.tv(this, subtitle, 11, false, UI.dim(this)));
            if (now) col.addView(UI.tv(this, "Now playing", 11, true, UI.TEAL));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            if (lockedAt(k)) row.addView(UI.iconView(this, R.drawable.ic_lock, 16, UI.dim(this)));
            else if (Progress.watched(this, ids[k])) row.addView(UI.withIcon(UI.tv(this, "Watched", 11, true, UI.TEAL), R.drawable.ic_check, 13));
            else if (Progress.percent(this, ids[k]) > 0) row.addView(UI.tv(this, Progress.percent(this, ids[k]) + "%", 11, true, UI.dim(this)));
            LinearLayout.LayoutParams lp = UI.matchWrap();
            lp.bottomMargin = UI.dp(this, 8);
            listCol.addView(row, lp);
            if (now) current = row;
        }
        final View cur = current;
        if (cur != null) listScroll.post(() -> listScroll.scrollTo(0, Math.max(0, cur.getTop() - UI.dp(this, 70))));
    }

    private void renderBookmarks() {
        markCol.removeAllViews();
        markCol.addView(UI.iconPill(this, R.drawable.ic_bookmark, "Bookmark this moment", v -> addBookmark()), UI.matchWrap());
        markCol.addView(UI.space(this, 12));
        List<Bookmarks.Mark> marks = Bookmarks.list(this, lectureId);
        if (marks.isEmpty()) {
            TextView t = UI.tv(this, "No bookmarks yet.\nTap the button while the video plays to mark a moment.", 13, false, UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 24), 0, 0);
            markCol.addView(t, UI.matchWrap());
            return;
        }
        for (int k = 0; k < marks.size(); k++) {
            final int at = k;
            final Bookmarks.Mark m = marks.get(k);
            LinearLayout row = UI.card(this, 16, 12, null, v -> {
                if (ytControls == null) { toast("The video is still loading."); return; }
                ytControls.seekToSeconds(m.t);
            });
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView time = UI.tv(this, Progress.clock(m.t), 13, true, UI.TEAL);
            time.setBackground(UI.box(0x2219C3B1, UI.dp(this, 999), 0, 0));
            time.setPadding(UI.dp(this, 10), UI.dp(this, 5), UI.dp(this, 10), UI.dp(this, 5));
            row.addView(time);
            TextView label = UI.tv(this, m.label.isEmpty() ? "Bookmark" : m.label, 14, false, UI.text(this));
            LinearLayout.LayoutParams lp0 = new LinearLayout.LayoutParams(0, -2, 1f);
            lp0.leftMargin = UI.dp(this, 12);
            row.addView(label, lp0);
            android.widget.ImageView del = UI.iconView(this, R.drawable.ic_trash, 18, UI.dim(this));
            del.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            del.setLayoutParams(UI.lp(UI.dp(this, 40), UI.dp(this, 36)));
            del.setOnClickListener(v -> { Bookmarks.remove(this, lectureId, at); renderBookmarks(); buildTabs(); });
            row.addView(del);
            LinearLayout.LayoutParams lp = UI.matchWrap();
            lp.bottomMargin = UI.dp(this, 8);
            markCol.addView(row, lp);
        }
    }

    private void addBookmark() {
        if (ytControls == null) { toast("Start the video first."); return; }
        final float t = ytControls.currentSeconds();
        final EditText e = UI.input(this, "Note (optional)");
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(e, UI.matchWrap());
        new GlassDialog.Builder(this)
                .setTitle("Bookmark at " + Progress.clock(t))
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    Bookmarks.add(this, lectureId, t, e.getText().toString().trim());
                    renderBookmarks();
                    buildTabs();
                    toast("Bookmark saved.");
                }).show();
    }

    // ---------- switching lecture, auto-play next ----------
    private void switchTo(int k) {
        if (k < 0 || k >= ids.length) return;
        if (lockedAt(k)) { Access.showPremiumRequired(this); return; }
        saveExoProgress();
        cancelAutoNext();
        index = k;
        lectureId = ids[k];
        title = titles[k];
        hasPdf = pdfFlags[k];
        lastSavedAt = -100f;
        titleView.setText(title);
        disposePdf();
        if (tab == T_PDF) tab = ids.length > 1 ? T_LECT : T_MARK;
        renderPlaylist();
        renderBookmarks();
        buildTabs();
        load();
    }

    private void cancelAutoNext() {
        handler.removeCallbacks(ticker);
        if (nextOverlay != null && nextOverlay.getParent() != null) ((ViewGroup) nextOverlay.getParent()).removeView(nextOverlay);
        nextOverlay = null;
    }

    private TextView countdownText;

    private void updateCountdown() {
        if (countdownText != null) countdownText.setText("Playing in " + countdown + "...");
    }

    /** The video finished: count down 5 seconds, then play the next lecture of the folder. */
    private void onVideoEnded() {
        if (ytControls != null) {
            float d = ytControls.durationSeconds();
            if (d > 0) Progress.save(this, lectureId, d, d);
        }
        renderPlaylist();
        int next = index + 1;
        if (!autoNext() || next >= ids.length || lockedAt(next) || nextOverlay != null) return;

        nextOverlay = new FrameLayout(this);
        nextOverlay.setBackgroundColor(0xCC000000);
        nextOverlay.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(UI.dp(this, 20), UI.dp(this, 8), UI.dp(this, 20), UI.dp(this, 8));
        TextView up = UI.tv(this, "UP NEXT", 11, true, UI.TEAL);
        up.setGravity(Gravity.CENTER);
        col.addView(up);
        TextView nt = UI.tv(this, titles[next], 17, true, Color.WHITE);
        nt.setGravity(Gravity.CENTER);
        nt.setMaxLines(2);
        col.addView(nt);
        countdown = 5;
        countdownText = UI.tv(this, "", 13, false, 0xCCFFFFFF);
        countdownText.setGravity(Gravity.CENTER);
        col.addView(countdownText);
        updateCountdown();
        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.CENTER);
        TextView cancel = UI.pillButton(this, "Cancel", v -> cancelAutoNext());
        TextView now = UI.iconPill(this, R.drawable.ic_play, "Play now", v -> switchTo(index + 1));
        btns.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        btns.addView(UI.space(this, 1), UI.lp(UI.dp(this, 10), 1));
        btns.addView(now, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = UI.dp(this, 12);
        col.addView(btns, bp);
        nextOverlay.addView(col, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        videoHolder.addView(nextOverlay, new FrameLayout.LayoutParams(-1, -1));
        handler.postDelayed(ticker, 1000);
    }

    // ---------- loading ----------
    private void load() {
        resumed = false;
        handler.removeCallbacks(saver);
        if (player != null) { player.release(); player = null; }
        if (yt != null) { yt.release(); yt = null; }
        ytControls = null;
        panel = null;
        videoHolder.removeAllViews();
        videoHolder.addView(new ProgressBar(this), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        if (Downloads.has(this, lectureId)) {  // saved on this phone: plays with no internet
            videoHolder.removeAllViews();
            startExo(new Models.PlaySource(android.net.Uri.fromFile(Downloads.file(this, lectureId)).toString(), java.util.Collections.emptyMap()));
            return;
        }
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
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)))
                .build();
        PlayerView pv = new PlayerView(this);
        pv.setPlayer(player);
        pv.setUseController(false);
        pv.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        pv.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        videoHolder.addView(pv, new FrameLayout.LayoutParams(-1, -1));
        ytControls = new YtControls(this, player, pv, title, new YtControls.Host() {
            @Override public boolean isFullscreen() { return fullscreen || landscape(); }
            @Override public void toggleFullscreen() { setFullscreen(!(fullscreen || landscape())); }
            @Override public void onBack() { getOnBackPressedDispatcher().onBackPressed(); }
        });
        videoHolder.addView(ytControls, new FrameLayout.LayoutParams(-1, -1));
        ytControls.setOnEnded(this::onVideoEnded);
        ytControls.setNeighbours(index > 0, index < ids.length - 1, () -> switchTo(index - 1), () -> switchTo(index + 1));
        ytControls.setOnPlaylist(ids.length > 1 ? this::showPlaylistPanel : null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
            @Override public boolean isFullscreen() { return fullscreen || landscape(); }
            @Override public void toggleFullscreen() { setFullscreen(!(fullscreen || landscape())); }
            @Override public void onBack() { getOnBackPressedDispatcher().onBackPressed(); }
        });
        videoHolder.addView(ytControls, new FrameLayout.LayoutParams(-1, -1));
        ytControls.setOnEnded(this::onVideoEnded);
        ytControls.setNeighbours(index > 0, index < ids.length - 1, () -> switchTo(index - 1), () -> switchTo(index + 1));
        ytControls.setOnPlaylist(ids.length > 1 ? this::showPlaylistPanel : null);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        yt.loadVideo(videoId);
        addWatermark();
    }

    // ---------- fullscreen ----------
    private OrientationEventListener oel;
    private int forcedLandscape = -1;   // -1 = nothing forced, 1 = forced landscape, 0 = forced portrait

    /** The fullscreen button: turn the screen the way asked, then hand control back to the phone's
     *  auto-rotate as soon as the phone is actually held that way. */
    private void setFullscreen(boolean full) {
        fullscreen = full;
        forcedLandscape = full ? 1 : 0;
        setRequestedOrientation(full ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        applyLayout();
    }

    private void watchRotation() {
        // follow the phone's auto-rotate setting: held sideways -> landscape, upright -> portrait
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        oel = new OrientationEventListener(this) {
            @Override public void onOrientationChanged(int deg) {
                if (forcedLandscape < 0 || deg == ORIENTATION_UNKNOWN) return;
                boolean sideways = (deg > 60 && deg < 120) || (deg > 240 && deg < 300);
                boolean upright = deg < 30 || deg > 330 || (deg > 150 && deg < 210);
                if ((forcedLandscape == 1 && sideways) || (forcedLandscape == 0 && upright)) {
                    forcedLandscape = -1;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                }
            }
        };
        if (oel.canDetectOrientation()) oel.enable();
    }

    private boolean landscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        fullscreen = landscape();   // whichever way the phone ended up decides the layout
        applyLayout();  // phone rotated or window resized: refit everything
    }

    /** Sizes the video to whatever screen this is: 16:9 across the width in portrait (never taller
     *  than 45% of the screen, so the PDF keeps room), full screen in landscape. */
    private void applyLayout() {
        boolean full = fullscreen || landscape();
        info.setVisibility(full ? View.GONE : View.VISIBLE);
        tabBar.setVisibility(full ? View.GONE : View.VISIBLE);
        content.setVisibility(full ? View.GONE : View.VISIBLE);
        int w = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        int h = full ? ViewGroup.LayoutParams.MATCH_PARENT : Math.min(w * 9 / 16, (int) (sh * 0.45f));
        rootCol.removeView(videoHolder);
        rootCol.addView(videoHolder, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        holderBackground(full);

        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), rootCol);
        if (full) {
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
        if (oel != null) oel.disable();
        if (pdfPanel != null) pdfPanel.release();
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
