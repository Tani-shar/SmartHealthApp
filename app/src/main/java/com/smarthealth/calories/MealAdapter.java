package com.smarthealth.calories;

import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.smarthealth.databinding.ItemMealBinding;
import com.smarthealth.models.MealLog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MealAdapter extends RecyclerView.Adapter<MealAdapter.ViewHolder> {

    public interface OnDeleteListener { void onDelete(MealLog meal); }

    private final List<MealLog> meals;
    private final OnDeleteListener deleteListener;
    private final String todayDate;

    public MealAdapter(List<MealLog> meals, OnDeleteListener listener) {
        this.meals = meals;
        this.deleteListener = listener;
        this.todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMealBinding b = ItemMealBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(meals.get(position));
    }

    @Override
    public int getItemCount() { return meals.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemMealBinding binding;

        ViewHolder(ItemMealBinding b) {
            super(b.getRoot());
            binding = b;
        }

        void bind(MealLog meal) {
            binding.tvFoodName.setText(meal.getFoodName());
            binding.tvCalories.setText(meal.getCalories() + " kcal");

            String dateLabel = meal.getDate().equals(todayDate) ? "TODAY" : meal.getDate();
            binding.tvMealType.setText(dateLabel + " • " + meal.getMealType().toUpperCase());

            binding.tvMacros.setText(String.format(
                "P: %.0fg  C: %.0fg  F: %.0fg",
                meal.getProtein(), meal.getCarbs(), meal.getFat()));
            binding.btnDelete.setOnClickListener(v -> deleteListener.onDelete(meal));

            // Show meal photo if available
            if (meal.getPhotoUrl() != null && !meal.getPhotoUrl().isEmpty()) {
                binding.ivMealPhoto.setVisibility(View.VISIBLE);
                Glide.with(binding.getRoot().getContext())
                    .load(meal.getPhotoUrl())
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(binding.ivMealPhoto);
            } else {
                binding.ivMealPhoto.setVisibility(View.GONE);
            }
        }
    }
}
