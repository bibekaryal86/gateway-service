package gateway.service.proxy;

import gateway.service.logging.LogLogger;
import java.io.IOException;

import gateway.service.utils.Constants;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class ProxyLogging implements Interceptor {
  private static final LogLogger logger = LogLogger.getLogger(ProxyLogging.class);

  @NotNull
  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    String requestId = request.header(Constants.GATEWAY_REQUEST_DETAILS_KEY.name());
    long startTime = System.nanoTime();

    logger.info("[{}] OkHttp: [{}] [{}]", requestId, request.method(), request.url());

    Response response = chain.proceed(request);
    long endTime = System.nanoTime();

    logger.info(
        "[{}] OkHttp: [{}] in [{}s]",
        requestId,
        response.code(),
        String.format("%.2f", (endTime - startTime) / 1e9d));
    return response;
  }
}
