package gateway.service.utils;

import gateway.service.dtos.GatewayDbRequestDetails;
import gateway.service.dtos.GatewayRequestDetails;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Common {

  public static void validateInitArgs() {
    final Map<String, String> properties =
        CommonUtilities.getSystemEnvProperties(Constants.ENV_KEY_NAMES);
    final List<String> requiredEnvProperties =
        Constants.ENV_KEY_NAMES.stream().filter(key -> !Constants.ENV_PORT.equals(key)).toList();
    final List<String> errors =
        requiredEnvProperties.stream().filter(key -> properties.get(key) == null).toList();
    if (!errors.isEmpty()) {
      throw new IllegalStateException(
          "One or more environment configurations could not be accessed...");
    }
  }

  public static boolean isProduction() {
    return Constants.PRODUCTION_ENV.equalsIgnoreCase(
        CommonUtilities.getSystemEnvProperty(Constants.SPRING_PROFILES_ACTIVE));
  }

  public static String getRequestId(final GatewayRequestDetails gatewayRequestDetails) {
    return gatewayRequestDetails == null ? "!NULL_GRD!" : gatewayRequestDetails.getRequestId();
  }

  public static String getDbRequestId(final GatewayDbRequestDetails gatewayDbRequestDetails) {
    return gatewayDbRequestDetails == null
        ? "!NULL_GdbRD!"
        : gatewayDbRequestDetails.getRequestId();
  }

  public static CorsHandler newCorsHandler() {
    return new CorsHandler(
        CorsConfigBuilder.forAnyOrigin()
            .allowedRequestMethods(
                HttpMethod.GET,
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.PATCH,
                HttpMethod.DELETE,
                HttpMethod.OPTIONS)
            .allowedRequestHeaders(
                HttpHeaderNames.AUTHORIZATION,
                HttpHeaderNames.CONTENT_TYPE,
                HttpHeaderNames.CONTENT_LENGTH,
                "X-Authorization-AppId")
            .build());
  }

  public static String extractClientId(final ChannelHandlerContext channelHandlerContext) {
    String remoteAddress = channelHandlerContext.channel().remoteAddress().toString();

    if (remoteAddress.contains("/")) {
      remoteAddress = remoteAddress.substring(remoteAddress.indexOf("/") + 1);
    }
    if (remoteAddress.contains(":")) {
      remoteAddress = remoteAddress.substring(0, remoteAddress.indexOf(":"));
    }

    return remoteAddress;
  }

  public static String validateGatewayDbRequest(
      final GatewayDbRequestDetails gatewayDbRequestDetails) {
    final List<String> errors = new ArrayList<>();

    if (CommonUtilities.isEmpty(gatewayDbRequestDetails.getDatabase())) {
      errors.add("Database is missing");
    }
    if (CommonUtilities.isEmpty(gatewayDbRequestDetails.getAction())) {
      errors.add("Action is missing");
    } else if (!gatewayDbRequestDetails.getAction().matches("(?i)CREATE|READ|UPDATE|DELETE|RAW")) {
      errors.add("Action is invalid");
    }
    if (CommonUtilities.isEmpty(gatewayDbRequestDetails.getTable())) {
      errors.add("Table is missing");
    }
    if (gatewayDbRequestDetails.getGatewayDbRequestMetadata() != null
        && gatewayDbRequestDetails.getGatewayDbRequestMetadata().getSortDirection() != null
        && !gatewayDbRequestDetails
            .getGatewayDbRequestMetadata()
            .getSortDirection()
            .matches("(?i)ASC|DESC")) {
      errors.add("Sort Direction is invalid");
    }

    return errors.isEmpty() ? "" : String.join(";", errors);
  }
}
