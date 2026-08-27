package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import tk.therealsuji.vtopchennai.R;

public class FeatureFlagsRepository {
    private static final String TAG = "FeatureFlags";

    public static final String REMOTE_FLAGS_URL = "https://raw.githubusercontent.com/shanmukhasaireddy13/vtop-releases/main/feature_flags.json";
    public static final String PREF_FEATURE_FLAGS_JSON = "cached_feature_flags_json";
    public static final String PREF_FLAGS_LAST_FETCH = "feature_flags_last_fetch_time";

    public static final String FLAG_MEALS_LAUNDRY = "enable_meals_laundry";
    public static final String FLAG_AI_CUSTOMIZER = "enable_ai_hostel_customizer";
    public static final String FLAG_IN_APP_UPDATES = "enable_in_app_updates";
    public static final String FLAG_MOODLE = "enable_moodle";
    public static final String FLAG_SMART_DND = "enable_smart_dnd";
    public static final String FLAG_CGPA_CALCULATOR = "enable_cgpa_calculator";
    public static final String FLAG_MAINTENANCE_MODE = "maintenance_mode";
    public static final String KEY_MAINTENANCE_MSG = "maintenance_message";

    private static JSONObject cachedFlags = null;

    public static void fetchRemoteFlags(@NonNull Context context) {
        Observable.fromCallable(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder().build();
                Request request = new Request.Builder()
                        .url(REMOTE_FLAGS_URL)
                        .header("Cache-Control", "no-cache")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String jsonString = response.body().string();
                        JSONObject parsed = new JSONObject(jsonString);
                        saveCachedFlags(context, jsonString);
                        cachedFlags = parsed;
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching remote feature flags", e);
            }
            return false;
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(success -> {
            if (success) {
                Log.d(TAG, "Remote feature flags updated successfully.");
            }
        }, throwable -> Log.e(TAG, "Failed feature flags stream", throwable));
    }

    private static synchronized JSONObject getFlagsObject(Context context) {
        if (cachedFlags != null) return cachedFlags;
        if (context == null) return new JSONObject();

        SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
        String cachedJson = prefs.getString(PREF_FEATURE_FLAGS_JSON, null);

        if (cachedJson != null && !cachedJson.trim().isEmpty()) {
            try {
                cachedFlags = new JSONObject(cachedJson);
                return cachedFlags;
            } catch (Exception ignored) {
            }
        }

        // Fallback to bundled raw default flags
        try {
            InputStream is = context.getResources().openRawResource(R.raw.default_feature_flags);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            cachedFlags = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
            return cachedFlags;
        } catch (Exception e) {
            Log.e(TAG, "Error loading fallback default feature flags", e);
            cachedFlags = new JSONObject();
            return cachedFlags;
        }
    }

    private static void saveCachedFlags(Context context, String json) {
        if (context == null) return;
        SettingsRepository.getSharedPreferences(context)
                .edit()
                .putString(PREF_FEATURE_FLAGS_JSON, json)
                .putLong(PREF_FLAGS_LAST_FETCH, System.currentTimeMillis())
                .apply();
    }

    public static boolean isFeatureEnabled(Context context, String flagKey, boolean defaultVal) {
        try {
            JSONObject root = getFlagsObject(context);
            if (root.has("flags")) {
                JSONObject flags = root.getJSONObject("flags");
                return flags.optBoolean(flagKey, defaultVal);
            }
        } catch (Exception ignored) {
        }
        return defaultVal;
    }

    public static boolean isMealsLaundryEnabled(Context context) {
        return isFeatureEnabled(context, FLAG_MEALS_LAUNDRY, true);
    }

    public static boolean isAiCustomizerEnabled(Context context) {
        return isFeatureEnabled(context, FLAG_AI_CUSTOMIZER, true);
    }

    public static boolean isInAppUpdatesEnabled(Context context) {
        return isFeatureEnabled(context, FLAG_IN_APP_UPDATES, true);
    }

    public static boolean isMoodleEnabled(Context context) {
        return isFeatureEnabled(context, FLAG_MOODLE, true);
    }

    public static boolean isSmartDndEnabled(Context context) {
        return isFeatureEnabled(context, FLAG_SMART_DND, true);
    }

    public static boolean isMaintenanceMode(Context context) {
        return isFeatureEnabled(context, FLAG_MAINTENANCE_MODE, false);
    }

    public static String getMaintenanceMessage(Context context) {
        try {
            JSONObject root = getFlagsObject(context);
            if (root.has("flags")) {
                JSONObject flags = root.getJSONObject("flags");
                return flags.optString(KEY_MAINTENANCE_MSG, "VTOP servers are temporarily undergoing maintenance.");
            }
        } catch (Exception ignored) {
        }
        return "VTOP servers are temporarily undergoing maintenance.";
    }

    public static JSONObject getAnnouncement(Context context) {
        try {
            JSONObject root = getFlagsObject(context);
            if (root.has("announcement")) {
                JSONObject ann = root.getJSONObject("announcement");
                if (ann.optBoolean("enabled", false)) {
                    return ann;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
