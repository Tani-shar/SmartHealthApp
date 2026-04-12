package com.smarthealth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.smarthealth.auth.LoginActivity;
import com.smarthealth.utils.FirebaseHelper;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply cached dark mode BEFORE super.onCreate() to prevent theme flicker
        SharedPreferences prefs = getSharedPreferences("smarthealth_prefs", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_mode_enabled", false);
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Wait 1.5 seconds then route to correct screen
        new Handler().postDelayed(() -> {
            if (FirebaseHelper.getInstance().isLoggedIn()) {
                startActivity(new Intent(this, MainActivity.class));
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, 1500);
    }
}

