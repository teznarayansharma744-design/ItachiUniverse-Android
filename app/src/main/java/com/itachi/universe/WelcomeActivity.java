package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WelcomeActivity extends BaseActivity {
    private int taps = 0;
    private long lastTap = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = column();
        int dim = UI.dim(this), text = UI.text(this);

        // theme switch
        TextView theme = UI.tv(this, UI.dark(this) ? "\u2600" : "\u263E", 22, false, text);
        theme.setGravity(Gravity.CENTER);
        theme.setOnClickListener(v -> UI.toggleTheme(this));
        LinearLayout.LayoutParams tp = UI.lp(UI.dp(this, 44), UI.dp(this, 44));
        tp.gravity = Gravity.END;
        col.addView(theme, tp);
        col.addView(UI.space(this, 20));

        // logo: tap 7 times quickly to open the hidden admin login
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setClipToOutline(true);
        logo.setBackground(UI.box(0, UI.dp(this, 18), 0, 0));
        logo.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        logo.setOnClickListener(v -> secretTap());
        col.addView(logo, UI.lp(UI.dp(this, 64), UI.dp(this, 64)));
        col.addView(UI.space(this, 16));

        col.addView(UI.tv(this, "Itachi Universe", 30, true, text));
        col.addView(UI.space(this, 8));
        col.addView(UI.tv(this, "Learn at your own pace. Subject-wise videos and PDFs, all in one place.", 16, false, dim));
        col.addView(UI.space(this, 36));

        col.addView(option("\u25B6", "Demo Mode", "Browse everything. Only free lectures play.", "DEMO", false, v -> {
            Access.premium = false;
            startActivity(new Intent(this, HomeActivity.class));
        }));
        col.addView(UI.space(this, 16));
        col.addView(option("\uD83D\uDD11", "Enter Premium Key", "Enter your key to unlock the full course", null, true,
                v -> startActivity(new Intent(this, KeyActivity.class))));
        col.addView(UI.space(this, 16));
        col.addView(option("\u2B50", "Get Premium", "Contact us on Telegram to get a key", null, false,
                v -> openAdminTelegram()));
        col.addView(UI.space(this, 28));
        TextView foot = UI.tv(this, "One key, one device. Your key works only on your phone.", 12, false, dim);
        foot.setGravity(Gravity.CENTER);
        col.addView(foot, UI.matchWrap());

        setScreen(scroll(col));
    }

    private void secretTap() {
        long now = System.currentTimeMillis();
        if (now - lastTap > 2000) taps = 0;
        lastTap = now;
        if (++taps >= 7) {
            taps = 0;
            startActivity(new Intent(this, AdminLoginActivity.class));
        }
    }

    private View option(String icon, String title, String subtitle, String badge, boolean highlight, View.OnClickListener click) {
        int[] grad = highlight ? new int[]{0xFF4B4BF0, 0xFF2E9BE8} : null;
        LinearLayout card = UI.card(this, 24, 18, grad, click);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int fg = highlight ? 0xFFFFFFFF : UI.text(this);
        int fgDim = highlight ? 0xE6FFFFFF : UI.dim(this);

        TextView tile = UI.tv(this, icon, 20, false, fg);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(UI.box((fg & 0x00FFFFFF) | 0x24000000, UI.dp(this, 16), 0, 0));
        card.addView(tile, UI.lp(UI.dp(this, 48), UI.dp(this, 48)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UI.tv(this, title, 17, true, fg));
        col.addView(UI.tv(this, subtitle, 13, false, fgDim));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = UI.dp(this, 16);
        card.addView(col, cp);

        if (badge != null) {
            TextView t = UI.tv(this, badge, 11, true, 0xFF0B9E8F);
            t.setPadding(UI.dp(this, 10), UI.dp(this, 5), UI.dp(this, 10), UI.dp(this, 5));
            t.setBackground(UI.box(0x3819C3B1, UI.dp(this, 999), 0, 0));
            card.addView(t);
        } else {
            card.addView(UI.tv(this, "\u203A", 24, false, fgDim));
        }
        card.setLayoutParams(UI.matchWrap());
        return card;
    }
}
