package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends BaseActivity {
    private View[] tabs;
    private PillTabs nav;
    private static final int[] NAV_ICONS = {R.drawable.ic_home, R.drawable.ic_library, R.drawable.ic_settings, R.drawable.ic_info};
    private final List<LinearLayout> batchLists = new ArrayList<>();
    private final List<SwipeRefreshLayout> refreshers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        final boolean admin = Api.hasAdminSession();   // owners get an extra Admin tab
        tabs = new View[admin ? 5 : 4];
        tabs[0] = refreshable(homeTab());
        tabs[1] = refreshable(libraryTab());
        tabs[2] = settingsTab();
        tabs[3] = aboutTab();
        if (admin) tabs[4] = adminTab();
        for (View t : tabs) content.addView(t, new FrameLayout.LayoutParams(-1, -1));

        // bottom navigation: the chosen tab grows into a pill
        String[] labels = admin ? new String[]{"Home", "Library", "Settings", "About", "Admin"}
                : new String[]{"Home", "Library", "Settings", "About"};
        int[] icons = admin ? new int[]{NAV_ICONS[0], NAV_ICONS[1], NAV_ICONS[2], NAV_ICONS[3], R.drawable.ic_shield} : NAV_ICONS;
        nav = new PillTabs(this, icons, labels, this::select);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        np.setMargins(UI.dp(this, 20), 0, UI.dp(this, 20), UI.dp(this, 16));
        root.addView(nav, np);

        setScreen(root);
        nav.select(0, false);
        select(0);
        Catalog.load(this::refresh);
        Updater.check(this, false);
    }

    private void select(int idx) {
        for (int i = 0; i < tabs.length; i++) {
            boolean sel = i == idx;
            boolean was = tabs[i].getVisibility() == View.VISIBLE;
            tabs[i].setVisibility(sel ? View.VISIBLE : View.GONE);
            if (sel && !was) {   // the new page fades and rises in
                tabs[i].setAlpha(0f);
                tabs[i].setTranslationY(UI.dp(this, 14));
                tabs[i].animate().alpha(1f).translationY(0f).setDuration(380).setInterpolator(Motion.SMOOTH).start();
            }
        }
        if (nav != null) nav.select(idx, true);
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
        Motion.riseOnFirstDraw(col);
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
        chip.setBackground(UI.box(0x66A3122A, UI.dp(this, 999), UI.border(this), 1));
        header.addView(chip);
        col.addView(header, UI.matchWrap());
        col.addView(UI.space(this, 20));

        LinearLayout searchCard = UI.card(this, 20, 14, null, v -> startActivity(new Intent(this, SearchActivity.class)));
        searchCard.setOrientation(LinearLayout.HORIZONTAL);
        searchCard.setGravity(Gravity.CENTER_VERTICAL);
        searchCard.addView(UI.iconView(this, R.drawable.ic_search, 20, dim));
        TextView sl = UI.tv(this, "Search lectures", 15, false, dim);
        sl.setPadding(UI.dp(this, 12), 0, 0, 0);
        searchCard.addView(sl);
        col.addView(searchCard, UI.matchWrap());
        col.addView(UI.space(this, 16));

        announceBox = new LinearLayout(this);
        announceBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(announceBox, UI.matchWrap());
        loadAnnouncement();

        if (Access.isDemo()) {
            LinearLayout banner = UI.card(this, 24, 16, new int[]{0x59A3122A, 0x2EE63950},
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
        card.addView(UI.iconTile(this, index % 2 == 0 ? R.drawable.ic_book : R.drawable.ic_bolt, batch.tint, 48));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UI.tv(this, batch.name, 16, true, UI.text(this)));
        col.addView(UI.tv(this, batch.subjects.size() + " subjects / " + batch.lectures() + " lectures", 12, false, UI.dim(this)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = UI.dp(this, 14);
        card.addView(col, cp);
        card.addView(UI.iconView(this, R.drawable.ic_chevron, 20, UI.dim(this)));
        LinearLayout.LayoutParams lp = UI.matchWrap();
        lp.bottomMargin = UI.dp(this, 12);
        card.setLayoutParams(lp);
        return card;
    }

    // ---------- Notice banner ----------
    private LinearLayout announceBox;

    private void loadAnnouncement() {
        run(Api::announce, new Api.Callback<com.google.gson.JsonObject>() {
            @Override public void onSuccess(com.google.gson.JsonObject d) {
                announceBox.removeAllViews();
                String text = Models.str(d, "text"), link = Models.str(d, "link");
                if (text.isEmpty()) return;
                android.content.SharedPreferences prefs = getSharedPreferences("ui", MODE_PRIVATE);
                if (String.valueOf(text.hashCode()).equals(prefs.getString("ann_dismissed", ""))) return;
                LinearLayout card = UI.card(HomeActivity.this, 22, 16, new int[]{0x59FFB020, 0x2EFF6B6B}, null);
                LinearLayout row = new LinearLayout(HomeActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(UI.tv(HomeActivity.this, text, 14, true, UI.text(HomeActivity.this)),
                        new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(UI.textButton(HomeActivity.this, "Dismiss", v -> {
                    prefs.edit().putString("ann_dismissed", String.valueOf(text.hashCode())).apply();
                    announceBox.removeAllViews();
                }));
                card.addView(row);
                if (!link.isEmpty()) {
                    card.addView(UI.textButton(HomeActivity.this, "Open", v -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)));
                        } catch (Exception e) { toast("Could not open this link."); }
                    }));
                }
                announceBox.addView(card, UI.matchWrap());
                announceBox.addView(UI.space(HomeActivity.this, 16));
            }
            @Override public void onError(Api.ApiException e) { /* no banner if the server is asleep */ }
        });
    }

    // ---------- About developer ----------
    private LinearLayout aboutBox;

    // ---------- Admin (only after the owner typed the admin code on the key screen) ----------
    private View adminTab() {
        LinearLayout col = tabColumn();
        col.addView(UI.tv(this, "Admin", 28, true, UI.text(this)));
        col.addView(UI.space(this, 8));
        col.addView(UI.tv(this, "Manage keys, content and the announcement banner.", 14, false, UI.dim(this)));
        col.addView(UI.space(this, 20));
        col.addView(UI.gradientButton(this, "Open admin panel", v -> startActivity(new Intent(this, AdminPanelActivity.class))));
        col.addView(UI.space(this, 14));
        LinearLayout out = UI.card(this, 22, 18, null, v -> new GlassDialog.Builder(this)
                .setTitle("Log out of admin?")
                .setMessage("The Admin tab disappears until you enter the admin code again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (d, w) -> {
                    Api.adminLogout();
                    Intent i = new Intent(this, HomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                }).show());
        out.addView(UI.tv(this, "Log out of admin", 16, true, UI.text(this)));
        col.addView(out, UI.matchWrap());
        return tabScroll(col);
    }

    private View aboutTab() {
        LinearLayout col = tabColumn();
        col.addView(UI.tv(this, "About Developer", 28, true, UI.text(this)));
        col.addView(UI.space(this, 20));
        aboutBox = new LinearLayout(this);
        aboutBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(aboutBox, UI.matchWrap());
        loadAbout();
        return tabScroll(col);
    }

    private void loadAbout() {
        aboutBox.removeAllViews();
        aboutBox.addView(new ProgressBar(this));
        run(Api::about, new Api.Callback<com.google.gson.JsonObject>() {
            @Override public void onSuccess(com.google.gson.JsonObject d) { AboutUi.render(HomeActivity.this, aboutBox, d); }
            @Override public void onError(Api.ApiException e) {
                aboutBox.removeAllViews();
                LinearLayout c = UI.card(HomeActivity.this, 22, 16, null, null);
                c.addView(UI.tv(HomeActivity.this, e.getMessage(), 14, false, UI.dim(HomeActivity.this)));
                c.addView(UI.textButton(HomeActivity.this, "Try again", v -> loadAbout()));
                aboutBox.addView(c, UI.matchWrap());
            }
        });
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

        LinearLayout dl = UI.card(this, 22, 18, null, v -> startActivity(new Intent(this, DownloadsActivity.class)));
        dl.setOrientation(LinearLayout.HORIZONTAL);
        dl.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout dlText = new LinearLayout(this);
        dlText.setOrientation(LinearLayout.VERTICAL);
        dlText.addView(UI.tv(this, "Downloads", 16, true, text));
        dlText.addView(UI.tv(this, "Lectures saved for offline watching", 13, false, dim));
        dl.addView(dlText, new LinearLayout.LayoutParams(0, -2, 1f));
        dl.addView(UI.iconView(this, R.drawable.ic_chevron, 20, dim));
        if (!Access.isDemo()) {   // downloads are a premium feature
            col.addView(dl, UI.matchWrap());
            col.addView(UI.space(this, 12));
        }

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
        new GlassDialog.Builder(this)
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
