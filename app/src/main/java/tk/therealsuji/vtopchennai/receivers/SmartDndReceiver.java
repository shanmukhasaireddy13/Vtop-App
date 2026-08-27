package tk.therealsuji.vtopchennai.receivers;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.helpers.AutoSilentScheduler;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.helpers.TimetableEvaluator;
import tk.therealsuji.vtopchennai.models.CalendarEvent;
import tk.therealsuji.vtopchennai.models.Exam;
import tk.therealsuji.vtopchennai.models.Timetable;

public class SmartDndReceiver extends BroadcastReceiver {
    public static final String ACTION_DND_ON = "tk.therealsuji.vtopchennai.action.DND_ON";
    public static final String ACTION_DND_OFF = "tk.therealsuji.vtopchennai.action.DND_OFF";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        
        final String action = intent.getAction();
        if (!ACTION_DND_ON.equals(action) && !ACTION_DND_OFF.equals(action)) {
            return;
        }

        final PendingResult pendingResult = goAsync();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    AppDatabase database = AppDatabase.getInstance(context);
                    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
                    long currentTime = System.currentTimeMillis();
                    
                    boolean hasDndAccess = false;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        hasDndAccess = notificationManager.isNotificationPolicyAccessGranted();
                    }

                    if (ACTION_DND_ON.equals(action)) {
                        if (hasDndAccess) {
                            int currentMode = audioManager.getRingerMode();
                            if (currentMode != AudioManager.RINGER_MODE_SILENT) {
                                prefs.edit().putInt("previousRingerMode", currentMode).apply();
                            }
                            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                        }
                    } else if (ACTION_DND_OFF.equals(action)) {
                        long DOZE_THROTTLE_QUOTA_MILLIS = 9 * 60 * 1000L;
                        long bufferMillis = SettingsRepository.getMuteBufferMillis(context);
                        long safeLookaheadMillis = Math.max(
                                DOZE_THROTTLE_QUOTA_MILLIS - (bufferMillis * 2),
                                5 * 60 * 1000L
                        );

                        // Extract Timetable, CalendarEvents, and Exams to check conflicts and schedule the next class
                        List<Timetable> timetableList = database.timetableDao().getTimetable().blockingGet();
                        List<CalendarEvent> calendarEvents = database.calendarDao().getAll().blockingGet();
                        List<Exam> exams = database.examsDao().getExams().blockingGet();

                        boolean isConflict = TimetableEvaluator.isClassActiveOrStartingSoon(
                                timetableList,
                                calendarEvents,
                                exams,
                                currentTime,
                                currentTime + safeLookaheadMillis,
                                currentTime
                        );

                        if (!isConflict && hasDndAccess) {
                            int previousMode = prefs.getInt("previousRingerMode", AudioManager.RINGER_MODE_NORMAL);
                            audioManager.setRingerMode(previousMode);
                        }

                        // Relay handoff
                        TimetableEvaluator.ParsedClass nextClass = TimetableEvaluator.getNextImmediateClass(timetableList, calendarEvents, exams, currentTime);
                        if (nextClass != null) {
                            AutoSilentScheduler.scheduleNextClassCycle(context, nextClass);
                        }
                    }
                } finally {
                    pendingResult.finish();
                }
            }
        }).start();
    }
}
