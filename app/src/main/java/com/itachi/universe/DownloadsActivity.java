package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Lectures saved on this phone. They play without internet and are invisible to other apps. */
public class DownloadsActivity extends BaseActivity {
    private LinearLayout list;
    private TextView summary;

    @Override protected boolean secure() { return true; }

    private final Runnable refresh = () -> { if (alive()) render(); };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = column();
        col.addView(UI.backHeader(this, "Offline", "Downloads"));
        col.addView(UI.space(this, 8));
        summary = UI.tv(this, "", 13, false, UI.dim(this));
        summary.setPadding(UI.dp(this, 56), 0, 0, 0);
        col.addView(summary);
        col.addView(UI.space(this, 16));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, UI.matchWrap());
        setScreen(scroll(col));
        render();
    }

    @Override protected void onStart() { super.onStart(); Downloads.addListener(refresh); }
    @Override protected void onStop() { super.onStop(); Downloads.removeListener(refresh); }

    private static String mb(long bytes) {
        double m = bytes / 1048576.0;
        return m >= 1024 ? String.format(Locale.US, "%.2f GB", m / 1024) : String.format(Locale.US, "%.0f MB", m);
    }

    private void render() {
        list.removeAllViews();
        List<Downloads.Item> items = Downloads.list(this);
        summary.setText(items.isEmpty() ? "" : items.size() + (items.size() == 1 ? " lecture" : " lectures") + " · " + mb(Downloads.totalBytes(this)) + " on this phone");
        if (items.isEmpty()) {
            TextView t = UI.tv(this, "Nothing downloaded yet.\nOpen a lecture and tap Download to watch it offline.", 14, false, UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 48), 0, 0);
            list.addView(t, UI.matchWrap());
            return;
        }
        for (Downloads.Item it : items) {
            LinearLayout card = UI.card(this, 22, 16, null, null);
            card.addView(UI.tv(this, it.subtitle, 12, true, UI.TEAL));
            card.addView(UI.tv(this, it.title, 17, true, UI.text(this)));
            card.addView(UI.tv(this, mb(it.size), 12, false, UI.dim(this)));
            card.addView(UI.space(this, 12));
            LinearLayout row = new LinearLayout(this);
            row.addView(UI.pillButton(this, "▷  Play", v -> {
                Intent i = new Intent(this, PlayerActivity.class);
                i.putExtra("id", it.id);
                i.putExtra("title", it.title);
                i.putExtra("subtitle", it.subtitle);
                startActivity(i);
            }), new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(UI.space(this, 1), UI.lp(UI.dp(this, 10), 1));
            row.addView(UI.pillButton(this, "⌫  Remove", v -> new GlassDialog.Builder(this)
                    .setTitle("Remove this download?")
                    .setMessage("You can download it again any time.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("⌫  Remove", (d, w) -> Downloads.delete(this, it.id))
                    .show()), new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(row, UI.matchWrap());
            LinearLayout.LayoutParams lp = UI.matchWrap();
            lp.bottomMargin = UI.dp(this, 12);
            list.addView(card, lp);
        }
    }
}
