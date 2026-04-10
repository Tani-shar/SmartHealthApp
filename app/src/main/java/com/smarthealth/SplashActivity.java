package com.smarthealth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.smarthealth.auth.LoginActivity;
import com.smarthealth.utils.FirebaseHelper;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
