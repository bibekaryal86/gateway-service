package gateway.service.dtos;

import io.netty.handler.codec.http.HttpMethod;
import java.io.Serializable;
import java.util.UUID;

public class GatewayRequestDetails implements Serializable {
  private final UUID requestId;
  private final HttpMethod requestMethod;
  private final String requestUri;
  private final String apiName;
  private final String clientId;

  public GatewayRequestDetails(
      final HttpMethod requestMethod,
      final String apiName,
      final String requestUri,
      final String clientId) {
    this.requestId = UUID.randomUUID();
    this.requestMethod = requestMethod;
    this.apiName = apiName;
    this.requestUri = requestUri;
    this.clientId = clientId;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public HttpMethod getRequestMethod() {
    return requestMethod;
  }

  public String getRequestUri() {
    return requestUri;
  }

  public String getApiName() {
    return apiName;
  }

  public String getClientId() {
    return clientId;
  }

  @Override
  public String toString() {
    return "GatewayRequestDetails{"
        + "requestId="
        + requestId
        + ", requestMethod="
        + requestMethod
        + ", requestUri='"
        + requestUri
        + '\''
        + ", apiName='"
        + apiName
        + '\''
        + ", clientId='"
        + clientId
        + '\''
        + '}';
  }
}
