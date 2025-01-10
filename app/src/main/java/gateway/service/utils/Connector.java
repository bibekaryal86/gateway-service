package gateway.service.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import gateway.service.logging.LogLogger;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Connector {
  private static final LogLogger logger = LogLogger.getLogger(Connector.class);

  private static final OkHttpClient okHttpClient =
      new OkHttpClient.Builder()
          .connectTimeout(5, TimeUnit.SECONDS)
          .readTimeout(15, TimeUnit.SECONDS)
          .build();

  public static HttpResponse sendRequest(
      final String url,
      final String method,
      final String requestBody,
      final Map<String, String> headers,
      final String authorization) {
    logger.debug("HTTP Request Sent: [{}], [{}]", method, url);
    Request request = buildRequest(url, method, requestBody, headers, authorization);
    try (Response response = okHttpClient.newCall(request).execute()) {
      int responseCode = response.code();
      String responseBody = response.body() == null ? "" : response.body().string();
      logger.debug("HTTP Response Received: [{}], [{}], [{}]", method, url, responseCode);
      return new HttpResponse(responseCode, responseBody);
    } catch (Exception ex) {
      logger.error("HTTP Request Error: [{}]|[{}]", method, url, ex);
      try {
        Map<String, String> errorMap = Map.of("errMsg", ex.getMessage());
        return new HttpResponse(-1, Common.objectMapperProvider().writeValueAsString(errorMap));
      } catch (JsonProcessingException e) {
        return new HttpResponse(-1, "{\"errMsg\":\"HTTP Request/Response Serialization Error\"}");
      }
    }
  }

  private static Request buildRequest(
      final String url,
      final String method,
      final String requestBody,
      final Map<String, String> headers,
      final String authorization) {
    RequestBody body =
        Common.isEmpty(requestBody)
            ? null
            : RequestBody.create(requestBody, MediaType.parse("application/json"));
    Request.Builder requestBuilder = new Request.Builder().url(url).method(method, body);

    if (authorization != null && !authorization.isEmpty()) {
      requestBuilder.header("Authorization", authorization);
    }

    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        requestBuilder.header(entry.getKey(), entry.getValue());
      }
    }

    return requestBuilder.build();
  }

  public static String createBasicAuthHeader(final String username, final String password) {
    String credentials = username + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
  }

  public record HttpResponse(int statusCode, String responseBody) {}
}
