package com.smarthealth.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.smarthealth.MainActivity;
import com.smarthealth.R;
import com.smarthealth.utils.FirebaseHelper;

public class SmartHealthMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID   = "smart_health_channel";
    public static final String CHANNEL_NAME = "Smart Health Notifications";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Save FCM token to Firestore for this user
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid != null) {
            FirebaseHelper.getInstance().usersCollection()
                .document(uid)
                .update("fcmToken", token);
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "Smart Health";
        String body  = "Time to check your health stats!";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null)
                title = remoteMessage.getNotification().getTitle();
            if (remoteMessage.getNotification().getBody() != null)
                body = remoteMessage.getNotification().getBody();
        } else if (!remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().containsKey("title"))
                title = remoteMessage.getData().get("title");
            if (remoteMessage.getData().containsKey("body"))
                body = remoteMessage.getData().get("body");
        }

        showNotification(title, body);
    }

    public static void showNotification(Context context, String title, String body) {
        createChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void showNotification(String title, String body) {
        showNotification(this, title, body);
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Health reminders and updates");
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
