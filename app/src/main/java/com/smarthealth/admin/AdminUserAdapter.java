package com.smarthealth.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smarthealth.R;
import com.smarthealth.models.User;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder> {

    public interface OnUserDeleteListener {
        void onUserDelete(User user);
    }

    private final List<User> users;
    private final OnUserDeleteListener deleteListener;

    public AdminUserAdapter(List<User> users, OnUserDeleteListener listener) {
        this.users = users;
        this.deleteListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_user, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);
        holder.tvName.setText(user.getDisplayName() != null ? user.getDisplayName() : "Unknown");
        holder.tvEmail.setText(user.getEmail() != null ? user.getEmail() : "");
        holder.tvBmi.setText(user.getBmiCurrent() > 0
            ? String.format(Locale.getDefault(), "BMI: %.1f (%s)",
                user.getBmiCurrent(), user.getBmiCategory())
            : "BMI: Not set");
        holder.tvGoal.setText("Goal: " + (user.getFitnessGoal() != null
            ? user.getFitnessGoal().replace("_", " ") : "Not set"));
        holder.tvCalories.setText("Calorie Target: " + user.getDailyCalorieTarget() + " kcal");

        String joined = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(new Date(user.getCreatedAt()));
        holder.tvJoined.setText("Joined: " + joined);

        if (user.isAdmin()) {
            holder.tvAdminBadge.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.GONE); // Can't delete other admins for safety
        } else {
            holder.tvAdminBadge.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> deleteListener.onUserDelete(user));
        }
    }

    @Override
    public int getItemCount() { return users.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvBmi, tvGoal, tvCalories, tvJoined, tvAdminBadge;
        ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName      = itemView.findViewById(R.id.tvAdminUserName);
            tvEmail     = itemView.findViewById(R.id.tvAdminUserEmail);
            tvBmi       = itemView.findViewById(R.id.tvAdminUserBmi);
            tvGoal      = itemView.findViewById(R.id.tvAdminUserGoal);
            tvCalories  = itemView.findViewById(R.id.tvAdminUserCalories);
            tvJoined    = itemView.findViewById(R.id.tvAdminUserJoined);
            tvAdminBadge = itemView.findViewById(R.id.tvAdminBadge);
            btnDelete   = itemView.findViewById(R.id.btnDeleteUser);
        }
    }
}
