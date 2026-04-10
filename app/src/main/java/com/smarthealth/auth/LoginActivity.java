package com.smarthealth.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.*;
import com.smarthealth.MainActivity;
import com.smarthealth.R;
import com.smarthealth.databinding.ActivityLoginBinding;
import com.smarthealth.models.User;
import com.smarthealth.notifications.ReminderScheduler;
import com.smarthealth.utils.FirebaseHelper;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            try {
                GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                        .getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                setLoading(false);
                // Common error codes: 12500 (Sign-in cancelled/configuration error), 10 (Developer error/SHA-1 mismatch)
                String errorMsg = "Google sign-in failed. ";
                if (e.getStatusCode() == 10) {
                    errorMsg += "Please check if your SHA-1 fingerprint is added to Firebase Console.";
                } else if (e.getStatusCode() == 12500) {
                    errorMsg += "Check your Google Play Services or network connection.";
                } else {
                    errorMsg += "Error code: " + e.getStatusCode();
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        // Google Sign-In setup
        // Ensure you have added the SHA-1 of your debug and release keys to your Firebase project settings
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        
        if (binding.btnGoogleSignIn != null) {
            binding.btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        }
        
        binding.tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        binding.tvForgotPassword.setOnClickListener(v -> sendPasswordReset());
    }

    private void signInWithGoogle() {
        setLoading(true);
        // Explicitly sign out first to ensure account picker always shows
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
            .addOnCompleteListener(task -> {
                setLoading(false);
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null && task.getResult().getAdditionalUserInfo() != null
                            && task.getResult().getAdditionalUserInfo().isNewUser()) {
                        // New Google user — create Firestore document
                        User newUser = new User(user.getUid(),
                                user.getDisplayName(), user.getEmail());
                        FirebaseHelper.getInstance().usersCollection()
                            .document(user.getUid()).set(newUser)
                            .addOnSuccessListener(v -> goToSetupOrMain(true));
                    } else {
                        // Check if existing user has completed profile
                        checkProfileCompletion(user.getUid());
                    }
                } else {
                    Toast.makeText(this, "Firebase Auth failed: " +
                        task.getException().getMessage(), Toast.LENGTH_LONG).show();
                }
            });
    }

    private void checkProfileCompletion(String uid) {
        FirebaseHelper.getInstance().usersCollection().document(uid).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists() && doc.get("bmiCategory") != null) {
                    goToSetupOrMain(false);
                } else {
                    goToSetupOrMain(true);
                }
            })
            .addOnFailureListener(e -> goToSetupOrMain(false));
    }

    private void goToSetupOrMain(boolean needsSetup) {
        ReminderScheduler.scheduleDailyReminders(this);
        if (needsSetup) {
            startActivity(new Intent(this, SetupProfileActivity.class));
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
        finishAffinity();
    }

    private void attemptLogin() {
        String email    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email))    { binding.etEmail.setError("Email is required"); return; }
        if (TextUtils.isEmpty(password)) { binding.etPassword.setError("Password is required"); return; }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                setLoading(false);
                if (task.isSuccessful()) {
                    ReminderScheduler.scheduleDailyReminders(this);
                    startActivity(new Intent(this, MainActivity.class));
                    finishAffinity();
                } else {
                    Toast.makeText(this, "Login failed: " +
                        task.getException().getMessage(), Toast.LENGTH_LONG).show();
                }
            });
    }

    private void sendPasswordReset() {
        String email = binding.etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) { binding.etEmail.setError("Enter your email first"); return; }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener(v ->
                Toast.makeText(this, "Reset email sent!", Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e ->
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        if (binding.btnGoogleSignIn != null) binding.btnGoogleSignIn.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
