package com.smarthealth.social;

import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.smarthealth.databinding.FragmentSocialBinding;
import com.smarthealth.models.FriendActivity;
import com.smarthealth.models.FriendRequest;
import com.smarthealth.utils.FirebaseHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class SocialFragment extends Fragment {

    private FragmentSocialBinding binding;
    private FriendActivityAdapter feedAdapter;
    private FriendRequestAdapter  requestAdapter;
    private FriendsListAdapter    friendsAdapter;

    private final List<FriendActivity>        feedList    = new ArrayList<>();
    private final List<FriendRequest>         requestList = new ArrayList<>();
    private final List<Map<String, Object>>   friendsList = new ArrayList<>();

    private String myUid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSocialBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        myUid = FirebaseHelper.getInstance().getCurrentUid();

        friendsAdapter = new FriendsListAdapter(friendsList, this::showUnfriendDialog);
        binding.recyclerFriends.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerFriends.setAdapter(friendsAdapter);

        requestAdapter = new FriendRequestAdapter(requestList, this::acceptRequest, this::declineRequest);
        binding.recyclerRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerRequests.setAdapter(requestAdapter);

        feedAdapter = new FriendActivityAdapter(feedList);
        binding.recyclerFeed.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerFeed.setAdapter(feedAdapter);

        binding.btnAddFriend.setOnClickListener(v -> showAddFriendDialog());

        loadFriendsList();
        loadFriendRequests();
        loadActivityFeed();
    }

    private void showUnfriendDialog(String friendUid, String friendName) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Unfriend " + friendName + "?")
            .setMessage("Are you sure you want to remove this person from your friends list?")
            .setPositiveButton("Remove", (dialog, which) -> unfriend(friendUid))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void unfriend(String friendUid) {
        if (myUid == null) return;
        
        // Remove from my list
        FirebaseHelper.getInstance().friendsCollection(myUid).document(friendUid).delete();
        // Remove me from their list
        FirebaseHelper.getInstance().friendsCollection(friendUid).document(myUid).delete();
        
        Toast.makeText(getContext(), "Friend removed", Toast.LENGTH_SHORT).show();
    }

    private void showAddFriendDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Enter friend's email address");
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            | android.text.InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(requireContext())
            .setTitle("Add Friend")
            .setMessage("Send a friend request by email:")
            .setView(input)
            .setPositiveButton("Send Request", (dialog, which) -> {
                String email = input.getText().toString().trim().toLowerCase();
                if (!email.isEmpty()) sendFriendRequest(email);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void sendFriendRequest(String email) {
        if (myUid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(myUid).get()
            .addOnSuccessListener(myDoc -> {
                if (!isAdded() || myDoc == null) return;
                String senderName  = myDoc.getString("displayName");
                String senderEmail = myDoc.getString("email");
                if (senderName  == null) senderName  = "Unknown";
                if (senderEmail == null) senderEmail = "";

                final String finalName  = senderName;
                final String finalEmail = senderEmail;

                FirebaseHelper.getInstance().usersCollection()
                    .whereEqualTo("email", email).limit(1).get()
                    .addOnSuccessListener(snapshots -> {
                        if (!isAdded()) return;
                        if (snapshots == null || snapshots.isEmpty()) {
                            Toast.makeText(getContext(), "No user found with that email.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        DocumentSnapshot doc = snapshots.getDocuments().get(0);
                        String toUid = doc.getId();

                        if (toUid.equals(myUid)) {
                            Toast.makeText(getContext(), "You can't add yourself!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        FirebaseHelper.getInstance().friendsCollection(myUid)
                            .document(toUid).get()
                            .addOnSuccessListener(friendDoc -> {
                                if (friendDoc.exists()) {
                                    Toast.makeText(getContext(), "Already friends!", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                FriendRequest request = new FriendRequest(myUid, finalName, finalEmail, toUid);
                                FirebaseHelper.getInstance().friendRequestsCollection()
                                    .add(request)
                                    .addOnSuccessListener(ref ->
                                        Toast.makeText(getContext(),
                                            "Friend request sent to " + email, Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                        Toast.makeText(getContext(),
                                            "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            });
                    })
                    .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            });
    }

    private void loadFriendsList() {
        if (myUid == null) return;

        FirebaseHelper.getInstance().friendsCollection(myUid)
            .addSnapshotListener((snapshots, e) -> {
                if (!isAdded() || snapshots == null) return;
                friendsList.clear();

                for (DocumentSnapshot doc : snapshots) {
                    Map<String, Object> friend = new HashMap<>();
                    friend.put("name",  doc.getString("name")  != null ? doc.getString("name")  : "Unknown");
                    friend.put("email", doc.getString("email") != null ? doc.getString("email") : "");
                    friend.put("uid",   doc.getId());
                    friendsList.add(friend);
                }

                friendsAdapter.notifyDataSetChanged();

                if (friendsList.isEmpty()) {
                    binding.tvFriendsHeader.setText("👥 Friends (0)");
                    binding.tvNoFriends.setVisibility(View.VISIBLE);
                } else {
                    binding.tvFriendsHeader.setText("👥 Friends (" + friendsList.size() + ")");
                    binding.tvNoFriends.setVisibility(View.GONE);
                }

                loadActivityFeed();
            });
    }

    private void loadFriendRequests() {
        if (myUid == null) return;
        FirebaseHelper.getInstance().friendRequestsCollection()
            .whereEqualTo("toUid", myUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener((snapshots, e) -> {
                if (!isAdded() || snapshots == null) return;
                requestList.clear();
                for (DocumentSnapshot doc : snapshots) {
                    FriendRequest req = doc.toObject(FriendRequest.class);
                    if (req != null) {
                        req.setId(doc.getId());
                        requestList.add(req);
                    }
                }
                requestAdapter.notifyDataSetChanged();
                binding.tvRequestsHeader.setVisibility(requestList.isEmpty() ? View.GONE : View.VISIBLE);
                binding.recyclerRequests.setVisibility(requestList.isEmpty() ? View.GONE : View.VISIBLE);
            });
    }

    private void acceptRequest(FriendRequest request) {
        if (myUid == null) return;

        FirebaseHelper.getInstance().friendRequestsCollection()
            .document(request.getId()).update("status", "accepted");

        FirebaseHelper.getInstance().usersCollection().document(myUid).get()
            .addOnSuccessListener(myDoc -> {
                String myName  = myDoc != null && myDoc.getString("displayName") != null
                    ? myDoc.getString("displayName") : "Unknown";
                String myEmail = myDoc != null && myDoc.getString("email") != null
                    ? myDoc.getString("email") : "";

                Map<String, Object> myEntry = new HashMap<>();
                myEntry.put("uid",   request.getFromUid());
                myEntry.put("name",  request.getFromName() != null ? request.getFromName() : "Unknown");
                myEntry.put("email", request.getFromEmail() != null ? request.getFromEmail() : "");
                myEntry.put("since", System.currentTimeMillis());

                Map<String, Object> theirEntry = new HashMap<>();
                theirEntry.put("uid",   myUid);
                theirEntry.put("name",  myName);
                theirEntry.put("email", myEmail);
                theirEntry.put("since", System.currentTimeMillis());

                FirebaseHelper.getInstance().friendsCollection(myUid)
                    .document(request.getFromUid()).set(myEntry);
                FirebaseHelper.getInstance().friendsCollection(request.getFromUid())
                    .document(myUid).set(theirEntry);

                if (isAdded()) {
                    Toast.makeText(getContext(),
                        "You and " + request.getFromName() + " are now friends!",
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void declineRequest(FriendRequest request) {
        FirebaseHelper.getInstance().friendRequestsCollection()
            .document(request.getId()).update("status", "declined");
    }

    private void loadActivityFeed() {
        if (myUid == null || !isAdded()) return;

        FirebaseHelper.getInstance().friendsCollection(myUid).get()
            .addOnSuccessListener(friendSnaps -> {
                if (!isAdded() || friendSnaps == null) return;
                feedList.clear();

                if (friendSnaps.isEmpty()) {
                    binding.tvEmptyFeed.setVisibility(View.VISIBLE);
                    feedAdapter.notifyDataSetChanged();
                    return;
                }
                binding.tvEmptyFeed.setVisibility(View.GONE);

                for (DocumentSnapshot friendDoc : friendSnaps) {
                    String friendUid = friendDoc.getId();
                    FirebaseHelper.getInstance().activityFeedCollection(friendUid)
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(5).get()
                        .addOnSuccessListener(actSnaps -> {
                            if (!isAdded() || actSnaps == null) return;
                            for (DocumentSnapshot actDoc : actSnaps) {
                                FriendActivity act = actDoc.toObject(FriendActivity.class);
                                if (act != null) feedList.add(act);
                            }
                            feedList.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                            feedAdapter.notifyDataSetChanged();
                        });
                }
            });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
