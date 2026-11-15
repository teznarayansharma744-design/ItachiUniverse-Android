package com.itachi.universe;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

/** A PDF on its own screen. All the work is done by PdfPanel. */
public class PdfActivity extends BaseActivity {
    @Override protected boolean secure() { return true; }
    @Override protected int maxContentDp() { return 900; }

    private PdfPanel panel;

    private final ActivityResultLauncher<String> saver =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"),
                    uri -> { if (panel != null) panel.saveTo(uri); });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int id = getIntent().getIntExtra("id", -1);
        String title = getIntent().getStringExtra("title");
        String subtitle = getIntent().getStringExtra("subtitle");
        if (title == null) title = "PDF";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(UI.dp(this, 24), UI.dp(this, 16), UI.dp(this, 12), UI.dp(this, 8));
        head.addView(UI.backHeader(this, subtitle == null ? "" : subtitle, title), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(head, UI.matchWrap());

        panel = new PdfPanel(this, id, title, true);
        panel.setOnDownload(() -> saver.launch(panel.fileName()));
        root.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1f));
        setScreen(root);
    }

    @Override
    protected void onDestroy() {
        if (panel != null) panel.release();
        super.onDestroy();
    }
}
