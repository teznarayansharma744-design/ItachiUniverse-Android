package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AdminLoginActivity extends BaseActivity {
    private EditText url, password;
    private TextView error, button;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Api.hasAdminSession()) {
            startActivity(new Intent(this, AdminPanelActivity.class));
            finish();
            return;
        }
        int dim = UI.dim(this), text = UI.text(this);
        LinearLayout col = column();
        col.addView(UI.backHeader(this, "", "Admin"));
        col.addView(UI.space(this, 32));
        col.addView(UI.tv(this, "Admin login", 30, true, text));
        col.addView(UI.space(this, 8));
        col.addView(UI.tv(this, "Only for the owner. Generate and manage student keys here.", 15, false, dim));
        col.addView(UI.space(this, 24));

        LinearLayout card = UI.card(this, 28, 20, null, null);
        card.addView(UI.tv(this, "Server address", 13, false, dim));
        card.addView(UI.space(this, 8));
        url = UI.input(this, "https://your-app.onrender.com");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(Api.baseUrl());
        card.addView(url, UI.matchWrap());
        card.addView(UI.space(this, 16));
        card.addView(UI.tv(this, "Password", 13, false, dim));
        card.addView(UI.space(this, 8));
        password = UI.input(this, "Admin password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(password, UI.matchWrap());
        error = UI.tv(this, "", 13, false, 0xFFFF6B6B);
        card.addView(error);
        col.addView(card, UI.matchWrap());
        col.addView(UI.space(this, 20));

        button = UI.gradientButton(this, "Log in", v -> login());
        col.addView(button);
        setScreen(scroll(col));
    }

    private void login() {
        if (busy) return;
        String pw = password.getText().toString();
        if (pw.isEmpty()) { error.setText("Enter the admin password."); return; }
        error.setText("");
        busy = true;
        button.setText("Logging in...");
        Api.setBaseUrl(url.getText().toString());
        run(() -> { Api.adminLogin(pw); return null; }, new Api.Callback<Object>() {
            @Override public void onSuccess(Object r) {
                startActivity(new Intent(AdminLoginActivity.this, AdminPanelActivity.class));
                finish();
            }
            @Override public void onError(Api.ApiException e) {
                busy = false;
                button.setText("Log in");
                error.setText(e.getMessage());
            }
        });
    }
}
