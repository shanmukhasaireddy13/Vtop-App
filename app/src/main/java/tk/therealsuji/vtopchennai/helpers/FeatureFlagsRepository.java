package tk.therealsuji.vtopchennai.helpers;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.remoteconfig.ConfigUpdate;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import tk.therealsuji.vtopchennai.R;

public final class FeatureFlagsRepository {
    private static final String TAG = "FeatureFlags";

    public enum Feature {
        MEALS_LAUNDRY("enable_meals_laundry", true),
        AI_CUSTOMIZER("enable_ai_hostel_customizer", true),
        IN_APP_UPDATES("enable_in_app_updates", true),
        MOODLE("enable_moodle", true),
        SMART_DND("enable_smart_dnd", true),
        CGPA_CALCULATOR("enable_cgpa_calculator", true),
        MAINTENANCE_MODE("maintenance_mode", false);

        public final String key;
        public final boolean defaultValue;

        Feature(String key, boolean defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }
    }

    public static final String KEY_MAINTENANCE_MSG = "maintenance_message";
    private static final String DEFAULT_MAINTENANCE_MSG = "VTOP servers are temporarily undergoing maintenance.";

    private static FirebaseRemoteConfig remoteConfig;
    private static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();

    private FeatureFlagsRepository() {}

    public static void initAndFetch(@NonNull Context context) {
        initAndFetch(context, null);
    }

    public static void initAndFetch(@NonNull Context context, Runnable onFetched) {
        try {
            remoteConfig = FirebaseRemoteConfig.getInstance();
            FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(0)
                    .build();
            remoteConfig.setConfigSettingsAsync(configSettings);
            remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);

            if (onFetched != null) {
                addListener(onFetched);
            }

            remoteConfig.addOnConfigUpdateListener(new ConfigUpdateListener() {
                @Override
                public void onUpdate(@NonNull ConfigUpdate configUpdate) {
                    remoteConfig.activate().addOnCompleteListener(task -> notifyListeners(context));
                }

                @Override
                public void onError(@NonNull FirebaseRemoteConfigException error) {
                    Log.w(TAG, "Remote config error", error);
                }
            });

            remoteConfig.fetchAndActivate()
                    .addOnCompleteListener(task -> notifyListeners(context));
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase Remote Config", e);
        }
    }

    public static void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private static void notifyListeners(Context context) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(() -> {
                for (Runnable r : listeners) {
                    try {
                        r.run();
                    } catch (Exception ignored) {
                    }
                }
            });
        } else {
            for (Runnable r : listeners) {
                try {
                    r.run();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static boolean isEnabled(Feature feature) {
        try {
            if (remoteConfig == null) {
                remoteConfig = FirebaseRemoteConfig.getInstance();
            }
            if (remoteConfig != null) {
                return remoteConfig.getBoolean(feature.key);
            }
        } catch (Exception ignored) {
        }
        return feature.defaultValue;
    }

    public static boolean isMealsLaundryEnabled(Context context) {
        return isEnabled(Feature.MEALS_LAUNDRY);
    }

    public static boolean isAiCustomizerEnabled(Context context) {
        return isEnabled(Feature.AI_CUSTOMIZER);
    }

    public static boolean isInAppUpdatesEnabled(Context context) {
        return isEnabled(Feature.IN_APP_UPDATES);
    }

    public static boolean isMoodleEnabled(Context context) {
        return isEnabled(Feature.MOODLE);
    }

    public static boolean isSmartDndEnabled(Context context) {
        return isEnabled(Feature.SMART_DND);
    }

    public static boolean isMaintenanceMode(Context context) {
        return isEnabled(Feature.MAINTENANCE_MODE);
    }

    public static String getMaintenanceMessage(Context context) {
        try {
            if (remoteConfig == null) {
                remoteConfig = FirebaseRemoteConfig.getInstance();
            }
            if (remoteConfig != null) {
                String msg = remoteConfig.getString(KEY_MAINTENANCE_MSG);
                if (msg != null && !msg.trim().isEmpty()) {
                    return msg;
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_MAINTENANCE_MSG;
    }
}
