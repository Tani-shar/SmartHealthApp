package com.smarthealth.social;

import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smarthealth.databinding.ItemFriendRequestBinding;
import com.smarthealth.models.FriendRequest;
import java.util.List;

public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.ViewHolder> {

    public interface OnAccept  { void onAccept(FriendRequest r); }
    public interface OnDecline { void onDecline(FriendRequest r); }

    private final List<FriendRequest> items;
    private final OnAccept  acceptListener;
    private final OnDecline declineListener;

    public FriendRequestAdapter(List<FriendRequest> items,
                                 OnAccept accept, OnDecline decline) {
        this.items           = items;
        this.acceptListener  = accept;
        this.declineListener = decline;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemFriendRequestBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFriendRequestBinding binding;

        ViewHolder(ItemFriendRequestBinding b) {
            super(b.getRoot());
            binding = b;
        }

        void bind(FriendRequest req) {
            binding.tvRequesterName.setText(req.getFromName() != null ? req.getFromName() : "Unknown");
            binding.tvRequesterEmail.setText(req.getFromEmail() != null ? req.getFromEmail() : "");
            binding.btnAccept.setOnClickListener(v -> {
                acceptListener.onAccept(req);
                items.remove(getAdapterPosition());
                notifyItemRemoved(getAdapterPosition());
            });
            binding.btnDecline.setOnClickListener(v -> {
                declineListener.onDecline(req);
                items.remove(getAdapterPosition());
                notifyItemRemoved(getAdapterPosition());
            });
        }
    }
}
