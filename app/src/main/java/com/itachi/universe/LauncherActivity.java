package com.itachi.universe;

import android.content.Intent;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import android.os.Bundle;

/** First screen. A phone that already has a premium key goes straight to Home, otherwise to Welcome. */
public class LauncherActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout f = new FrameLayout(this);
        f.addView(new ProgressBar(this), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setScreen(f);

        if (!Api.hasPremiumToken()) {
            go(false);   // (the owner's phone is sent to Home by go(): the Admin tab lives there)
            return;
        }
        // false = server says the key is revoked/expired; null = could not tell (trust the saved key)
        run(() -> Api.checkPremium(), new Api.Callback<Boolean>() {
            @Override public void onSuccess(Boolean ok) { go(ok == null || ok); }
            @Override public void onError(Api.ApiException e) { go(true); }
        });
    }

    private void go(boolean premium) {
        Access.premium = premium;
        boolean home = premium || Api.hasAdminSession();   // an unlocked admin never goes back to the welcome screen
        startActivity(new Intent(this, home ? HomeActivity.class : WelcomeActivity.class));
        finish();
    }
}
