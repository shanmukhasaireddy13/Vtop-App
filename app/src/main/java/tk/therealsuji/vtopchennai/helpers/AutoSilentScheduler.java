package tk.therealsuji.vtopchennai.helpers;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import tk.therealsuji.vtopchennai.receivers.SmartDndReceiver;

public class AutoSilentScheduler {

    private static final int REQ_MUTE = 101;
    private static final int REQ_UNMUTE = 102;

    public static void scheduleNextClassCycle(Context context, TimetableEvaluator.ParsedClass nextClass) {
        long bufferMillis = SettingsRepository.getMuteBufferMillis(context);
        scheduleAlarm(context, SmartDndReceiver.ACTION_DND_ON, REQ_MUTE, nextClass.startTimeMillis - bufferMillis);
        scheduleAlarm(context, SmartDndReceiver.ACTION_DND_OFF, REQ_UNMUTE, nextClass.endTimeMillis + bufferMillis);
    }

    public static void scheduleSingleUnmute(Context context, TimetableEvaluator.ParsedClass activeClass) {
        long bufferMillis = SettingsRepository.getMuteBufferMillis(context);
        scheduleAlarm(context, SmartDndReceiver.ACTION_DND_OFF, REQ_UNMUTE, activeClass.endTimeMillis + bufferMillis);
    }

    private static void scheduleAlarm(Context context, String action, int requestCode, long triggerTime) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, SmartDndReceiver.class);
        intent.setAction(action);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } catch (SecurityException e) {
            handlePermissionRevoked(context);
        }
    }

    private static void handlePermissionRevoked(Context context) {
        SettingsRepository.setAutoSilentEnabled(context, false);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        }
    }
}
