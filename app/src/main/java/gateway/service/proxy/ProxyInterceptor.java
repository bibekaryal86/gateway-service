package gateway.service.proxy;

import gateway.service.logging.LogLogger;
import gateway.service.utils.Constants;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class ProxyInterceptor implements Interceptor {
  private static final LogLogger logger = LogLogger.getLogger(ProxyInterceptor.class);

  @NotNull
  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    String requestId = request.header(Constants.GATEWAY_REQUEST_DETAILS_KEY.name());
    long startTime = System.nanoTime();

    logger.info("[{}] Request OUT: [{}] [{}]", requestId, request.method(), request.url());

    Response response = chain.proceed(request);
    long endTime = System.nanoTime();

    logger.info(
        "[{}] Response IN: [{}] in [{}s]",
        requestId,
        response.code(),
        String.format("%.2f", (endTime - startTime) / 1e9d));
    return response;
  }
}
