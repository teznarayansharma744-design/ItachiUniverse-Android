package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Every lecture of one unit as a card, with "Play video" and "View PDF" buttons. */
public class LecturesActivity extends BaseActivity {
    private static final int ALL = 0, VIDEO = 1, PDF = 2;

    private Models.Subject subject;
    private Models.UnitItem unit;
    private int filter = ALL;
    private LinearLayout tabsRow, list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int bi = getIntent().getIntExtra("b", 0), si = getIntent().getIntExtra("s", 0), ui = getIntent().getIntExtra("u", 0);
        try {
            subject = Catalog.batches.get(bi).subjects.get(si);
            unit = subject.units.get(ui);
        } catch (Exception e) {
            finish();
            return;
        }

        LinearLayout col = column();
        col.addView(UI.backHeader(this, subject.name + " / Unit " + (ui + 1), unit.name));
        col.addView(UI.space(this, 20));

        tabsRow = UI.card(this, 16, 5, null, null);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(tabsRow, UI.matchWrap());
        col.addView(UI.space(this, 16));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, UI.matchWrap());

        setScreen(scroll(col));
        render();
    }

    private void render() {
        tabsRow.removeAllViews();
        String[] names = {"All", "Video", "PDF"};
        int[] counts = {unit.lectures.size(), unit.videos(), unit.pdfs()};
        for (int i = 0; i < 3; i++) {
            final int f = i;
            boolean sel = filter == i;
            TextView t = UI.tv(this, names[i] + " (" + counts[i] + ")", 14, sel, sel ? UI.text(this) : UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 10), 0, UI.dp(this, 10));
            if (sel) t.setBackground(UI.box(UI.dark(this) ? 0x28FFFFFF : 0x28000000, UI.dp(this, 12), 0, 0));
            t.setOnClickListener(v -> { filter = f; render(); });
            tabsRow.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        list.removeAllViews();
        List<Models.Lecture> items = new ArrayList<>();
        for (Models.Lecture l : unit.lectures) {
            if (filter == ALL || (filter == VIDEO && l.hasVideo) || (filter == PDF && l.hasPdf)) items.add(l);
        }
        if (items.isEmpty()) {
            TextView t = UI.tv(this, "Nothing here yet.", 14, false, UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 40), 0, 0);
            list.addView(t, UI.matchWrap());
        }
        for (Models.Lecture l : items) list.addView(lectureCard(l));
    }

    private View lectureCard(Models.Lecture l) {
        boolean locked = Access.isDemo() && !l.free;
        boolean pdfOnly = !l.hasVideo;
        LinearLayout card = UI.card(this, 22, 16, null, null);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(UI.iconTile(this, pdfOnly ? "\uD83D\uDCC4" : "\u25B6", pdfOnly ? UI.PINK : UI.INDIGO, 40));
        TextView title = UI.tv(this, l.title, 16, true, UI.text(this));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = UI.dp(this, 12);
        top.addView(title, tp);
        if (Access.isDemo() && l.free) top.addView(UI.tv(this, "FREE", 11, true, UI.TEAL));
        else if (locked) top.addView(UI.tv(this, "\uD83D\uDD12", 15, false, UI.dim(this)));
        card.addView(top, UI.matchWrap());
        card.addView(UI.space(this, 12));

        LinearLayout actions = new LinearLayout(this);
        if (l.hasVideo) {
            actions.addView(UI.pillButton(this, locked ? "\uD83D\uDD12  Play video" : "\u25B6  Play video",
                    v -> { if (locked) Access.showPremiumRequired(this); else play(l); }),
                    new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (l.hasVideo && l.hasPdf) actions.addView(UI.space(this, 1), UI.lp(UI.dp(this, 10), 1));
        if (l.hasPdf) {
            actions.addView(UI.pillButton(this, locked ? "\uD83D\uDD12  View PDF" : "\uD83D\uDCC4  View PDF",
                    v -> { if (locked) Access.showPremiumRequired(this); else openPdf(l); }),
                    new LinearLayout.LayoutParams(0, -2, 1f));
        }
        card.addView(actions, UI.matchWrap());

        LinearLayout.LayoutParams lp = UI.matchWrap();
        lp.bottomMargin = UI.dp(this, 12);
        card.setLayoutParams(lp);
        return card;
    }

    private void play(Models.Lecture l) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("id", l.id);
        i.putExtra("title", l.title);
        i.putExtra("subtitle", subject.name + " / " + unit.name);
        startActivity(i);
    }

    private void openPdf(Models.Lecture l) {
        Intent i = new Intent(this, PdfActivity.class);
        i.putExtra("id", l.id);
        i.putExtra("title", l.title);
        i.putExtra("subtitle", subject.name + " / " + unit.name);
        startActivity(i);
    }
}
