package com.smarthealth.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.CollectionReference;

public class FirebaseHelper {

    private static FirebaseHelper instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    private FirebaseHelper() {
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
    }

    public static synchronized FirebaseHelper getInstance() {
        if (instance == null) instance = new FirebaseHelper();
        return instance;
    }

    public FirebaseAuth getAuth() { return auth; }
    public FirebaseFirestore getDb() { return db; }

    public FirebaseUser getCurrentUser() { return auth.getCurrentUser(); }

    public String getCurrentUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    public boolean isLoggedIn() { return auth.getCurrentUser() != null; }

    // ── Collection references ──────────────────────────────────────
    public CollectionReference usersCollection() {
        return db.collection("users");
    }

    public CollectionReference bmiLogsCollection(String uid) {
        return db.collection("users").document(uid).collection("bmiLogs");
    }

    public CollectionReference mealLogsCollection(String uid) {
        return db.collection("users").document(uid).collection("mealLogs");
    }

    public CollectionReference workoutLogsCollection(String uid) {
        return db.collection("users").document(uid).collection("workoutLogs");
    }

    public CollectionReference weeklyReportsCollection(String uid) {
        return db.collection("users").document(uid).collection("weeklyReports");
    }

    public CollectionReference friendRequestsCollection() {
        return db.collection("friendRequests");
    }

    public CollectionReference activityFeedCollection(String uid) {
        return db.collection("users").document(uid).collection("activityFeed");
    }

    public CollectionReference friendsCollection(String uid) {
        return db.collection("users").document(uid).collection("friends");
    }
}
