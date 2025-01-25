package gateway.service.dtos;

import io.netty.handler.codec.http.HttpMethod;
import java.io.Serializable;
import java.util.UUID;

public class GatewayRequestDetails implements Serializable {
  private final String requestId;
  private final HttpMethod requestMethod;
  private final String requestUri;
  private final String requestUriLessApiName;
  private final String apiName;
  private final String clientId;
  private final String targetBaseUrl;

  private final long startTime;

  public GatewayRequestDetails(
      final HttpMethod requestMethod,
      final String requestUri,
      final String apiName,
      final String clientId,
      final String targetBaseUrl,
      final long startTime) {
    this.requestId = UUID.randomUUID().toString();
    this.requestMethod = requestMethod;
    this.requestUri = requestUri;
    this.apiName = apiName;
    this.requestUriLessApiName = this.setRequestUriLessApiName(requestUri, apiName);
    this.clientId = clientId;
    this.targetBaseUrl = targetBaseUrl;
    this.startTime = startTime;
  }

  public String getRequestId() {
    return requestId;
  }

  public HttpMethod getRequestMethod() {
    return requestMethod;
  }

  public String getRequestUri() {
    return requestUri;
  }

  public String getRequestUriLessApiName() {
    return requestUriLessApiName;
  }

  public String getApiName() {
    return apiName;
  }

  public String getClientId() {
    return clientId;
  }

  public String getTargetBaseUrl() {
    return targetBaseUrl;
  }

  public long getStartTime() {
    return startTime;
  }

  // transform /gatewaysvc/tests/api to /tests/api
  private String setRequestUriLessApiName(final String requestUri, final String apiName) {
    return requestUri.replace("/" + apiName, "");
  }

  @Override
  public String toString() {
    return "GatewayRequestDetails{"
        + "apiName="
        + '\''
        + apiName
        + '\''
        + ", requestMethod="
        + '\''
        + requestMethod
        + '\''
        + ", requestUri="
        + '\''
        + requestUri
        + '\''
        + ", targetBaseUrl="
        + '\''
        + targetBaseUrl
        + '\''
        + ", clientId="
        + '\''
        + clientId
        + '\''
        + '}';
  }
}
