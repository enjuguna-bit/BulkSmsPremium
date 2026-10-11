package com.afriserve.smsmanager.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.afriserve.smsmanager.BuildConfig;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * MemoryManager — v2
 *
 * Changes vs v1:
 *  - Removed the mutable singleton ({@code getInstance()}).  Use Hilt or
 *    create instances per-component to avoid class-loader-level leaks.
 *  - Removed {@code System.gc()} / {@code System.runFinalization()} calls.
 *    These are not guaranteed, waste CPU, and mask rather than fix leaks.
 *  - {@link #checkMemoryStatus()} now uses the {@link Context} passed at
 *    construction time (already the application context) and is safe to
 *    call from any thread.
 *  - {@link LifecycleAwareCleanup} and {@link FragmentObserverCleanup} are
 *    unchanged (they were already correct).
 *  - {@link MemoryLeakDetector} stripped of its redundant GC calls; in
 *    DEBUG builds it now simply logs available memory.
 */
public final class MemoryManager {

    private static final String TAG = "MemoryManager";

    /** Warn when device has fewer than this many megabytes of free RAM. */
    private static final long MEMORY_WARNING_THRESHOLD_MB = 80L;

    private final Context appContext;
    private final ConcurrentHashMap<String, WeakReference<Object>> trackedObjects
            = new ConcurrentHashMap<>();

    public MemoryManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ── Object tracking (debug / leak investigation) ──────────────────────────

    public void trackObject(String tag, Object obj) {
        if (obj == null || tag == null) return;
        trackedObjects.put(tag, new WeakReference<>(obj));
        Log.d(TAG, "Tracking: " + tag);
    }

    public void untrackObject(String tag) {
        trackedObjects.remove(tag);
    }

    public void cleanupTrackedObjects() {
        trackedObjects.clear();
        Log.d(TAG, "All tracked objects cleared");
    }

    // ── Memory status ─────────────────────────────────────────────────────────

    public void checkMemoryStatus() {
        ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;

        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        long availMB = info.availMem / 1_048_576L;

        if (availMB < MEMORY_WARNING_THRESHOLD_MB) {
            Log.w(TAG, "Low RAM: " + availMB + " MB available. Consider releasing caches.");
        } else {
            Log.d(TAG, "RAM OK: " + availMB + " MB available");
        }
    }

    public MemoryStats getMemoryStats() {
        ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo sysInfo = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(sysInfo);

        Runtime rt = Runtime.getRuntime();
        MemoryStats s = new MemoryStats();
        s.availableMemoryMB  = sysInfo.availMem  / 1_048_576L;
        s.totalMemoryMB      = rt.totalMemory()  / 1_048_576L;
        s.freeMemoryMB       = rt.freeMemory()   / 1_048_576L;
        s.maxMemoryMB        = rt.maxMemory()    / 1_048_576L;
        s.usedMemoryMB       = s.totalMemoryMB - s.freeMemoryMB;
        s.trackedObjectsCount = trackedObjects.size();
        return s;
    }

    // ── Static Rx helpers ─────────────────────────────────────────────────────

    public static void disposeSafely(CompositeDisposable d) {
        if (d != null && !d.isDisposed()) d.dispose();
    }

    public static void disposeSafely(Disposable d) {
        if (d != null && !d.isDisposed()) d.dispose();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void removeObserversSafely(LiveData<?> liveData, Observer<?> observer) {
        if (liveData != null && observer != null) {
            try {
                ((LiveData) liveData).removeObserver((Observer) observer);
            } catch (Exception e) {
                Log.w(TAG, "removeObserver failed", e);
            }
        }
    }

    // ── Inner helpers ─────────────────────────────────────────────────────────

    /** Tracks observeForever registrations and removes them on cleanup(). */
    public static final class FragmentObserverCleanup {
        private final List<Observer<?>>  observers    = new ArrayList<>();
        private final List<LiveData<?>>  liveDataList = new ArrayList<>();

        public <T> void observe(@NonNull LiveData<T> ld, @NonNull Observer<T> obs) {
            ld.observeForever(obs);
            observers.add(obs);
            liveDataList.add(ld);
        }

        public void cleanup() {
            for (int i = 0; i < observers.size(); i++) {
                removeObserversSafely(liveDataList.get(i), observers.get(i));
            }
            observers.clear();
            liveDataList.clear();
        }
    }

    /** Combines Rx disposable management and observeForever cleanup. */
    public static final class LifecycleAwareCleanup {
        private final CompositeDisposable disposables   = new CompositeDisposable();
        private final List<Observer<?>>   observers     = new ArrayList<>();
        private final List<LiveData<?>>   liveDataList  = new ArrayList<>();

        public void addDisposable(Disposable d) {
            if (d != null) disposables.add(d);
        }

        public <T> void observe(@NonNull LiveData<T> ld, @NonNull Observer<T> obs) {
            ld.observeForever(obs);
            observers.add(obs);
            liveDataList.add(ld);
        }

        public void cleanup() {
            disposeSafely(disposables);
            for (int i = 0; i < observers.size(); i++) {
                removeObserversSafely(liveDataList.get(i), observers.get(i));
            }
            observers.clear();
            liveDataList.clear();
        }
    }

    /** Debug-only memory snapshot logger. */
    public static final class MemoryLeakDetector {
        public static void detectLeaks(@NonNull Context context) {
            if (!BuildConfig.DEBUG) return;
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            long availMB = info.availMem / 1_048_576L;
            Runtime rt = Runtime.getRuntime();
            long heapUsed = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L;
            Log.d("MemoryLeakDetector",
                    "Device RAM free: " + availMB + " MB | Heap used: " + heapUsed + " MB");
        }
    }

    // ── Data class ────────────────────────────────────────────────────────────

    public static final class MemoryStats {
        public long availableMemoryMB;
        public long totalMemoryMB;
        public long freeMemoryMB;
        public long maxMemoryMB;
        public long usedMemoryMB;
        public int  trackedObjectsCount;

        @Override
        public String toString() {
            return "MemoryStats{avail=" + availableMemoryMB
                    + "MB, used=" + usedMemoryMB
                    + "MB, max=" + maxMemoryMB + "MB}";
        }
    }
}
