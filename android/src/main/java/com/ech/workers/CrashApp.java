package com.ech.workers;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashApp extends Application {
    public static final String FILE = "last_crash.txt";
    private Thread.UncaughtExceptionHandler sys;
    private Thread.UncaughtExceptionHandler mine;

    @Override
    public void onCreate() {
        super.onCreate();
        sys = Thread.getDefaultUncaughtExceptionHandler();
        mine = (t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), FILE));
                fo.write(("时间: " + new java.util.Date() + "\n线程: " + t.getName() + "\n" + sw).getBytes("UTF-8"));
                fo.close();
            } catch (Throwable ignored) {
            }
            if (sys != null) {
                sys.uncaughtException(t, e);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(mine);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle b) { }
            public void onActivityPostCreated(Activity a, Bundle b) { Thread.setDefaultUncaughtExceptionHandler(mine); }
            public void onActivityStarted(Activity a) { Thread.setDefaultUncaughtExceptionHandler(mine); }
            public void onActivityResumed(Activity a) { Thread.setDefaultUncaughtExceptionHandler(mine); }
            public void onActivityPaused(Activity a) { }
            public void onActivityStopped(Activity a) { }
            public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            public void onActivityDestroyed(Activity a) { }
        });
    }
}
