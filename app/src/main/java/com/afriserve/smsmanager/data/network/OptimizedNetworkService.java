package com.afriserve.smsmanager.data.network;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.afriserve.smsmanager.BuildConfig;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.gzip.GzipRequestInterceptor;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import android.content.Context;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Optimized network service with GZIP compression, ETag support, and pagination
 * Implements efficient network communication for offline-first architecture
 */
@Singleton
public class OptimizedNetworkService {
    
    private static final String TAG = "OptimizedNetworkService";
    private static final String BASE_URL = "https://api.bulksms.com/v1/";
    private static final int CACHE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int CONNECT_TIMEOUT = 30; // seconds
    private static final int READ_TIMEOUT = 60; // seconds
    private static final int WRITE_TIMEOUT = 60; // seconds
    
    private final OkHttpClient okHttpClient;
    private final Retrofit retrofit;
    private final Gson gson;
    private final Map<String, String> eTagCache = new HashMap<>();
    
    @Inject
    public OptimizedNetworkService(@ApplicationContext Context context) {
        this.gson = createGson();
        this.okHttpClient = createOkHttpClient(context);
        this.retrofit = createRetrofit();
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "OptimizedNetworkService initialized with GZIP, ETag, and pagination support");
        }
    }
    
    /**
     * Create optimized OkHttpClient with caching and compression
     */
    private OkHttpClient createOkHttpClient(Context context) {
        Cache cache = new Cache(context.getCacheDir(), CACHE_SIZE);
        
        // ETag interceptor for conditional requests
        Interceptor eTagInterceptor = chain -> {
            Request originalRequest = chain.request();
            String url = originalRequest.url().toString();
            
            // Add If-None-Match header if we have an ETag
            String eTag = eTagCache.get(url);
            if (eTag != null) {
                originalRequest = originalRequest.newBuilder()
                    .addHeader("If-None-Match", eTag)
                    .build();
            }
            
            Response response = chain.proceed(originalRequest);
            
            // Cache ETag from response
            String responseETag = response.header("ETag");
            if (responseETag != null) {
                eTagCache.put(url, responseETag);
            }
            
            return response;
        };
        
        // If-Modified-Since interceptor for time-based caching
        Interceptor ifModifiedSinceInterceptor = chain -> {
            Request originalRequest = chain.request();
            String url = originalRequest.url().toString();
            
            // Add If-Modified-Since header for cached resources
            // This would typically come from database metadata
            long lastModified = getLastModifiedTimestamp(url);
            if (lastModified > 0) {
                originalRequest = originalRequest.newBuilder()
                    .addHeader("If-Modified-Since", formatHttpDate(lastModified))
                    .build();
            }
            
            return chain.proceed(originalRequest);
        };
        
        // Network connectivity interceptor
        Interceptor connectivityInterceptor = chain -> {
            Request request = chain.request();
            
            if (!isNetworkAvailable(context)) {
                // Return cached response if available, otherwise fail
                Response cacheResponse = chain.proceed(request.newBuilder()
                    .header("Cache-Control", "only-if-cached")
                    .build());
                
                if (cacheResponse.cacheResponse() != null) {
                    return cacheResponse;
                } else {
                    throw new IOException("No network connection and no cached data available");
                }
            }
            
            return chain.proceed(request);
        };
        
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(new GzipRequestInterceptor()) // Enable GZIP compression
            .addInterceptor(eTagInterceptor)
            .addInterceptor(ifModifiedSinceInterceptor)
            .addInterceptor(connectivityInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true);

        if (BuildConfig.DEBUG) {
            // Logging interceptor for debug builds only
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            clientBuilder.addNetworkInterceptor(loggingInterceptor);
        }

        return clientBuilder.build();
    }
    
    /**
     * Create Gson instance with date formatting
     */
    private Gson createGson() {
        return new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create();
    }
    
    /**
     * Create Retrofit instance
     */
    private Retrofit createRetrofit() {
        return new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build();
    }
    
    /**
     * Get API service
     */
    public SmsApiService getSmsApiService() {
        return retrofit.create(SmsApiService.class);
    }
    
    /**
     * Get paginated messages with network optimizations
     */
    public retrofit2.Call<SmsApiService.PaginatedResponse<SmsApiService.SmsNetworkEntity>> getMessagesPaginated(
        int page, 
        int pageSize, 
        String lastModifiedSince
    ) {
        SmsApiService service = getSmsApiService();
        return service.getMessagesPaginated(page, pageSize, lastModifiedSince);
    }
    
    /**
     * Upload message with GZIP compression
     */
    public retrofit2.Call<SmsApiService.SmsNetworkEntity> uploadMessage(SmsApiService.SmsNetworkEntity message) {
        SmsApiService service = getSmsApiService();
        return service.uploadMessage(message, "gzip");
    }
    
    /**
     * Sync messages with ETag support
     */
    public retrofit2.Call<SmsApiService.PaginatedResponse<SmsApiService.SmsNetworkEntity>> syncMessages(
        String eTag, 
        long ifModifiedSince
    ) {
        SmsApiService service = getSmsApiService();
        // SmsApiService expects (String eTag, String ifModifiedSince, int limit)
        return service.syncMessages(eTag, formatHttpDate(ifModifiedSince), 100);
    }
    
    /**
     * Get last modified timestamp for URL
     * This would typically come from database metadata
     */
    private long getLastModifiedTimestamp(String url) {
        // TODO: Implement database lookup for last modified timestamp
        // For now, return 0 to disable If-Modified-Since
        return 0;
    }
    
    /**
     * Format date for HTTP headers
     */
    private String formatHttpDate(long timestamp) {
        return java.text.DateFormat.getDateTimeInstance()
            .format(new java.util.Date(timestamp));
    }
    
    /**
     * Check network availability
     */
    private boolean isNetworkAvailable(Context context) {
        android.net.ConnectivityManager cm = 
            (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (cm != null) {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }
    
    /**
     * Clear ETag cache
     */
    public void clearETagCache() {
        eTagCache.clear();
        Log.d(TAG, "ETag cache cleared");
    }
    
    /**
     * Get network statistics
     */
    public NetworkStatistics getNetworkStatistics() {
        NetworkStatistics stats = new NetworkStatistics();
        stats.eTagCacheSize = eTagCache.size();
        stats.isGzipEnabled = true;
        stats.isCacheEnabled = true;
        stats.connectTimeoutSeconds = CONNECT_TIMEOUT;
        stats.readTimeoutSeconds = READ_TIMEOUT;
        return stats;
    }
    
    /**
     * Network statistics data class
     */
    public static class NetworkStatistics {
        public int eTagCacheSize;
        public boolean isGzipEnabled;
        public boolean isCacheEnabled;
        public int connectTimeoutSeconds;
        public int readTimeoutSeconds;
    }
}
