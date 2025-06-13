package gateway.service.dtos;

import io.github.bibekaryal86.shdsvc.dtos.ResponseMetadata;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class GatewayDbResponseDetails implements Serializable {
  private final String requestId;
  private final List<Map<String, Object>> results;
  private final ResponseMetadata responseMetadata;

  public GatewayDbResponseDetails(
      final String requestId,
      final List<Map<String, Object>> results,
      final ResponseMetadata responseMetadata) {
    this.requestId = requestId;
    this.results = results;
    this.responseMetadata = responseMetadata;
  }

  public String getRequestId() {
    return requestId;
  }

  public List<Map<String, Object>> getResults() {
    return results;
  }

  public ResponseMetadata getResponseMetadata() {
    return responseMetadata;
  }

  @Override
  public String toString() {
    return "GatewayDbResponseDetails{"
        + "requestId='"
        + requestId
        + '\''
        + ", results="
        + results
        + ", responseMetadata="
        + responseMetadata
        + '}';
  }
}
