package com.afriserve.smsmanager.utils;

import android.util.Log;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import com.afriserve.smsmanager.BuildConfig;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Memory management utility for preventing memory leaks
 * Provides lifecycle-aware cleanup and memory monitoring
 */
public class MemoryManager {
    
    private static final String TAG = "MemoryManager";
    private static final int MEMORY_WARNING_THRESHOLD_MB = 100; // Warn when less than 100MB available
    
    private static MemoryManager instance;
    private final Context context;
    private final ConcurrentHashMap<String, WeakReference<Object>> trackedObjects;
    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    
    private MemoryManager(Context context) {
        this.context = context.getApplicationContext();
        this.trackedObjects = new ConcurrentHashMap<>();
    }
    
    public static synchronized MemoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new MemoryManager(context);
        }
        return instance;
    }
    
    /**
     * Track an object for memory leak detection
     */
    public void trackObject(String tag, Object object) {
        if (object == null) return;
        
        trackedObjects.put(tag, new WeakReference<>(object));
        Log.d(TAG, "Tracked object: " + tag);
    }
    
    /**
     * Untrack an object
     */
    public void untrackObject(String tag) {
        WeakReference<Object> ref = trackedObjects.remove(tag);
        if (ref != null && ref.get() != null) {
            Log.d(TAG, "Untracked object: " + tag);
        }
    }
    
    /**
     * Cleanup tracked objects
     */
    public void cleanupTrackedObjects() {
        trackedObjects.clear();
        Log.d(TAG, "Cleaned up all tracked objects");
    }
    
    /**
     * Check available memory and log warnings
     */
    public void checkMemoryStatus() {
        if (context == null) return;
        
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return;
        
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        
        long availableMegs = memoryInfo.availMem / 1048576L; // Convert to MB
        
        Log.d(TAG, "Available memory: " + availableMegs + "MB");
        
        if (availableMegs < MEMORY_WARNING_THRESHOLD_MB) {
            Log.w(TAG, "Low memory warning: " + availableMegs + "MB available");
            
            // Trigger garbage collection
            System.gc();
            
            // Log tracked objects for debugging
            logTrackedObjects();
        }
    }
    
    /**
     * Log all tracked objects for debugging
     */
    public void logTrackedObjects() {
        Log.d(TAG, "Currently tracked objects:");
        for (String tag : trackedObjects.keySet()) {
            WeakReference<Object> ref = trackedObjects.get(tag);
            if (ref != null && ref.get() != null) {
                Log.d(TAG, "  - " + tag + ": " + ref.get().getClass().getSimpleName());
            } else {
                Log.d(TAG, "  - " + tag + ": (GC'd)");
            }
        }
    }
    
    /**
     * Cleanup RxJava subscriptions safely
     */
    public static void disposeSafely(CompositeDisposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            Log.d(TAG, "Disposed CompositeDisposable");
        }
    }
    
    /**
     * Cleanup single disposable safely
     */
    public static void disposeSafely(Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            Log.d(TAG, "Disposed Disposable");
        }
    }
    
    /**
     * Remove all observeForever observers from LiveData
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void removeObserversSafely(androidx.lifecycle.LiveData<?> liveData, androidx.lifecycle.Observer<?> observer) {
        if (liveData != null && observer != null) {
            try {
                ((androidx.lifecycle.LiveData) liveData).removeObserver((androidx.lifecycle.Observer) observer);
                Log.d(TAG, "Removed observer from LiveData");
            } catch (Exception e) {
                Log.w(TAG, "Failed to remove observer", e);
            }
        }
    }
    
    /**
     * Cleanup Fragment observers in onCleared
     */
    public static class FragmentObserverCleanup {
        private final java.util.List<androidx.lifecycle.Observer<?>> observers = new java.util.ArrayList<>();
        private final java.util.List<androidx.lifecycle.LiveData<?>> liveDataList = new java.util.ArrayList<>();
        
        public <T> void observe(androidx.lifecycle.LiveData<T> liveData, androidx.lifecycle.Observer<T> observer) {
            if (liveData != null && observer != null) {
                liveData.observeForever(observer);
                observers.add(observer);
                liveDataList.add(liveData);
            }
        }
        
        public void cleanup() {
            for (int i = 0; i < observers.size(); i++) {
                removeObserversSafely(liveDataList.get(i), observers.get(i));
            }
            observers.clear();
            liveDataList.clear();
        }
    }
    
    /**
     * Memory leak detector for development
     */
    public static class MemoryLeakDetector {
        private static final String TAG = "MemoryLeakDetector";
        
        public static void detectLeaks(Context context) {
            if (BuildConfig.DEBUG) {
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);
                
                long availableMegs = memoryInfo.availMem / 1048576L;
                Log.d(TAG, "Memory available: " + availableMegs + "MB");
                
                // Force garbage collection
                System.gc();
                System.runFinalization();
                System.gc();
                
                // Check again
                activityManager.getMemoryInfo(memoryInfo);
                long afterGCMegs = memoryInfo.availMem / 1048576L;
                Log.d(TAG, "Memory available after GC: " + afterGCMegs + "MB");
                
                if (afterGCMegs < availableMegs - 10) {
                    Log.w(TAG, "Potential memory leak detected - memory not freed after GC");
                }
            }
        }
    }
    
    /**
     * Lifecycle-aware cleanup helper
     */
    public static class LifecycleAwareCleanup {
        private final CompositeDisposable disposables = new CompositeDisposable();
        private final java.util.List<androidx.lifecycle.Observer<?>> observers = new java.util.ArrayList<>();
        private final java.util.List<androidx.lifecycle.LiveData<?>> liveDataList = new java.util.ArrayList<>();
        
        public void addDisposable(Disposable disposable) {
            if (disposable != null) {
                disposables.add(disposable);
            }
        }
        
        public <T> void observe(androidx.lifecycle.LiveData<T> liveData, androidx.lifecycle.Observer<T> observer) {
            if (liveData != null && observer != null) {
                liveData.observeForever(observer);
                observers.add(observer);
                liveDataList.add(liveData);
            }
        }
        
        public void cleanup() {
            disposeSafely(disposables);
            
            for (int i = 0; i < observers.size(); i++) {
                androidx.lifecycle.Observer<?> observer = observers.get(i);
                androidx.lifecycle.LiveData<?> liveData = liveDataList.get(i);
                removeObserversSafely(liveData, observer);
            }
            
            observers.clear();
            liveDataList.clear();
        }
    }
    
    /**
     * Get memory usage statistics
     */
    public MemoryStats getMemoryStats() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        
        Runtime runtime = Runtime.getRuntime();
        
        MemoryStats stats = new MemoryStats();
        stats.availableMemoryMB = memoryInfo.availMem / 1048576L;
        stats.totalMemoryMB = runtime.totalMemory() / 1048576L;
        stats.freeMemoryMB = runtime.freeMemory() / 1048576L;
        stats.maxMemoryMB = runtime.maxMemory() / 1048576L;
        stats.usedMemoryMB = stats.totalMemoryMB - stats.freeMemoryMB;
        stats.trackedObjectsCount = trackedObjects.size();
        
        return stats;
    }
    
    /**
     * Memory statistics data class
     */
    public static class MemoryStats {
        public long availableMemoryMB;
        public long totalMemoryMB;
        public long freeMemoryMB;
        public long maxMemoryMB;
        public long usedMemoryMB;
        public int trackedObjectsCount;
        
        @Override
        public String toString() {
            return "MemoryStats{" +
                    "availableMemoryMB=" + availableMemoryMB +
                    ", totalMemoryMB=" + totalMemoryMB +
                    ", freeMemoryMB=" + freeMemoryMB +
                    ", maxMemoryMB=" + maxMemoryMB +
                    ", usedMemoryMB=" + usedMemoryMB +
                    ", trackedObjectsCount=" + trackedObjectsCount +
                    '}';
        }
    }
}
