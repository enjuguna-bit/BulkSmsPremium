package okhttp3.gzip;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Small compatibility implementation used if the project references okhttp3.gzip.GzipRequestInterceptor
 * Adds the Accept-Encoding: gzip header to requests. It's intentionally minimal.
 */
public final class GzipRequestInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request request = original.newBuilder()
            .header("Accept-Encoding", "gzip")
            .build();
        return chain.proceed(request);
    }
}
