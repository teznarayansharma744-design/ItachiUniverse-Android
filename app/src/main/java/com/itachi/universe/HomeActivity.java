package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends BaseActivity {
    private final View[] tabs = new View[3];
    private final TextView[] navItems = new TextView[3];
    private final List<LinearLayout> batchLists = new ArrayList<>();
    private final List<SwipeRefreshLayout> refreshers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        tabs[0] = refreshable(homeTab());
        tabs[1] = refreshable(libraryTab());
        tabs[2] = settingsTab();
        for (View t : tabs) content.addView(t, new FrameLayout.LayoutParams(-1, -1));

        // bottom navigation
        LinearLayout nav = UI.card(this, 24, 8, null, null);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        String[] icons = {"\u2302", "\u25A6", "\u2699"};
        String[] labels = {"Home", "Library", "Settings"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = UI.tv(this, icons[i] + "\n" + labels[i], 11, true, UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(UI.dp(this, 18), UI.dp(this, 8), UI.dp(this, 18), UI.dp(this, 8));
            t.setOnClickListener(v -> select(idx));
            navItems[i] = t;
            nav.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        np.setMargins(UI.dp(this, 24), 0, UI.dp(this, 24), UI.dp(this, 16));
        root.addView(nav, np);

        setScreen(root);
        select(0);
        Catalog.load(this::refresh);
        Updater.check(this, false);
    }

    private void select(int idx) {
        for (int i = 0; i < 3; i++) {
            tabs[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            boolean sel = i == idx;
            navItems[i].setTextColor(sel ? UI.text(this) : UI.dim(this));
            navItems[i].setBackground(sel ? UI.box(UI.dark(this) ? 0x1EFFFFFF : 0x1E000000, UI.dp(this, 16), 0, 0) : null);
        }
    }

    private SwipeRefreshLayout refreshable(View child) {
        SwipeRefreshLayout sr = new SwipeRefreshLayout(this);
        sr.addView(child);
        sr.setOnRefreshListener(() -> Catalog.load(this::refresh));
        refreshers.add(sr);
        return sr;
    }

    private LinearLayout tabColumn() {
        LinearLayout col = column();
        col.setPadding(UI.dp(this, 24), UI.dp(this, 24), UI.dp(this, 24), UI.dp(this, 120));
        return col;
    }

    private ScrollView tabScroll(LinearLayout col) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    // ---------- Home ----------
    private View homeTab() {
        LinearLayout col = tabColumn();
        int dim = UI.dim(this), text = UI.text(this);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UI.tv(this, "Welcome back", 13, false, dim));
        titles.addView(UI.tv(this, "Itachi Universe", 24, true, text));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView chip = UI.tv(this, Access.isDemo() ? "DEMO" : "PREMIUM", 11, true, text);
        chip.setLetterSpacing(0.1f);
        chip.setPadding(UI.dp(this, 12), UI.dp(this, 7), UI.dp(this, 12), UI.dp(this, 7));
        chip.setBackground(UI.box(0x665B5BFF, UI.dp(this, 999), UI.border(this), 1));
        header.addView(chip);
        col.addView(header, UI.matchWrap());
        col.addView(UI.space(this, 20));

        if (Access.isDemo()) {
            LinearLayout banner = UI.card(this, 24, 16, new int[]{0x595B5BFF, 0x2E19C3B1},
                    v -> startActivity(new Intent(this, KeyActivity.class)));
            banner.addView(UI.tv(this, "You are in Demo Mode", 15, true, text));
            banner.addView(UI.space(this, 4));
            banner.addView(UI.tv(this, "Only free lectures are unlocked. Tap to enter your premium key.", 13, false, dim));
            col.addView(banner, UI.matchWrap());
            col.addView(UI.space(this, 24));
        }

        col.addView(UI.tv(this, "Your batches", 17, true, text));
        col.addView(UI.space(this, 12));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        batchLists.add(list);
        col.addView(list, UI.matchWrap());
        return tabScroll(col);
    }

    // ---------- Library ----------
    private View libraryTab() {
        LinearLayout col = tabColumn();
        col.addView(UI.tv(this, "Library", 28, true, UI.text(this)));
        col.addView(UI.space(this, 20));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        batchLists.add(list);
        col.addView(list, UI.matchWrap());
        return tabScroll(col);
    }

    /** Spinner while loading, a retry card on error, otherwise one tile per batch. */
    private void refresh() {
        if (!alive()) return;
        for (SwipeRefreshLayout sr : refreshers) sr.setRefreshing(Catalog.loading && !Catalog.batches.isEmpty());
        for (LinearLayout list : batchLists) {
            list.removeAllViews();
            if (Catalog.loading && Catalog.batches.isEmpty()) {
                ProgressBar pb = new ProgressBar(this);
                LinearLayout.LayoutParams pp = UI.lp(-2, -2);
                pp.gravity = Gravity.CENTER_HORIZONTAL;
                pp.topMargin = UI.dp(this, 40);
                list.addView(pb, pp);
            } else if (Catalog.error != null && Catalog.batches.isEmpty()) {
                LinearLayout c = UI.card(this, 22, 16, null, null);
                c.addView(UI.tv(this, Catalog.error, 14, false, UI.dim(this)));
                c.addView(UI.textButton(this, "Try again", v -> Catalog.load(this::refresh)));
                list.addView(c, UI.matchWrap());
            } else if (Catalog.batches.isEmpty()) {
                list.addView(UI.tv(this, "No batches yet.", 14, false, UI.dim(this)));
            } else {
                for (int i = 0; i < Catalog.batches.size(); i++) list.addView(batchTile(i));
            }
        }
    }

    private View batchTile(int index) {
        Models.Batch batch = Catalog.batches.get(index);
        LinearLayout card = UI.card(this, 22, 16, null, v -> {
            Intent i = new Intent(this, SubjectsActivity.class);
            i.putExtra("b", index);
            startActivity(i);
        });
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(UI.iconTile(this, index % 2 == 0 ? "\uD83D\uDCDA" : "\u26A1", batch.tint, 48));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UI.tv(this, batch.name, 16, true, UI.text(this)));
        col.addView(UI.tv(this, batch.subjects.size() + " subjects / " + batch.lectures() + " lectures", 12, false, UI.dim(this)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = UI.dp(this, 14);
        card.addView(col, cp);
        card.addView(UI.tv(this, "\u203A", 24, false, UI.dim(this)));
        LinearLayout.LayoutParams lp = UI.matchWrap();
        lp.bottomMargin = UI.dp(this, 12);
        card.setLayoutParams(lp);
        return card;
    }

    // ---------- Settings ----------
    private View settingsTab() {
        LinearLayout col = tabColumn();
        int dim = UI.dim(this), text = UI.text(this);
        col.addView(UI.tv(this, "Settings", 28, true, text));
        col.addView(UI.space(this, 20));

        LinearLayout dark = UI.card(this, 22, 18, null, null);
        dark.setOrientation(LinearLayout.HORIZONTAL);
        dark.setGravity(Gravity.CENTER_VERTICAL);
        dark.addView(UI.tv(this, "Dark mode", 16, true, text), new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(UI.dark(this));
        sw.setOnCheckedChangeListener((v, checked) -> { if (checked != UI.dark(this)) UI.toggleTheme(this); });
        dark.addView(sw);
        col.addView(dark, UI.matchWrap());
        col.addView(UI.space(this, 12));

        LinearLayout dev = UI.card(this, 22, 18, null, null);
        dev.setOrientation(LinearLayout.HORIZONTAL);
        dev.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout devText = new LinearLayout(this);
        devText.setOrientation(LinearLayout.VERTICAL);
        devText.addView(UI.tv(this, "Device ID", 13, false, dim));
        TextView id = UI.tv(this, DeviceId.get(this), 16, true, text);
        id.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        devText.addView(id);
        dev.addView(devText, new LinearLayout.LayoutParams(0, -2, 1f));
        dev.addView(UI.textButton(this, "Copy", v -> copyToClipboard(DeviceId.get(this), "Device ID copied.")));
        col.addView(dev, UI.matchWrap());
        col.addView(UI.space(this, 12));

        LinearLayout upd = UI.card(this, 22, 18, null, v -> Updater.check(this, true));
        upd.addView(UI.tv(this, "Check for updates", 16, true, text));
        upd.addView(UI.tv(this, "Current version " + BuildConfig.VERSION_NAME, 13, false, dim));
        col.addView(upd, UI.matchWrap());
        col.addView(UI.space(this, 12));

        LinearLayout logout = UI.card(this, 22, 18, null, v -> confirmLogout());
        logout.addView(UI.tv(this, "Log out from this device", 16, true, text));
        col.addView(logout, UI.matchWrap());
        col.addView(UI.space(this, 24));

        TextView ver = UI.tv(this, "Itachi Universe  v" + BuildConfig.VERSION_NAME, 12, false, dim);
        ver.setGravity(Gravity.CENTER);
        col.addView(ver, UI.matchWrap());
        return tabScroll(col);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log out?")
                .setMessage("This removes premium from this phone. Your key stays valid, so you can enter it again on this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (d, w) -> {
                    Api.clearPremium();
                    Access.premium = false;
                    Intent i = new Intent(this, WelcomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .show();
    }
}
