package com.smarthealth.social;

import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.smarthealth.R;
import com.smarthealth.databinding.ItemFriendActivityBinding;
import com.smarthealth.models.FriendActivity;
import java.text.SimpleDateFormat;
import java.util.*;

public class FriendActivityAdapter extends RecyclerView.Adapter<FriendActivityAdapter.ViewHolder> {

    private final List<FriendActivity> items;

    public FriendActivityAdapter(List<FriendActivity> items) { this.items = items; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemFriendActivityBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFriendActivityBinding binding;

        ViewHolder(ItemFriendActivityBinding b) {
            super(b.getRoot());
            binding = b;
        }

        void bind(FriendActivity item) {
            binding.tvFriendName.setText(item.getDisplayName());
            binding.tvActivityDesc.setText(item.getDescription());
            binding.tvActivityTime.setText(new SimpleDateFormat("MMM d, h:mm a",
                Locale.getDefault()).format(new Date(item.getTimestamp())));

            // Emoji icon per type
            String icon;
            switch (item.getActivityType() != null ? item.getActivityType() : "") {
                case "meal_logged":   icon = "🍽️"; break;
                case "bmi_updated":  icon = "⚖️"; break;
                case "steps_goal":   icon = "👟"; break;
                case "workout_done": icon = "💪"; break;
                default:             icon = "🏃"; break;
            }
            binding.tvActivityIcon.setText(icon);

            // Meal photo
            if (item.getPhotoUrl() != null && !item.getPhotoUrl().isEmpty()) {
                binding.ivActivityPhoto.setVisibility(View.VISIBLE);
                Glide.with(binding.getRoot().getContext())
                    .load(item.getPhotoUrl())
                    .centerCrop()
                    .into(binding.ivActivityPhoto);
            } else {
                binding.ivActivityPhoto.setVisibility(View.GONE);
            }
        }
    }
}
