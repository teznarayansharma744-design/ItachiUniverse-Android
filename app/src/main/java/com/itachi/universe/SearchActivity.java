package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Search every lecture by title. Tapping a result opens the unit it belongs to. */
public class SearchActivity extends BaseActivity {
    private LinearLayout results;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = column();
        col.addView(UI.backHeader(this, "", "Search"));
        col.addView(UI.space(this, 16));
        EditText input = UI.input(this, "Search lectures");
        col.addView(input, UI.matchWrap());
        col.addView(UI.space(this, 16));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        col.addView(results, UI.matchWrap());
        setScreen(scroll(col));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable e) { search(e.toString().trim()); }
        });
        search("");
        input.requestFocus();
    }

    private void note(String msg) {
        TextView t = UI.tv(this, msg, 14, false, UI.dim(this));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, UI.dp(this, 30), 0, 0);
        results.addView(t, UI.matchWrap());
    }

    private void search(String q) {
        results.removeAllViews();
        if (q.length() < 2) { note("Type at least 2 letters."); return; }
        String needle = q.toLowerCase();
        int shown = 0;
        for (int bi = 0; bi < Catalog.batches.size() && shown < 50; bi++) {
            Models.Batch batch = Catalog.batches.get(bi);
            for (int si = 0; si < batch.subjects.size() && shown < 50; si++) {
                Models.Subject sub = batch.subjects.get(si);
                for (int ui = 0; ui < sub.units.size() && shown < 50; ui++) {
                    Models.UnitItem unit = sub.units.get(ui);
                    for (Models.Lecture l : unit.lectures) {
                        if (shown >= 50) break;
                        if (!l.title.toLowerCase().contains(needle)) continue;
                        shown++;
                        final int fb = bi, fs = si, fu = ui;
                        LinearLayout card = UI.card(this, 20, 14, null, v -> {
                            Intent i = new Intent(this, LecturesActivity.class);
                            i.putExtra("b", fb).putExtra("s", fs).putExtra("u", fu);
                            startActivity(i);
                        });
                        card.addView(UI.tv(this, l.title, 16, true, UI.text(this)));
                        card.addView(UI.tv(this, batch.name + " / " + sub.name + " / " + unit.name, 12, false, UI.dim(this)));
                        LinearLayout.LayoutParams lp = UI.matchWrap();
                        lp.bottomMargin = UI.dp(this, 10);
                        results.addView(card, lp);
                    }
                }
            }
        }
        if (shown == 0) note("No lecture found.");
    }
}
