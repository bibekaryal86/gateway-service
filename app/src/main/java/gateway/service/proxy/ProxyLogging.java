package gateway.service.proxy;

import gateway.service.logging.LogLogger;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ProxyLogging implements Interceptor {
    private static final LogLogger logger = LogLogger.getLogger(ProxyLogging.class);

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        long startTime = System.nanoTime();

        logger.info("OkHttp Request: [{}] [{}]", request.method(), request.url());

        Response response = chain.proceed(request);
        long endTime = System.nanoTime();

        logger.info("OkHttp Response: [{}] [{}] [{}] in [{}s]", request.method(), request.url(), response.code(), String.format("%.2f", (endTime - startTime) / 1e9d));
        return response;
    }
}
