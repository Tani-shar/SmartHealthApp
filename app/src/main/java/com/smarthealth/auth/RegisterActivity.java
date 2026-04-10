package com.smarthealth.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.smarthealth.R;
import com.smarthealth.databinding.ActivityRegisterBinding;
import com.smarthealth.models.User;
import com.smarthealth.utils.FirebaseHelper;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String name     = binding.etName.getText().toString().trim();
        String email    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirm  = binding.etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name))    { binding.etName.setError("Name required"); return; }
        if (TextUtils.isEmpty(email))   { binding.etEmail.setError("Email required"); return; }
        if (password.length() < 6)      { binding.etPassword.setError("Min 6 characters"); return; }
        if (!password.equals(confirm))  { binding.etConfirmPassword.setError("Passwords do not match"); return; }

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Set display name
                    UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build();
                    task.getResult().getUser().updateProfile(req);

                    // Create user document in Firestore
                    String uid = task.getResult().getUser().getUid();
                    User user = new User(uid, name, email);
                    FirebaseHelper.getInstance().usersCollection()
                        .document(uid).set(user)
                        .addOnCompleteListener(t -> {
                            setLoading(false);
                            // Send to profile setup
                            startActivity(new Intent(this, SetupProfileActivity.class));
                            finishAffinity();
                        });
                } else {
                    setLoading(false);
                    Toast.makeText(this,
                        "Registration failed: " + task.getException().getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
    }

    private void setLoading(boolean loading) {
        binding.btnRegister.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
