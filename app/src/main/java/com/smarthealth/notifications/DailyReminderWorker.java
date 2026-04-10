package com.smarthealth.notifications;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class DailyReminderWorker extends Worker {

    public DailyReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String type = getInputData().getString("reminder_type");
        if (type == null) type = "general";

        String title, body;
        switch (type) {
            case "log_meal":
                title = "🍽️ Log Your Meals";
                body  = "Don't forget to track today's meals and stay on target!";
                break;
            case "workout":
                title = "💪 Workout Reminder";
                body  = "Time for your daily workout! Keep up the great work.";
                break;
            case "weigh_in":
                title = "⚖️ Weekly Weigh-In";
                body  = "It's weigh-in day! Update your weight to track your progress.";
                break;
            default:
                title = "🏃 Smart Health Check-In";
                body  = "Open the app to check your daily health stats!";
                break;
        }

        SmartHealthMessagingService.createChannel(getApplicationContext());
        SmartHealthMessagingService.showNotification(getApplicationContext(), title, body);
        return Result.success();
    }
}
