package com.plexle.doveconnect;

import android.util.Log;
import java.lang.reflect.Method;

public class HiddenApiHelper {
    private static final String TAG = "DoveConnect";
    private static boolean initialized = false;

    public static synchronized void exemptAll() {
        if (initialized) return;
        initialized = true;
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);

            Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(
                    vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});

            Object vmRuntime = getRuntime.invoke(null);
            setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{"L"}});
            Log.d(TAG, "Hidden API exemptions enabled for all classes (L*)");
        } catch (Throwable t) {
            Log.w(TAG, "Standard meta-reflection hidden API exemption failed: " + t.getMessage());
        }
    }
}
