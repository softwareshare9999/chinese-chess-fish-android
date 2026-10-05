package com.zfdang.chess;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class ChessApp extends Application {
    private static ChessApp instance;

    private static Context appContext;

    /** Get the application context. */
    public static Context getContext() {
        return appContext;
    }

    public static String str(int resId) {
        return appContext != null ? appContext.getString(resId) : "";
    }

    public static String str(int resId, Object... args) {
        return appContext != null ? appContext.getString(resId, args) : "";
    }

    public ChessApp() {
        instance = this;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(applyVietnamese(base));
        appContext = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("vi"));
    }

    private static Context applyVietnamese(Context context) {
        Locale locale = new Locale("vi");
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }

    public static ChessApp getInstance() {
        return instance;
    }
}
