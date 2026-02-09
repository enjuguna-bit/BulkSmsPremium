package com.afriserve.smsmanager.ui.inbox;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Java utilities to replace Kotlin Flow functionality
 */
public class JavaFlowUtils {
    
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    public static class StateFlow<T> {
        private final MutableLiveData<T> liveData;
        
        public StateFlow(T initialValue) {
            this.liveData = new MutableLiveData<>();
            this.liveData.setValue(initialValue);
        }
        
        public LiveData<T> asLiveData() {
            return liveData;
        }
        
        public void setValue(T value) {
            liveData.postValue(value);
        }
        
        public T getValue() {
            return liveData.getValue();
        }
    }
    
    public static class MutableStateFlow<T> extends StateFlow<T> {
        public MutableStateFlow(T initialValue) {
            super(initialValue);
        }
    }
    
    public static void runInBackground(Runnable runnable) {
        executor.execute(runnable);
    }
    
    public static void collectLatest(LiveData<?> liveData, OnCollectListener listener) {
        listener.onCollect(liveData.getValue());
    }
    
    public interface OnCollectListener {
        void onCollect(Object value);
    }
}
