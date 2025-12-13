package com.github.catvod.crawler;

import android.util.Log;

public class SpiderDebug {
    public static final String TAG = "SpiderDebug";

    public static void log(String msg) {
        Log.d(TAG, msg);
    }

    public static void log(Throwable th) {
        Log.d(TAG, "Exception", th);
    }
}
