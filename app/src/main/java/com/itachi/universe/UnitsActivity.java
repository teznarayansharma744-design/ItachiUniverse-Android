package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class UnitsActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int bi = getIntent().getIntExtra("b", 0), si = getIntent().getIntExtra("s", 0);
        if (bi >= Catalog.batches.size() || si >= Catalog.batches.get(bi).subjects.size()) { finish(); return; }
        Models.Batch batch = Catalog.batches.get(bi);
        Models.Subject subject = batch.subjects.get(si);

        LinearLayout col = column();
        col.addView(UI.backHeader(this, batch.name, subject.name));
        col.addView(UI.space(this, 20));
        for (int i = 0; i < subject.units.size(); i++) {
            col.addView(unitCard(bi, si, i, subject.units.get(i)));
        }
        setScreen(scroll(col));
    }

    private LinearLayout unitCard(int bi, int si, int ui, Models.UnitItem unit) {
        LinearLayout card = UI.card(this, 22, 16, null, v -> {
            Intent i = new Intent(this, LecturesActivity.class);
            i.putExtra("b", bi);
            i.putExtra("s", si);
            i.putExtra("u", ui);
            startActivity(i);
        });
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView num = UI.tv(this, String.valueOf(ui + 1), 14, true, UI.text(this));
        num.setGravity(Gravity.CENTER);
        num.setBackground(UI.box(UI.dark(this) ? 0x24FFFFFF : 0x24000000, UI.dp(this, 12), 0, 0));
        card.addView(num, UI.lp(UI.dp(this, 36), UI.dp(this, 36)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UI.tv(this, unit.name, 16, true, UI.text(this)));
        col.addView(UI.tv(this, unit.videos() + " videos / " + unit.pdfs() + " PDFs", 12, false, UI.dim(this)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = UI.dp(this, 14);
        card.addView(col, cp);

        if (Access.isDemo() && unit.freeCount() > 0) {
            card.addView(UI.tv(this, unit.freeCount() + " FREE", 11, true, UI.TEAL));
        } else if (Access.isDemo()) {
            card.addView(UI.tv(this, "\uD83D\uDD12", 15, false, UI.text(this)));
        }
        TextView arrow = UI.tv(this, "\u203A", 24, false, UI.dim(this));
        arrow.setPadding(UI.dp(this, 6), 0, 0, 0);
        card.addView(arrow);

        LinearLayout.LayoutParams lp = UI.matchWrap();
        lp.bottomMargin = UI.dp(this, 12);
        card.setLayoutParams(lp);
        return card;
    }
}
