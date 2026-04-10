package com.smarthealth.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.smarthealth.databinding.ActivityAdminDashboardBinding;
import com.smarthealth.models.User;
import com.smarthealth.utils.FirebaseHelper;
import java.util.ArrayList;
import java.util.List;

public class AdminDashboardActivity extends AppCompatActivity {

    private ActivityAdminDashboardBinding binding;
    private AdminUserAdapter adapter;
    private final List<User> userList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Admin Dashboard");
        }

        // Verify admin access first
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) { finish(); return; }

        FirebaseHelper.getInstance().usersCollection().document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc == null || !doc.exists()) { finish(); return; }
                Boolean isAdmin = doc.getBoolean("isAdmin");
                if (isAdmin == null || !isAdmin) {
                    Toast.makeText(this, "Access denied: Admin only", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                setupDashboard();
            })
            .addOnFailureListener(e -> finish());
    }

    private void setupDashboard() {
        adapter = new AdminUserAdapter(userList, this::showDeleteConfirmation);
        binding.recyclerUsers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerUsers.setAdapter(adapter);

        binding.progressBar.setVisibility(View.VISIBLE);
        loadAllUsers();
    }

    private void showDeleteConfirmation(User user) {
        new AlertDialog.Builder(this)
            .setTitle("Delete User Profile?")
            .setMessage("Are you sure you want to delete the profile for " + user.getDisplayName() + "?\n\nThis will remove all their health data from the database. This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> deleteUserProfile(user))
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    private void deleteUserProfile(User user) {
        // 1. Delete user document from 'users' collection
        FirebaseHelper.getInstance().usersCollection().document(user.getUid())
            .delete()
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "User profile deleted successfully", Toast.LENGTH_SHORT).show();
                // Note: To fully delete a user's subcollections and Auth account, 
                // a Cloud Function or Admin SDK would be needed. 
                // This removes the primary profile data.
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Error deleting user: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void loadAllUsers() {
        FirebaseHelper.getInstance().usersCollection()
            .orderBy("createdAt")
            .addSnapshotListener((snapshots, e) -> {
                if (!isFinishing() && binding != null) {
                    binding.progressBar.setVisibility(View.GONE);
                }
                if (snapshots == null) return;

                userList.clear();
                int totalUsers = snapshots.size();
                int[] activeToday = {0};

                long oneDayAgo = System.currentTimeMillis() - 86_400_000L;

                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                    User user = doc.toObject(User.class);
                    if (user != null) {
                        user.setUid(doc.getId());
                        userList.add(user);
                        if (user.getCreatedAt() > oneDayAgo) activeToday[0]++;
                    }
                }

                if (adapter != null) adapter.notifyDataSetChanged();

                if (binding != null) {
                    binding.tvTotalUsers.setText(String.valueOf(totalUsers));
                    binding.tvActiveToday.setText(String.valueOf(activeToday[0]));

                    // BMI category breakdown
                    long obese = userList.stream()
                        .filter(u -> u.getBmiCategory() != null && u.getBmiCategory().contains("Obese"))
                        .count();
                    long overweight = userList.stream()
                        .filter(u -> "Overweight".equals(u.getBmiCategory()))
                        .count();
                    long healthy = userList.stream()
                        .filter(u -> "Normal weight".equals(u.getBmiCategory()))
                        .count();

                    binding.tvBmiBreakdown.setText(
                        "Healthy: " + healthy + "  |  Overweight: " + overweight + "  |  Obese: " + obese);
                }
            });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
