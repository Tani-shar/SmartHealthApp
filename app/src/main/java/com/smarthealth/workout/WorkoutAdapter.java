package com.smarthealth.workout;

import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smarthealth.databinding.ItemWorkoutBinding;
import com.smarthealth.models.WorkoutExercise;
import java.util.List;

public class WorkoutAdapter extends RecyclerView.Adapter<WorkoutAdapter.ViewHolder> {

    private final List<WorkoutExercise> exercises;

    public WorkoutAdapter(List<WorkoutExercise> exercises) {
        this.exercises = exercises;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWorkoutBinding b = ItemWorkoutBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(exercises.get(position));
    }

    @Override
    public int getItemCount() { return exercises.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemWorkoutBinding binding;

        ViewHolder(ItemWorkoutBinding b) {
            super(b.getRoot());
            binding = b;
        }

        void bind(WorkoutExercise ex) {
            binding.tvExerciseName.setText(ex.getName());
            binding.tvExerciseDesc.setText(ex.getDescription());
            binding.tvCategory.setText(ex.getCategory().toUpperCase());

            String detail = "";
            if (ex.getSets() != null && !ex.getSets().equals("—"))
                detail += "Sets: " + ex.getSets() + "  ";
            if (ex.getReps() != null && !ex.getReps().equals("—"))
                detail += "Reps: " + ex.getReps() + "  ";
            if (ex.getDuration() != null && !ex.getDuration().equals("—"))
                detail += "Duration: " + ex.getDuration();
            binding.tvExerciseDetail.setText(detail.trim());
        }
    }
}
