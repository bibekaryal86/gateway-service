package gateway.service.dtos;

import java.io.Serializable;
import java.util.UUID;

public class GatewayRequestDetails implements Serializable {
  private final UUID requestId;
  private final String requestMethod;
  private final String requestUri;

  public GatewayRequestDetails(final UUID requestId, final String requestMethod, final String requestUri) {
    this.requestId = requestId;
    this.requestMethod = requestMethod;
    this.requestUri = requestUri;
  }

  @Override
  public String toString() {
    return "GatewayRequestDetails: [" + this.requestId + ", " + this.requestMethod + ", " + this.requestUri + "]";
  }
}
