package gateway.service.utils;

import gateway.service.dtos.GatewayRequestDetails;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import java.util.regex.Pattern;

public class Common {

  private static final Pattern PERMISSIONS_URL_PATTERN;
  private static final String[] CORS_ALLOWED_HEADERS;

  static {
    PERMISSIONS_URL_PATTERN =
        Pattern.compile(CommonUtilities.getSystemEnvProperty(Constants.CHECK_PERMISSIONS_MATCHER));
    CORS_ALLOWED_HEADERS =
        CommonUtilities.getSystemEnvProperty(Constants.CORS_ALLOWED_HEADERS).split(",");
  }

  public static String getRequestId(final GatewayRequestDetails gatewayRequestDetails) {
    return gatewayRequestDetails == null ? "!NULL_GRD!" : gatewayRequestDetails.getRequestId();
  }

  public static boolean isCheckPermissions(final FullHttpRequest fullHttpRequest) {
    return PERMISSIONS_URL_PATTERN.matcher(fullHttpRequest.uri()).matches();
  }

  public static CorsHandler newCorsHandler() {
    return new CorsHandler(
        CorsConfigBuilder.forAnyOrigin()
            .allowCredentials()
            .allowedRequestMethods(
                HttpMethod.GET,
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.PATCH,
                HttpMethod.DELETE,
                HttpMethod.OPTIONS)
            .allowedRequestHeaders(CORS_ALLOWED_HEADERS)
            .build());
  }
}
