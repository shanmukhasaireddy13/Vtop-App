package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import tk.therealsuji.vtopchennai.R;

public class FeatureFlagsRepository {
    private static final String TAG = "FeatureFlags";

    public static final String FLAG_MEALS_LAUNDRY = "enable_meals_laundry";
    public static final String FLAG_AI_CUSTOMIZER = "enable_ai_hostel_customizer";
    public static final String FLAG_IN_APP_UPDATES = "enable_in_app_updates";
    public static final String FLAG_MOODLE = "enable_moodle";
    public static final String FLAG_SMART_DND = "enable_smart_dnd";
    public static final String FLAG_CGPA_CALCULATOR = "enable_cgpa_calculator";
    public static final String FLAG_MAINTENANCE_MODE = "maintenance_mode";
    public static final String KEY_MAINTENANCE_MSG = "maintenance_message";

    private static FirebaseRemoteConfig remoteConfig;

    public static void initAndFetch(@NonNull Context context) {
        try {
            remoteConfig = FirebaseRemoteConfig.getInstance();
            FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build();
            remoteConfig.setConfigSettingsAsync(configSettings);
            remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);

            remoteConfig.fetchAndActivate()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Firebase Remote Config fetch and activate succeeded.");
                        } else {
                            Log.w(TAG, "Firebase Remote Config fetch failed, using defaults/cache.");
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase Remote Config", e);
        }
    }

    private static FirebaseRemoteConfig getConfig() {
        if (remoteConfig == null) {
            try {
                remoteConfig = FirebaseRemoteConfig.getInstance();
            } catch (Exception ignored) {
            }
        }
        return remoteConfig;
    }

    public static boolean isFeatureEnabled(Context context, String flagKey, boolean defaultVal) {
        try {
            FirebaseRemoteConfig config = getConfig();
            if (config != null) {
                return config.getBoolean(flagKey);
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
            FirebaseRemoteConfig config = getConfig();
            if (config != null) {
                String msg = config.getString(KEY_MAINTENANCE_MSG);
                if (msg != null && !msg.trim().isEmpty()) {
                    return msg;
                }
            }
        } catch (Exception ignored) {
        }
        return "VTOP servers are temporarily undergoing maintenance.";
    }
}
