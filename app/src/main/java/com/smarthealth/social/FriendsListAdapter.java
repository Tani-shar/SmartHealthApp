package com.smarthealth.social;

import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.smarthealth.databinding.ItemFriendBinding;
import com.smarthealth.utils.FirebaseHelper;
import java.text.SimpleDateFormat;
import java.util.*;

public class FriendsListAdapter extends RecyclerView.Adapter<FriendsListAdapter.ViewHolder> {

    public interface OnUnfriendListener { void onUnfriend(String friendUid, String friendName); }

    private final List<Map<String, Object>> friends;
    private final String todayDate;
    private final OnUnfriendListener unfriendListener;

    public FriendsListAdapter(List<Map<String, Object>> friends, OnUnfriendListener listener) {
        this.friends = friends;
        this.unfriendListener = listener;
        this.todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemFriendBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> friend = friends.get(position);
        String name  = friend.get("name")  != null ? friend.get("name").toString()  : "Unknown";
        String email = friend.get("email") != null ? friend.get("email").toString() : "";
        String uid   = friend.get("uid")   != null ? friend.get("uid").toString()   : null;

        holder.binding.tvFriendName.setText(name);
        holder.binding.tvFriendEmail.setText(email);

        if (uid != null) {
            fetchFriendStats(uid, holder);
            holder.binding.btnUnfriend.setOnClickListener(v -> unfriendListener.onUnfriend(uid, name));
        } else {
            holder.binding.tvFriendStats.setText("Stats unavailable");
        }
    }

    private void fetchFriendStats(String friendUid, ViewHolder holder) {
        FirebaseHelper.getInstance().usersCollection().document(friendUid).get()
            .addOnSuccessListener(doc -> {
                if (doc == null || !doc.exists()) return;
                
                Long steps = doc.getLong("stepsToday_" + todayDate);
                int stepsCount = (steps != null) ? steps.intValue() : 0;
                
                FirebaseHelper.getInstance().mealLogsCollection(friendUid)
                    .whereEqualTo("date", todayDate)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        int totalCals = 0;
                        for (DocumentSnapshot mealDoc : querySnapshot) {
                            Long cal = mealDoc.getLong("calories");
                            if (cal != null) totalCals += cal.intValue();
                        }
                        
                        holder.binding.tvFriendStats.setText(String.format(Locale.getDefault(),
                            "Today: %d steps • %d kcal", stepsCount, totalCals));
                        holder.binding.tvFriendStats.setVisibility(View.VISIBLE);
                    });
            });
    }

    @Override
    public int getItemCount() { return friends.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemFriendBinding binding;
        ViewHolder(ItemFriendBinding b) {
            super(b.getRoot());
            binding = b;
        }
    }
}
