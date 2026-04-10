package com.smarthealth;

import android.content.Intent;
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

    private ActivityMainBinding binding;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private Menu toolbarMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyDarkModePref();
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

    private void applyDarkModePref() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;
        FirebaseHelper.getInstance().usersCollection().document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc != null && doc.exists()) {
                    Boolean dark = doc.getBoolean("darkModeEnabled");
                    AppCompatDelegate.setDefaultNightMode(
                        dark != null && dark
                            ? AppCompatDelegate.MODE_NIGHT_YES
                            : AppCompatDelegate.MODE_NIGHT_NO);
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
