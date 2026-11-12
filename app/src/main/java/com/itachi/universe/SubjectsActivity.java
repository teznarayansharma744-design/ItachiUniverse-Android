package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

public class SubjectsActivity extends BaseActivity {
    static final String[] SUBJECT_ICONS = {"\u26A1", "\uD83E\uDDEA", "\uD83E\uDDEE", "\uD83C\uDF3F"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int bi = getIntent().getIntExtra("b", 0);
        if (bi >= Catalog.batches.size()) { finish(); return; }
        Models.Batch batch = Catalog.batches.get(bi);

        LinearLayout col = column();
        col.addView(UI.backHeader(this, "Batch", batch.name));
        col.addView(UI.space(this, 20));
        col.addView(UI.tv(this, "Subjects", 17, true, UI.text(this)));
        col.addView(UI.space(this, 12));

        // two cards per row
        for (int i = 0; i < batch.subjects.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                int idx = i + k;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UI.dp(this, 150), 1f);
                lp.setMargins(k == 0 ? 0 : UI.dp(this, 7), 0, k == 0 ? UI.dp(this, 7) : 0, UI.dp(this, 14));
                if (idx < batch.subjects.size()) row.addView(subjectCard(bi, idx, batch.subjects.get(idx)), lp);
                else row.addView(new View(this), lp);
            }
            col.addView(row, UI.matchWrap());
        }
        setScreen(scroll(col));
    }

    private View subjectCard(int bi, int si, Models.Subject s) {
        LinearLayout card = UI.card(this, 24, 16, new int[]{(s.tint & 0x00FFFFFF) | 0x64000000, UI.fill(this)}, v -> {
            Intent i = new Intent(this, UnitsActivity.class);
            i.putExtra("b", bi);
            i.putExtra("s", si);
            startActivity(i);
        });
        card.addView(UI.iconTile(this, SUBJECT_ICONS[si % 4], s.tint, 44));
        View spacer = new View(this);
        card.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        card.addView(UI.tv(this, s.name, 17, true, UI.text(this)));
        card.addView(UI.tv(this, s.units.size() + " units / " + s.lectures() + " lectures", 12, false, UI.dim(this)));
        return card;
    }
}
