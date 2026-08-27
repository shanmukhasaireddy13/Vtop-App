package tk.therealsuji.vtopchennai.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.activities.LauncherActivity;

public class TasksNotificationReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID_TASKS = "tasks_channel";
    public static final String CHANNEL_NAME_TASKS = "Scheduled Tasks";

    @Override
    public void onReceive(Context context, Intent intent) {
        int taskId = intent.getIntExtra("task_id", 0);
        String taskTitle = intent.getStringExtra("task_title");
        String taskTime = intent.getStringExtra("task_time");

        if (taskTitle == null) {
            taskTitle = "Scheduled Task Starting!";
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_TASKS,
                    CHANNEL_NAME_TASKS,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                taskId,
                new Intent(context, LauncherActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_TASKS)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle(taskTitle)
                .setContentText("Time: " + (taskTime != null ? taskTime : ""))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM);

        manager.notify(taskId, builder.build());
    }
}
