package com.itachi.universe;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.arknox.youtube.YouTubePlayerListener;
import com.arknox.youtube.YouTubePlayerView;

import java.util.Locale;

/**
 * PLAYit-style controls drawn on top of the YouTube player (YouTube videos only).
 *
 * Gestures: double tap left/right = -10s/+10s, double tap centre = play/pause,
 * swipe left/right = seek, swipe up/down on the left half = brightness, on the right half = volume,
 * long press = 2x speed (until you let go), single tap = show/hide controls.
 * Buttons: back, play/pause, +-10s, seek bar, speed, fit/fill/stretch, lock, fullscreen.
 */
public class YtControls extends FrameLayout {

    public interface Host {
        boolean isFullscreen();
        void toggleFullscreen();
        void onBack();
    }

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private static final String[] FIT_NAMES = {"Fit", "Fill", "Stretch"};
    private static final int NONE = 0, SEEK = 1, BRIGHT = 2, VOL = 3;
    private static final long HIDE_MS = 3500;

    private Activity act;
    private YouTubePlayerView yt;
    private Host host;
    private AudioManager audio;
    private final Handler h = new Handler(Looper.getMainLooper());
    private GestureDetector gd;
    private int slop;
    private int maxVol;

    private FrameLayout controls;
    private TextView playBtn, curTime, durTime, speedBtn, fitBtn, fsBtn, lockBtn, unlockBtn, hud;
    private SeekBar seek;

    private boolean playing, ended, locked, shown = true, userSeeking, longPress;
    private float pos, dur;
    private int speedIdx = 2, fitMode = 0;

    // drag state
    private int mode = NONE;
    private float downX, downY, seekBase, seekTarget, levelBase;

    private Runnable autoHide;
    private Runnable hideHud;
    private Runnable hideUnlock;

    public YtControls(Activity a, YouTubePlayerView player, String title, Host host) {
        super(a);
        this.act = a;
        this.yt = player;
        this.host = host;
        this.audio = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        this.maxVol = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        this.slop = ViewConfiguration.get(a).getScaledTouchSlop();
        setClickable(true);

        autoHide = new Runnable() {
            @Override public void run() { if (playing && !userSeeking) hideControls(); }
        };
        hideHud = new Runnable() {
            @Override public void run() { hud.setVisibility(View.GONE); }
        };
        hideUnlock = new Runnable() {
            @Override public void run() { unlockBtn.setVisibility(View.GONE); }
        };

        buildUi(title == null ? "" : title);
        gd = new GestureDetector(a, new Gesture());

        yt.addListener(new YouTubePlayerListener() {
            @Override public void onPlaying(String videoId) {
                playing = true; ended = false;
                updatePlayIcon();
                scheduleHide();
            }
            @Override public void onPaused(String videoId) {
                playing = false;
                updatePlayIcon();
                if (!locked) showControls();
            }
            @Override public void onEnded(String videoId) {
                playing = false; ended = true;
                updatePlayIcon();
                if (!locked) showControls();
            }
            @Override public void onTimeUpdate(float currentTime, float duration) {
                pos = currentTime;
                dur = duration;
                if (!userSeeking && mode != SEEK) updateSeek();
            }
        });
    }

    // ------------------------------------------------------------------ UI

    private int dp(float v) { return UI.dp(getContext(), v); }

    private TextView txt(String s, float sp) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView round(String s, float sp, int sizeDp) {
        TextView t = txt(s, sp);
        t.setBackground(UI.box(0x66000000, dp(sizeDp), 0, 0));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return t;
    }

    private void buildUi(String title) {
        Context c = getContext();
        controls = new FrameLayout(c);

        // dark gradients so white text stays readable on any video
        View top = new View(c);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB0000000, 0}));
        controls.addView(top, new FrameLayout.LayoutParams(-1, dp(70), Gravity.TOP));
        View bot = new View(c);
        bot.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xC0000000, 0}));
        controls.addView(bot, new FrameLayout.LayoutParams(-1, dp(100), Gravity.BOTTOM));

        // top row: back + title
        LinearLayout topRow = new LinearLayout(c);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = txt("\u2190", 22);
        back.setOnClickListener(v -> host.onBack());
        topRow.addView(back, new LinearLayout.LayoutParams(dp(44), dp(40)));
        TextView ttl = txt(title, 14);
        ttl.setSingleLine(true);
        ttl.setEllipsize(TextUtils.TruncateAt.END);
        ttl.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        topRow.addView(ttl, new LinearLayout.LayoutParams(0, -2, 1f));
        controls.addView(topRow, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // centre: -10s, play/pause, +10s
        LinearLayout center = new LinearLayout(c);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER);
        TextView b10 = round("\u21BA 10", 12, 44);
        b10.setOnClickListener(v -> { skip(-10); scheduleHide(); });
        playBtn = round("\u25B6", 22, 56);
        playBtn.setOnClickListener(v -> { togglePlay(); scheduleHide(); });
        TextView f10 = round("10 \u21BB", 12, 44);
        f10.setOnClickListener(v -> { skip(10); scheduleHide(); });
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(dp(44), dp(44));
        m.setMargins(dp(18), 0, dp(18), 0);
        center.addView(b10, m);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(56), dp(56));
        center.addView(playBtn, mp);
        LinearLayout.LayoutParams m2 = new LinearLayout.LayoutParams(dp(44), dp(44));
        m2.setMargins(dp(18), 0, dp(18), 0);
        center.addView(f10, m2);
        controls.addView(center, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        // lock button (left, middle)
        lockBtn = round("\uD83D\uDD12", 15, 40);
        lockBtn.setOnClickListener(v -> lock());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START | Gravity.CENTER_VERTICAL);
        lp.setMargins(dp(10), 0, 0, 0);
        controls.addView(lockBtn, lp);

        // bottom: time + seek bar, then speed / fit / fullscreen
        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(10), 0, dp(10), dp(2));

        LinearLayout row1 = new LinearLayout(c);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        curTime = txt("00:00", 12);
        durTime = txt("--:--", 12);
        seek = new SeekBar(c);
        seek.setMax(1000);
        seek.setProgressTintList(ColorStateList.valueOf(UI.TEAL));
        seek.setThumbTintList(ColorStateList.valueOf(UI.TEAL));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && dur > 0) curTime.setText(fmt(p / 1000f * dur));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
                userSeeking = true;
                h.removeCallbacks(autoHide);
            }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                if (dur > 0) {
                    float t = sb.getProgress() / 1000f * dur;
                    yt.seekTo(t);
                    pos = t;
                }
                scheduleHide();
            }
        });
        row1.addView(curTime, new LinearLayout.LayoutParams(dp(46), dp(32)));
        row1.addView(seek, new LinearLayout.LayoutParams(0, dp(32), 1f));
        row1.addView(durTime, new LinearLayout.LayoutParams(dp(46), dp(32)));
        bottom.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = new LinearLayout(c);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        speedBtn = txt("1x", 12);
        speedBtn.setBackground(UI.box(0x66000000, dp(14), 0, 0));
        speedBtn.setOnClickListener(v -> cycleSpeed());
        fitBtn = txt(FIT_NAMES[0], 12);
        fitBtn.setBackground(UI.box(0x66000000, dp(14), 0, 0));
        fitBtn.setOnClickListener(v -> cycleFit());
        fsBtn = txt("\u26F6", 18);
        fsBtn.setOnClickListener(v -> { host.toggleFullscreen(); scheduleHide(); });
        LinearLayout.LayoutParams sp1 = new LinearLayout.LayoutParams(dp(52), dp(28));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(dp(64), dp(28));
        sp2.setMargins(dp(8), 0, 0, 0);
        row2.addView(speedBtn, sp1);
        row2.addView(fitBtn, sp2);
        row2.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        row2.addView(fsBtn, new LinearLayout.LayoutParams(dp(40), dp(32)));
        bottom.addView(row2, new LinearLayout.LayoutParams(-1, -2));
        controls.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        addView(controls, new FrameLayout.LayoutParams(-1, -1));

        // shown only while the screen is locked (after a tap)
        unlockBtn = round("\uD83D\uDD13", 15, 40);
        unlockBtn.setVisibility(View.GONE);
        unlockBtn.setOnClickListener(v -> unlock());
        FrameLayout.LayoutParams up = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START | Gravity.CENTER_VERTICAL);
        up.setMargins(dp(10), 0, 0, 0);
        addView(unlockBtn, up);

        // feedback pill for gestures
        hud = txt("", 14);
        hud.setPadding(dp(16), dp(8), dp(16), dp(8));
        hud.setBackground(UI.box(0xB0000000, dp(18), 0, 0));
        hud.setVisibility(View.GONE);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hp.setMargins(0, dp(44), 0, 0);
        addView(hud, hp);
    }

    // ------------------------------------------------------------------ gestures

    private class Gesture extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(MotionEvent e) { return true; }

        @Override public boolean onSingleTapConfirmed(MotionEvent e) {
            if (locked) showUnlock();
            else if (shown) hideControls();
            else showControls();
            return true;
        }

        @Override public boolean onDoubleTap(MotionEvent e) {
            if (locked) return true;
            float x = e.getX() / Math.max(1, getWidth());
            if (x < 0.4f) skip(-10);
            else if (x > 0.6f) skip(10);
            else togglePlay();
            return true;
        }

        @Override public void onLongPress(MotionEvent e) {
            if (locked || mode != NONE) return;
            longPress = true;
            yt.setSpeed(2f);
            showHud("2x  \u25B6\u25B6", 0);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        gd.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                mode = NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!locked && !longPress) drag(e);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endGesture();
                break;
            default:
                break;
        }
        return true;
    }

    private void drag(MotionEvent e) {
        float dx = e.getX() - downX, dy = e.getY() - downY;
        if (mode == NONE) {
            if (Math.abs(dx) < slop * 2 && Math.abs(dy) < slop * 2) return;
            if (Math.abs(dx) > Math.abs(dy)) {
                if (dur <= 0) return;
                mode = SEEK;
                seekBase = pos;
                seekTarget = pos;
            } else if (downX < getWidth() / 2f) {
                mode = BRIGHT;
                levelBase = getBrightness();
            } else {
                mode = VOL;
                levelBase = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) maxVol;
            }
        }
        float range = Math.max(1, getHeight() * 0.8f);
        switch (mode) {
            case SEEK:
                seekTarget = clamp(seekBase + dx / Math.max(1, getWidth()) * 120f, 0f, dur);
                float diff = seekTarget - seekBase;
                showHud(fmt(seekTarget) + " / " + fmt(dur) + "   (" + (diff >= 0 ? "+" : "-")
                        + Math.abs((int) diff) + "s)", 0);
                break;
            case BRIGHT: {
                float v = clamp(levelBase - dy / range, 0.02f, 1f);
                setBrightness(v);
                showHud("\u2600  Brightness " + Math.round(v * 100) + "%", 0);
                break;
            }
            case VOL: {
                float v = clamp(levelBase - dy / range, 0f, 1f);
                int vol = Math.round(v * maxVol);
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                showHud("Volume " + Math.round(100f * vol / maxVol) + "%", 0);
                break;
            }
            default:
                break;
        }
    }

    private void endGesture() {
        if (mode == SEEK && dur > 0) {
            yt.seekTo(seekTarget);
            pos = seekTarget;
            updateSeek();
        }
        if (longPress) {
            longPress = false;
            yt.setSpeed(SPEEDS[speedIdx]);
        }
        if (hud.getVisibility() == View.VISIBLE) {
            h.removeCallbacks(hideHud);
            h.postDelayed(hideHud, 500);
        }
        mode = NONE;
    }

    // ------------------------------------------------------------------ actions

    private void togglePlay() {
        if (ended) {
            ended = false;
            playing = true;
            yt.seekTo(0);
            yt.play();
        } else if (playing) {
            playing = false;
            yt.pause();
        } else {
            playing = true;
            yt.play();
        }
        updatePlayIcon();
    }

    private void skip(int sec) {
        float t = pos + sec;
        t = dur > 0 ? clamp(t, 0f, dur) : Math.max(0f, t);
        yt.seekTo(t);
        pos = t;
        updateSeek();
        showHud(sec > 0 ? "\u25B6\u25B6  +" + sec + "s" : "\u25C0\u25C0  " + sec + "s", 600);
    }

    private void cycleSpeed() {
        speedIdx = (speedIdx + 1) % SPEEDS.length;
        float s = SPEEDS[speedIdx];
        yt.setSpeed(s);
        speedBtn.setText(speedLabel(s));
        showHud("Speed " + speedLabel(s), 700);
        scheduleHide();
    }

    private void cycleFit() {
        fitMode = (fitMode + 1) % FIT_NAMES.length;
        yt.setFitMode(fitMode);
        fitBtn.setText(FIT_NAMES[fitMode]);
        showHud(FIT_NAMES[fitMode], 700);
        scheduleHide();
    }

    private void lock() {
        locked = true;
        hideControls();
        showHud("Screen locked", 900);
        showUnlock();
    }

    private void unlock() {
        locked = false;
        h.removeCallbacks(hideUnlock);
        unlockBtn.setVisibility(View.GONE);
        showHud("Screen unlocked", 700);
        showControls();
    }

    private void showUnlock() {
        unlockBtn.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideUnlock);
        h.postDelayed(hideUnlock, 2500);
    }

    private void showControls() {
        shown = true;
        controls.setVisibility(View.VISIBLE);
        scheduleHide();
    }

    private void hideControls() {
        shown = false;
        controls.setVisibility(View.GONE);
        h.removeCallbacks(autoHide);
    }

    private void scheduleHide() {
        h.removeCallbacks(autoHide);
        if (shown && playing && !userSeeking) h.postDelayed(autoHide, HIDE_MS);
    }

    private void showHud(String text, long hideAfterMs) {
        hud.setText(text);
        hud.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideHud);
        if (hideAfterMs > 0) h.postDelayed(hideHud, hideAfterMs);
    }

    // ------------------------------------------------------------------ state -> views

    private void updatePlayIcon() {
        playBtn.setText(ended ? "\u21BA" : (playing ? "\u275A\u275A" : "\u25B6"));
    }

    private void updateSeek() {
        curTime.setText(fmt(pos));
        durTime.setText(dur > 0 ? fmt(dur) : "--:--");
        seek.setProgress(dur > 0 ? (int) (clamp(pos / dur, 0f, 1f) * 1000) : 0);
    }

    // ------------------------------------------------------------------ brightness

    private float getBrightness() {
        WindowManager.LayoutParams lp = act.getWindow().getAttributes();
        if (lp.screenBrightness >= 0) return lp.screenBrightness;
        try {
            return Settings.System.getInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    private void setBrightness(float v) {
        WindowManager.LayoutParams lp = act.getWindow().getAttributes();
        lp.screenBrightness = v;
        act.getWindow().setAttributes(lp);
    }

    // ------------------------------------------------------------------ helpers

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String speedLabel(float s) {
        return (s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + "x";
    }

    private static String fmt(float seconds) {
        int t = Math.max(0, (int) seconds);
        int hh = t / 3600, mm = (t % 3600) / 60, ss = t % 60;
        return hh > 0 ? String.format(Locale.US, "%d:%02d:%02d", hh, mm, ss)
                : String.format(Locale.US, "%02d:%02d", mm, ss);
    }

    @Override protected void onDetachedFromWindow() {
        h.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
