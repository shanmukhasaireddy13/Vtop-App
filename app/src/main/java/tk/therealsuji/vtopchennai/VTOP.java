package tk.therealsuji.vtopchennai;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class VTOP extends Application {
    private static Context context;

    public static Context getContext() {
        return context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        DynamicColors.applyToActivitiesIfAvailable(this);

        int theme = SettingsRepository.getTheme(this);
        if (theme == SettingsRepository.THEME_DAY) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (theme == SettingsRepository.THEME_NIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
