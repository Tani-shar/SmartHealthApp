package com.smarthealth.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

public class ReminderScheduler {

    public static void scheduleDailyReminders(Context context) {
        // Default reminders if not set by user
        scheduleReminder(context, 8, 0, 100, "Time to log your breakfast!");
        scheduleReminder(context, 18, 0, 101, "Ready for your daily workout?");
    }

    public static void scheduleCustomReminder(Context context, int hour, int minute, int requestId, String message) {
        scheduleReminder(context, hour, minute, requestId, message);
    }

    private static void scheduleReminder(Context context, int hour, int minute, int requestId, String message) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, DailyReminderWorker.class);
        intent.putExtra("message", message);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    public static void cancelAllReminders(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, DailyReminderWorker.class);
        
        PendingIntent p1 = PendingIntent.getBroadcast(context, 100, intent, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent p2 = PendingIntent.getBroadcast(context, 101, intent, PendingIntent.FLAG_IMMUTABLE);
        
        if (alarmManager != null) {
            alarmManager.cancel(p1);
            alarmManager.cancel(p2);
        }
    }
}
