package gateway.service.dtos;

import io.github.bibekaryal86.shdsvc.dtos.ResponseMetadata;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record GatewayDbResponseDetails(
    String requestId,
    List<Map<String, Object>> results,
    GatewayDbRequestDetails.GatewayDbRequestMetadata requestMetadata,
    ResponseMetadata responseMetadata)
    implements Serializable {

  @Override
  public String toString() {
    return "GatewayDbResponseDetails{"
        + "requestId='"
        + requestId
        + '\''
        + ", results="
        + results
        + ", requestMetadata="
        + requestMetadata
        + ", responseMetadata="
        + responseMetadata
        + '}';
  }
}
