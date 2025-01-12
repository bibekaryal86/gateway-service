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
  private final String targetBaseUrl;
  private final String targetHost;
  private final Integer targetPort;

  public GatewayRequestDetails(
      final HttpMethod requestMethod,
      final String requestUri,
      final String apiName,
      final String clientId,
      final String targetBaseUrl,
      final String targetHost,
      final Integer targetPort) {
    this.requestId = UUID.randomUUID();
    this.requestMethod = requestMethod;
    this.requestUri = requestUri;
    this.apiName = apiName;
    this.clientId = clientId;
    this.targetBaseUrl = targetBaseUrl;
    this.targetHost = targetHost;
    this.targetPort = targetPort;
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

  public String getTargetBaseUrl() {
    return targetBaseUrl;
  }

  public String getTargetHost() {
    return targetHost;
  }

  public Integer getTargetPort() {
    return targetPort;
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
        + ", targetBaseUrl='"
        + targetBaseUrl
        + '\''
        + ", targetHost='"
        + targetHost
        + '\''
        + ", targetPort="
        + targetPort
        + '}';
  }
}
