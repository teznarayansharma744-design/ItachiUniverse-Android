package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KeyActivity extends BaseActivity {
    private EditText keyInput;
    private TextView activate;
    private boolean busy = false;
    private String deviceId;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        deviceId = DeviceId.get(this);
        int dim = UI.dim(this), text = UI.text(this);
        LinearLayout col = column();

        col.addView(UI.backHeader(this, "", "Premium Key"));
        col.addView(UI.space(this, 32));
        col.addView(UI.tv(this, "Enter your key", 30, true, text));
        col.addView(UI.space(this, 8));
        col.addView(UI.tv(this, "Your key is generated for this device only. Copy the Device ID below and send it to the admin on Telegram to get your key.", 15, false, dim));
        col.addView(UI.textButton(this, "Message admin on Telegram", v -> openAdminTelegram()));
        col.addView(UI.space(this, 12));

        LinearLayout card = UI.card(this, 28, 20, null, null);
        card.addView(UI.tv(this, "Premium Key", 13, false, dim));
        card.addView(UI.space(this, 8));
        keyInput = UI.input(this, "XXXX-XXXX-XXXX-XXXX");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyInput.setLetterSpacing(0.06f);
        card.addView(keyInput, UI.matchWrap());
        card.addView(UI.space(this, 20));
        card.addView(UI.tv(this, "Your Device ID", 13, false, dim));
        card.addView(UI.space(this, 8));

        LinearLayout idRow = new LinearLayout(this);
        idRow.setGravity(Gravity.CENTER_VERTICAL);
        idRow.setPadding(UI.dp(this, 16), UI.dp(this, 8), UI.dp(this, 8), UI.dp(this, 8));
        idRow.setBackground(UI.box(UI.fill(this), UI.dp(this, 16), UI.border(this), 1));
        TextView id = UI.tv(this, deviceId, 15, true, text);
        id.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        idRow.addView(id, new LinearLayout.LayoutParams(0, -2, 1f));
        idRow.addView(UI.textButton(this, "Copy", v -> copyToClipboard(deviceId, "Device ID copied.")));
        card.addView(idRow, UI.matchWrap());
        col.addView(card, UI.matchWrap());
        col.addView(UI.space(this, 20));

        activate = UI.gradientButton(this, "Activate Premium", v -> activate());
        col.addView(activate);
        col.addView(UI.space(this, 20));

        LinearLayout info = UI.card(this, 20, 16, null, null);
        info.addView(UI.tv(this, "\uD83D\uDD12  One key works on one device only. If you switch phones, you will need a new key for the new Device ID.", 13, false, dim));
        col.addView(info, UI.matchWrap());

        setScreen(scroll(col));
    }

    private void activate() {
        if (busy) return;
        String key = keyInput.getText().toString().trim();
        if (key.isEmpty()) {
            toast("Please enter your premium key.");
            return;
        }
        busy = true;
        activate.setText("Activating...");
        run(() -> { Api.activate(key, deviceId); return null; }, new Api.Callback<Object>() {
            @Override public void onSuccess(Object r) {
                Access.premium = true;
                Intent i = new Intent(KeyActivity.this, HomeActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            }
            @Override public void onError(Api.ApiException e) {
                busy = false;
                activate.setText("Activate Premium");
                toast(e.getMessage());
            }
        });
    }
}
