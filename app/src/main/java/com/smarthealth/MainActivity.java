package com.smarthealth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.smarthealth.admin.AdminDashboardActivity;
import com.smarthealth.auth.LoginActivity;
import com.smarthealth.databinding.ActivityMainBinding;
import com.smarthealth.utils.FirebaseHelper;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "smarthealth_prefs";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";

    private ActivityMainBinding binding;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private Menu toolbarMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme SYNCHRONOUSLY before super.onCreate() to prevent flicker
        applyDarkModePrefSync();
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHost.getNavController();

        // Top-level destinations (no back button on these)
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.homeFragment, R.id.bmiFragment, R.id.workoutFragment,
                R.id.caloriesFragment, R.id.progressFragment)
                .build();

        NavigationUI.setupWithNavController(binding.toolbar, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);

        checkAdminStatus();

        // Update SharedPreferences cache from Firestore (for next launch)
        syncDarkModeFromFirestore();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        this.toolbarMenu = menu;
        // Re-check status in case menu was created after checkAdminStatus finished
        checkAdminStatus();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            navController.navigate(R.id.profileFragment);
            return true;
        } else if (id == R.id.action_admin) {
            startActivity(new Intent(this, AdminDashboardActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkAdminStatus() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid).get()
            .addOnSuccessListener(doc -> {
                if (doc != null && doc.exists()) {
                    Boolean isAdmin = doc.getBoolean("isAdmin");
                    if (toolbarMenu != null && isAdmin != null && isAdmin) {
                        MenuItem adminItem = toolbarMenu.findItem(R.id.action_admin);
                        if (adminItem != null) adminItem.setVisible(true);
                    }
                }
            });
    }

    /**
     * Apply dark mode synchronously from SharedPreferences (instant, no flicker).
     */
    private void applyDarkModePrefSync() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /**
     * Fetch dark mode preference from Firestore and cache it in SharedPreferences
     * for instant access on the next launch.
     */
    private void syncDarkModeFromFirestore() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;
        FirebaseHelper.getInstance().usersCollection().document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc != null && doc.exists()) {
                    Boolean dark = doc.getBoolean("darkModeEnabled");
                    boolean isDark = dark != null && dark;

                    // Cache for next launch
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_DARK_MODE, isDark)
                            .apply();

                    // If Firestore value differs from current mode, apply it
                    int currentMode = AppCompatDelegate.getDefaultNightMode();
                    int targetMode = isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
                    if (currentMode != targetMode) {
                        AppCompatDelegate.setDefaultNightMode(targetMode);
                    }
                }
            });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    public void logout() {
        FirebaseHelper.getInstance().getAuth().signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finishAffinity();
    }
}

