package tk.therealsuji.vtopchennai.helpers;

import static android.content.Context.DOWNLOAD_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.text.format.DateFormat;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;

import org.json.JSONObject;
import org.json.JSONArray;

import android.view.View;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import java.text.DecimalFormat;
import tk.therealsuji.vtopchennai.models.Course;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import tk.therealsuji.vtopchennai.BuildConfig;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.activities.WebViewActivity;
import tk.therealsuji.vtopchennai.fragments.RecyclerViewFragment;
import tk.therealsuji.vtopchennai.fragments.ViewPagerFragment;
import tk.therealsuji.vtopchennai.models.Exam;
import tk.therealsuji.vtopchennai.models.Timetable;
import tk.therealsuji.vtopchennai.receivers.ExamNotificationReceiver;
import tk.therealsuji.vtopchennai.receivers.TimetableNotificationReceiver;
import tk.therealsuji.vtopchennai.receivers.SmartDndReceiver;

public class SettingsRepository {
    public static final String GITHUB_OWNER = "shanmukhasaireddy13";
    public static final String GITHUB_REPO = "Vtop-App";
    public static final String GITHUB_BASE_URL = "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO;
    public static final String GITHUB_RELEASES_URL = GITHUB_BASE_URL + "/releases";
    public static final String GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    public static final String GITHUB_FEATURE_URL = GITHUB_BASE_URL + "/issues/new";
    public static final String GITHUB_ISSUE_URL = GITHUB_BASE_URL + "/issues/new";

    public static final String APP_BASE_URL = GITHUB_BASE_URL;
    public static final String APP_ABOUT_URL = GITHUB_LATEST_RELEASE_API;
    public static final String APP_PRIVACY_URL = GITHUB_BASE_URL + "/blob/main/README.md";
    public static final String DEVELOPER_BASE_URL = "https://github.com/" + GITHUB_OWNER;

    public static final String MOODLE_BASE_URL = "https://lms.vit.ac.in";
    public static final String MOODLE_LOGIN_PATH = "/login/token.php";
    public static final String MOODLE_UPLOAD_PATH = "/webservice/upload.php";
    public static final String MOODLE_WEBSERVICE_PATH = "/webservice/rest/server.php";

    public static final String VTOP_BASE_URL = "https://vtopcc.vit.ac.in/vtop";

    public static final int THEME_DAY = 1;
    public static final int THEME_NIGHT = 2;
    public static final int THEME_SYSTEM_DAY = 3;
    public static final int THEME_SYSTEM_NIGHT = 4;

    public static final int NOTIFICATION_ID_EXAMS = 1;
    public static final int NOTIFICATION_ID_TIMETABLE = 2;
    public static final int NOTIFICATION_ID_VTOP_DOWNLOAD = 3;

    public static int getTheme(Context context) {
        String appearance = getSharedPreferences(context).getString("appearance", "system");

        if (appearance.equals("dark")) {
            return THEME_NIGHT;
        } else if (appearance.equals("light")) {
            return THEME_DAY;
        }

        return getSystemTheme(context);
    }

    public static int getSystemTheme(Context context) {
        int currentNightMode = context.getApplicationContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            return THEME_SYSTEM_NIGHT;
        } else {
            return THEME_SYSTEM_DAY;
        }
    }

    public static void applyDynamicColors(Activity activity, boolean amoledMode) {
        DynamicColorsOptions.Builder dynamicColorsOptions = new DynamicColorsOptions.Builder();
        if (amoledMode) dynamicColorsOptions.setThemeOverlay(R.style.ThemeOverlay_VTOP_Amoled);
        DynamicColors.applyToActivityIfAvailable(activity, dynamicColorsOptions.build());
    }

    public static float getCGPA(Context context) {
        return getSharedPreferences(context).getFloat("cgpa", 0);
    }

    public static boolean isRefreshRequired(Context context) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, -7);

        Date now = c.getTime();
        Date lastRefreshed = new Date(getSharedPreferences(context).getLong("lastRefreshed", 0));

        return !lastRefreshed.after(now);
    }

    public static boolean isSignedIn(Context context) {
        return getSharedPreferences(context).getBoolean("isVTOPSignedIn", false);
    }

    public static boolean isMoodleSignedIn(Context context) {
        return getSharedPreferences(context).getBoolean("isMoodleSignedIn", false);
    }

    public static void signOut(Context context) {
        AppDatabase.deleteDatabase(context);
        getSharedPreferences(context).edit().clear().apply();

        SharedPreferences encryptedSharedPreferences = getEncryptedSharedPreferences(context);

        if (encryptedSharedPreferences != null) {
            encryptedSharedPreferences.edit().clear().apply();
        }
    }

    public static void signOutMoodle(Context context) {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        sharedPreferences.edit().remove("isMoodleSignedIn").apply();
        sharedPreferences.edit().remove("moodleToken").apply();
        sharedPreferences.edit().remove("moodlePrivateToken").apply();
    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("tk.therealsuji.vtopchennai", Context.MODE_PRIVATE);
    }

    public static SharedPreferences getEncryptedSharedPreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    "credentials",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            return context.getSharedPreferences("credentials_fallback", Context.MODE_PRIVATE);
        }
    }

    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasFileReadPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }

        return hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean hasFileWritePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }

        return hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean hasNotificationPermission(Context context) {
        // On android version 32 and below, notifications are always allowed
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            return true;
        }

        return hasPermission(context, Manifest.permission.POST_NOTIFICATIONS);
    }

    public static Observable<JSONObject> fetchAboutJson(boolean useVersion) {
        return Observable.fromCallable(() -> {
                    HttpURLConnection httpURLConnection = null;
                    try {
                        URL url = new URL(GITHUB_LATEST_RELEASE_API);
                        httpURLConnection = (HttpURLConnection) url.openConnection();
                        httpURLConnection.setRequestProperty("User-Agent", "VTOP-App/" + BuildConfig.VERSION_NAME);
                        httpURLConnection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                        httpURLConnection.setConnectTimeout(8000);
                        httpURLConnection.setReadTimeout(8000);

                        int responseCode = httpURLConnection.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            InputStream in = httpURLConnection.getInputStream();
                            java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            reader.close();

                            JSONObject release = new JSONObject(sb.toString());
                            String tagName = release.optString("tag_name", "");
                            String releaseNotes = release.optString("body", "Bug fixes and performance improvements.");
                            String htmlUrl = release.optString("html_url", GITHUB_RELEASES_URL);

                            // Find direct APK asset url
                            String directApkUrl = null;
                            JSONArray assets = release.optJSONArray("assets");
                            if (assets != null && assets.length() > 0) {
                                for (int i = 0; i < assets.length(); i++) {
                                    JSONObject asset = assets.optJSONObject(i);
                                    if (asset != null) {
                                        String name = asset.optString("name", "");
                                        if (name.endsWith(".apk")) {
                                            directApkUrl = asset.optString("browser_download_url", "");
                                            break;
                                        }
                                    }
                                }
                            }
                            if (directApkUrl == null || directApkUrl.isEmpty()) {
                                directApkUrl = "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/download/" + tagName + "/app-debug.apk";
                            }

                            JSONObject result = new JSONObject();
                            result.put("tagName", tagName);
                            result.put("releaseNotes", releaseNotes);
                            result.put("downloadUrl", directApkUrl);

                            int computedVersionCode = parseVersionToCode(tagName);
                            result.put("versionCode", computedVersionCode);

                            return result;
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (httpURLConnection != null) {
                            httpURLConnection.disconnect();
                        }
                    }
                    return new JSONObject();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public static boolean isVersionNewer(String remoteTag, String localVersionName) {
        if (remoteTag == null || localVersionName == null) return false;
        try {
            String cleanRemote = remoteTag.replaceAll("[^0-9.]", "").trim();
            String cleanLocal = localVersionName.replaceAll("[^0-9.]", "").trim();

            if (cleanRemote.isEmpty() || cleanLocal.isEmpty()) return false;

            String[] remoteParts = cleanRemote.split("\\.");
            String[] localParts = cleanLocal.split("\\.");

            int length = Math.max(remoteParts.length, localParts.length);
            for (int i = 0; i < length; i++) {
                int remoteVal = (i < remoteParts.length && !remoteParts[i].isEmpty()) ? Integer.parseInt(remoteParts[i]) : 0;
                int localVal = (i < localParts.length && !localParts[i].isEmpty()) ? Integer.parseInt(localParts[i]) : 0;

                if (remoteVal > localVal) return true;
                if (remoteVal < localVal) return false;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static int parseVersionToCode(String version) {
        if (version == null) return 0;
        String clean = version.replaceAll("[^0-9.]", "").trim();
        String[] parts = clean.split("\\.");
        int code = 0;
        try {
            if (parts.length >= 1) code += Integer.parseInt(parts[0]) * 10000;
            if (parts.length >= 2) code += Integer.parseInt(parts[1]) * 100;
            if (parts.length >= 3) code += Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
        }
        return code;
    }

    public static void downloadAndInstallUpdate(Context context, String versionName, String apkUrl) {
        if (apkUrl == null || apkUrl.isEmpty()) {
            apkUrl = GITHUB_RELEASES_URL;
        }

        String fileName = "VTOP-" + (versionName != null && versionName.startsWith("v") ? versionName : "v" + versionName) + ".apk";
        Toast.makeText(context, "Starting download: " + fileName + "\nCheck notification bar for download progress.", Toast.LENGTH_LONG).show();

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("Downloading " + fileName);
            request.setDescription("VTOP App Update " + versionName);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setMimeType("application/vnd.android.package-archive");
            request.allowScanningByMediaScanner();

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                downloadManager.enqueue(request);
            }
        } catch (Exception e) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
            context.startActivity(browserIntent);
        }
    }

    public static void openDownloadPage(Context context) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL));
        context.startActivity(browserIntent);
    }

    public static void openRecyclerViewFragment(FragmentActivity fragmentActivity, int titleId, int contentType) {
        RecyclerViewFragment recyclerViewFragment = new RecyclerViewFragment();
        Bundle bundle = new Bundle();

        bundle.putInt("title_id", titleId);
        bundle.putInt("content_type", contentType);

        recyclerViewFragment.setArguments(bundle);

        fragmentActivity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, 0, 0, R.anim.slide_out_right)
                .add(android.R.id.content, recyclerViewFragment)
                .addToBackStack(null)
                .commit();
    }

    public static void openViewPagerFragment(FragmentActivity fragmentActivity, int titleId, int contentType) {
        openViewPagerFragment(fragmentActivity, titleId, contentType, null);
    }

    public static void openViewPagerFragment(FragmentActivity fragmentActivity, int titleId, int contentType, Bundle extraArgs) {
        ViewPagerFragment viewPagerFragment = new ViewPagerFragment();
        Bundle bundle = new Bundle();

        bundle.putInt("title_id", titleId);
        bundle.putInt("content_type", contentType);
        if (extraArgs != null) {
            bundle.putAll(extraArgs);
        }

        viewPagerFragment.setArguments(bundle);

        fragmentActivity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, 0, 0, R.anim.slide_out_right)
                .add(android.R.id.content, viewPagerFragment)
                .addToBackStack(null)
                .commit();
    }

    public static void showCourseInfoBottomSheet(Context context, Course.AllData course) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        View bottomSheetLayout = View.inflate(context, R.layout.layout_bottom_sheet_course_info, null);
        bottomSheetDialog.setContentView(bottomSheetLayout);
        bottomSheetDialog.show();

        TextView courseTitle = bottomSheetLayout.findViewById(R.id.text_view_course_title);
        TextView courseCode = bottomSheetLayout.findViewById(R.id.text_view_course_code);
        TextView faculty = bottomSheetLayout.findViewById(R.id.text_view_faculty);
        TextView venue = bottomSheetLayout.findViewById(R.id.text_view_venue);
        TextView attendanceExcessText = bottomSheetLayout.findViewById(R.id.text_view_attendance_excess);
        TextView attendanceText = bottomSheetLayout.findViewById(R.id.text_view_attendance);
        Chip slot = bottomSheetLayout.findViewById(R.id.chip_slot);
        ProgressBar attendanceProgress = bottomSheetLayout.findViewById(R.id.progress_bar_attendance);

        courseTitle.setText(course.courseTitle);
        courseCode.setText(course.courseCode);
        faculty.setText(Html.fromHtml(context.getString(R.string.faculty, course.faculty), Html.FROM_HTML_MODE_LEGACY));
        venue.setText(Html.fromHtml(context.getString(R.string.venue, course.venue), Html.FROM_HTML_MODE_LEGACY));

        if (course.courseType != null && course.courseType.equals("lab")) {
            slot.setChipIconResource(R.drawable.ic_lab);
        } else {
            slot.setChipIconResource(R.drawable.ic_theory);
        }

        if (course.slot != null) {
            slot.setVisibility(View.VISIBLE);
            slot.setText(course.slot);
        } else if (course.slots != null && !course.slots.isEmpty()) {
            slot.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < course.slots.size(); i++) {
                if (i > 0) sb.append(" + ");
                sb.append(course.slots.get(i));
            }
            slot.setText(sb.toString());
        } else {
            slot.setVisibility(View.GONE);
        }

        if (course.attendancePercentage == null) {
            attendanceText.setText(context.getString(R.string.na));
            attendanceProgress.setProgress(0);
        } else {
            attendanceText.setText(new DecimalFormat("#'%'").format(course.attendancePercentage));
            attendanceProgress.setProgress(course.attendancePercentage);

            if (SettingsRepository.getCGPA(context) < 9) {
                double attendanceExcess = 100 * course.attendanceAttended - 75 * course.attendanceTotal;

                if (course.attendancePercentage < 75) {
                    attendanceExcess = Math.floor(attendanceExcess / 25);
                    attendanceProgress.setSecondaryProgress(75);
                } else {
                    attendanceExcess = Math.floor(attendanceExcess / 75);
                }

                attendanceExcessText.setVisibility(View.VISIBLE);
                attendanceExcessText.setText(new DecimalFormat("+#;-#").format(attendanceExcess));

                if (attendanceExcess < 0) {
                    attendanceExcessText.setTextColor(MaterialColors.getColor(attendanceExcessText, R.attr.colorError));
                } else if (attendanceExcess == 0) {
                    attendanceExcessText.setTextColor(MaterialColors.getColor(attendanceExcessText, R.attr.colorSecondary));
                }
            }
        }

        // Show detailed attendance logs if available
        TextView logsTitle = bottomSheetLayout.findViewById(R.id.text_view_attendance_logs_title);
        View logsScrollView = bottomSheetLayout.findViewById(R.id.scroll_view_attendance_logs);
        LinearLayout logsLayout = bottomSheetLayout.findViewById(R.id.linear_layout_attendance_logs);

        if (course.attendanceDetails != null && !course.attendanceDetails.isEmpty() && !course.attendanceDetails.equals("[]")) {
            try {
                JSONArray logsArray = new JSONArray(course.attendanceDetails);
                if (logsArray.length() > 0) {
                    logsTitle.setVisibility(View.VISIBLE);
                    logsScrollView.setVisibility(View.VISIBLE);
                    logsLayout.removeAllViews();

                    for (int i = 0; i < logsArray.length(); i++) {
                        JSONObject logObj = logsArray.getJSONObject(i);
                        String logDate = logObj.optString("date", "");
                        String logSlot = logObj.optString("slot", "");
                        String logTiming = logObj.optString("timing", "");
                        String logStatus = logObj.optString("status", "");

                        View logView = View.inflate(context, R.layout.layout_item_attendance_log, null);
                        TextView logDateTv = logView.findViewById(R.id.text_view_log_date);
                        TextView logInfoTv = logView.findViewById(R.id.text_view_log_info);
                        TextView logStatusTv = logView.findViewById(R.id.text_view_log_status);

                        logDateTv.setText(logDate);
                        logInfoTv.setText(logSlot + " • " + logTiming);
                        logStatusTv.setText(logStatus);

                        // Color background and text nicely for Material Design 3
                        if (logStatus.toLowerCase().contains("absent")) {
                            logStatusTv.setTextColor(0xFFD32F2F); // Pleasant Dark Red
                            logStatusTv.setBackground(createRoundedDrawable(0x1AD32F2F)); // Transparent Red
                        } else if (logStatus.toLowerCase().contains("medical") || logStatus.toLowerCase().contains("ml")) {
                            logStatusTv.setTextColor(0xFFF57C00); // Pleasant Dark Orange
                            logStatusTv.setBackground(createRoundedDrawable(0x1AF57C00)); // Transparent Orange
                        } else {
                            logStatusTv.setTextColor(0xFF388E3C); // Pleasant Dark Green
                            logStatusTv.setBackground(createRoundedDrawable(0x1A388E3C)); // Transparent Green
                        }

                        logsLayout.addView(logView);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        bottomSheetLayout.findViewById(R.id.progress_bar_loading).setVisibility(View.GONE);
        bottomSheetLayout.findViewById(R.id.linear_layout_container).setVisibility(View.VISIBLE);
    }

    private static android.graphics.drawable.GradientDrawable createRoundedDrawable(int color) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(16); // 8dp
        shape.setColor(color);
        return shape;
    }

    public static void openWebViewActivity(Context context, String title, String url) {
        Intent intent = new Intent(context, WebViewActivity.class)
                .putExtra("url", url)
                .putExtra("title", title);
        context.startActivity(intent);
    }

    public static void openBrowser(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(intent);
    }

    public static void downloadFile(Context context, String filePath, String fileName, String mimetype, Uri uri, String cookie) {
        Toast.makeText(context, Html.fromHtml(context.getString(R.string.downloading_file, fileName), Html.FROM_HTML_MODE_LEGACY), Toast.LENGTH_SHORT).show();

        DownloadManager.Request request = new DownloadManager.Request(uri);
        String encodedFilePath = Uri.encode("VIT Student/" + filePath + "/" + fileName);
        request.addRequestHeader("cookie", cookie);
        request.allowScanningByMediaScanner();
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedFilePath);
        request.setMimeType(mimetype);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle(fileName);

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
        downloadManager.enqueue(request);
    }

    public static String getSystemFormattedTime(Context context, String time) throws ParseException {
        if (DateFormat.is24HourFormat(context)) {
            return time;
        } else {
            SimpleDateFormat hour12 = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

            return hour12.format(Objects.requireNonNull(hour24.parse(time)));
        }
    }

    public static Bitmap getBitmapFromVectorDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static void clearNotificationPendingIntents(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        Intent notificationIntent = new Intent(context, TimetableNotificationReceiver.class);

        int alarmCount = sharedPreferences.getInt("alarmCount", 0);
        while (alarmCount >= 0) {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, --alarmCount, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pendingIntent);
        }

        sharedPreferences.edit().remove("alarmCount").apply();
    }

    public static void setTimetableNotifications(Context context, Timetable timetable) throws ParseException {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar calendar = Calendar.getInstance();
        Intent notificationIntent = new Intent(context, TimetableNotificationReceiver.class);
        SharedPreferences sharedPreferences = getSharedPreferences(context);

        SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);

        int alarmCount = sharedPreferences.getInt("alarmCount", 0);
        int day = calendar.get(Calendar.DAY_OF_WEEK) - 1;

        Integer[] slots = {
                timetable.sunday,
                timetable.monday,
                timetable.tuesday,
                timetable.wednesday,
                timetable.thursday,
                timetable.friday,
                timetable.saturday
        };

        Date today = dateFormat.parse(dateFormat.format(calendar.getTime()));
        Date now = hour24.parse(hour24.format(calendar.getTime()));

        for (int i = 0; i < slots.length; ++i) {
            if (slots[i] == null) {
                continue;
            }

            assert today != null;
            Calendar alarm = Calendar.getInstance();
            alarm.setTime(today);

            if (i == day) {
                Date startTime = hour24.parse(timetable.startTime);
                assert startTime != null;

                if (startTime.before(now)) {
                    alarm.add(Calendar.DATE, 7);
                }
            } else if (i > day) {
                alarm.add(Calendar.DATE, i - day);
            } else {
                alarm.add(Calendar.DATE, 7 - day + i);
            }

            alarm.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timetable.startTime.split(":")[0]));
            alarm.set(Calendar.MINUTE, Integer.parseInt(timetable.startTime.split(":")[1]));

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, alarmCount++, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, alarm.getTimeInMillis(), AlarmManager.INTERVAL_DAY * 7, pendingIntent);

            alarm.add(Calendar.MINUTE, -30);
            pendingIntent = PendingIntent.getBroadcast(context, alarmCount++, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, alarm.getTimeInMillis(), AlarmManager.INTERVAL_DAY * 7, pendingIntent);
        }

        sharedPreferences.edit().putInt("alarmCount", alarmCount).apply();
    }

    public static void setExamNotifications(Context context, List<Exam> exams) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar calendar = Calendar.getInstance();
        Intent notificationIntent = new Intent(context, ExamNotificationReceiver.class);
        SharedPreferences sharedPreferences = getSharedPreferences(context);

        int alarmCount = sharedPreferences.getInt("alarmCount", 0);

        Date now = calendar.getTime();

        for (int i = 0; i < exams.size(); ++i) {
            Exam exam = exams.get(i);

            if (exam.startTime == null || now.after(new Date(exam.startTime))) {
                continue;
            }

            Calendar alarm = Calendar.getInstance();
            alarm.setTime(new Date(exam.startTime));
            alarm.add(Calendar.MINUTE, -30);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, alarmCount++, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarm.getTimeInMillis(), pendingIntent);
        }

        sharedPreferences.edit().putInt("alarmCount", alarmCount).apply();
    }

    public static boolean canEnableAutoSilent(Context context) {
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        boolean hasDndAccess = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasDndAccess = notificationManager.isNotificationPolicyAccessGranted();
        }

        boolean canScheduleExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canScheduleExact = alarmManager.canScheduleExactAlarms();
        }

        SharedPreferences sharedPreferences = getSharedPreferences(context);
        boolean dndEnabled = sharedPreferences.getBoolean("smartDndEnabled", false);

        return hasDndAccess && canScheduleExact && dndEnabled;
    }

    public static long getMuteBufferMillis(Context context) {
        return getSharedPreferences(context).getLong("MUTE_BUFFER_MILLIS", 3 * 60 * 1000L);
    }

    public static void setAutoSilentEnabled(Context context, boolean enabled) {
        getSharedPreferences(context).edit().putBoolean("smartDndEnabled", enabled).apply();
    }

    public static void clearLegacyDndAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        Intent dndIntent = new Intent(context, SmartDndReceiver.class);
        int alarmCount = sharedPreferences.getInt("dndAlarmCount", 0);
        while (alarmCount >= 0) {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 100000 + --alarmCount, dndIntent, PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pendingIntent);
        }
        sharedPreferences.edit().remove("dndAlarmCount").apply();
    }

    public static void clearDndAlarms(Context context) {
        clearLegacyDndAlarms(context);
        
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent dndIntent = new Intent(context, SmartDndReceiver.class);
        
        dndIntent.setAction(SmartDndReceiver.ACTION_DND_ON);
        PendingIntent pendingIntentOn = PendingIntent.getBroadcast(context, 101, dndIntent, PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntentOn);

        dndIntent.setAction(SmartDndReceiver.ACTION_DND_OFF);
        PendingIntent pendingIntentOff = PendingIntent.getBroadcast(context, 102, dndIntent, PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntentOff);
    }

    public static void rescheduleDndAlarms(Context context) {
        clearDndAlarms(context);

        if (!canEnableAutoSilent(context)) {
            return;
        }

        AppDatabase appDatabase = AppDatabase.getInstance(context.getApplicationContext());
        io.reactivex.rxjava3.core.Single.zip(
                appDatabase.timetableDao().getTimetable(),
                appDatabase.calendarDao().getAll(),
                appDatabase.examsDao().getExams(),
                (timetableList, calendarEvents, exams) -> new Object[] {timetableList, calendarEvents, exams}
        )
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.single())
                .subscribe(new io.reactivex.rxjava3.core.SingleObserver<Object[]>() {
                    @Override
                    public void onSubscribe(io.reactivex.rxjava3.disposables.Disposable d) {}

                    @SuppressWarnings("unchecked")
                    @Override
                    public void onSuccess(Object[] data) {
                        try {
                            List<tk.therealsuji.vtopchennai.models.Timetable> timetableList = (List<tk.therealsuji.vtopchennai.models.Timetable>) data[0];
                            List<tk.therealsuji.vtopchennai.models.CalendarEvent> calendarEvents = (List<tk.therealsuji.vtopchennai.models.CalendarEvent>) data[1];
                            List<tk.therealsuji.vtopchennai.models.Exam> exams = (List<tk.therealsuji.vtopchennai.models.Exam>) data[2];

                            long currentTime = System.currentTimeMillis();
                            
                            TimetableEvaluator.ParsedClass activeClass = TimetableEvaluator.getCurrentActiveClass(timetableList, calendarEvents, exams, currentTime);
                            if (activeClass != null) {
                                // We are mid-class! Force mute now, and schedule the unmute bridge.
                                android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                                android.media.AudioManager audioManager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                                SharedPreferences prefs = getSharedPreferences(context);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted()) {
                                    int currentMode = audioManager.getRingerMode();
                                    if (currentMode != android.media.AudioManager.RINGER_MODE_SILENT) {
                                        prefs.edit().putInt("previousRingerMode", currentMode).apply();
                                    }
                                    audioManager.setRingerMode(android.media.AudioManager.RINGER_MODE_SILENT);
                                }
                                AutoSilentScheduler.scheduleSingleUnmute(context, activeClass);
                            } else {
                                // Normal relay chain
                                TimetableEvaluator.ParsedClass nextClass = TimetableEvaluator.getNextImmediateClass(timetableList, calendarEvents, exams, currentTime);
                                if (nextClass != null) {
                                    AutoSilentScheduler.scheduleNextClassCycle(context, nextClass);
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void onError(Throwable e) {}
                });
    }

    /**
     * WARNING: This function is to be used ONLY for the purpose of testing and not in production.
     * This function returns a naive OkHttpClient that accepts any and all certificates,
     * and hence can lead to attacks.
     */
    @Deprecated
    @SuppressLint({"BadHostnameVerifier", "CustomX509TrustManager", "TrustAllX509TrustManager"})
    public static OkHttpClient getNaiveOkHttpClient() {
        try {
            TrustManager[] trustAllCertificates = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] x509Certificates, String s) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] x509Certificates, String s) {
                        }

                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCertificates, new SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCertificates[0])
                    .hostnameVerifier((s, sslSession) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
