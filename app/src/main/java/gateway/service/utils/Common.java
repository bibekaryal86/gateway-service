package gateway.service.utils;

import gateway.service.dtos.GatewayRequestDetails;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;

public class Common {

  public static boolean isProduction() {
    return Constants.PRODUCTION_ENV.equalsIgnoreCase(
        CommonUtilities.getSystemEnvProperty(Constants.SPRING_PROFILES_ACTIVE));
  }

  public static String getRequestId(final GatewayRequestDetails gatewayRequestDetails) {
    return gatewayRequestDetails == null ? "!NULL_GRD!" : gatewayRequestDetails.getRequestId();
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
}
