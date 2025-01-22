package gateway.service.proxy;

import gateway.service.dtos.GatewayRequestDetails;
import gateway.service.logging.LogLogger;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.Gateway;
import gateway.service.utils.Routes;
import gateway.service.utils.Validate;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;

public class SecurityConfig extends ChannelDuplexHandler {
  private static final LogLogger logger = LogLogger.getLogger(SecurityConfig.class);

  @Override
  public void channelRead(
      @NotNull final ChannelHandlerContext channelHandlerContext, @NotNull final Object object)
      throws Exception {
    if (object instanceof FullHttpRequest fullHttpRequest) {
      GatewayRequestDetails gatewayRequestDetails =
          channelHandlerContext.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();

      // check if uri is excluded from auth requirements or not needed to modify
      if (Routes.getAuthExclusions().stream()
          .anyMatch(gatewayRequestDetails.getRequestUriLessApiName()::startsWith)
              || Routes.getBasicAuthApis().stream().anyMatch(gatewayRequestDetails.getRequestUri()::startsWith)) {
        logger.debug("[{}] Excluded From Authorization Modification...", gatewayRequestDetails.getRequestId());
        super.channelRead(channelHandlerContext, fullHttpRequest);
        return;
      }

      if (!gatewayRequestDetails.getApiName().equals(Constants.THIS_APP_NAME)) {
        // check if there is auth header app id (to validate auth token)
        String authAppId = fullHttpRequest.headers().get(Constants.HEADER_AUTH_APPID);
        if (Common.isEmpty(authAppId)) {
          authAppId = fullHttpRequest.headers().get(Constants.HEADER_AUTH_APPID.toLowerCase());
        }
        final int authHeaderAppId = Common.parseIntNoEx(authAppId);

        if (authHeaderAppId <= 0) {
          logger.error("[{}] AppId Header Missing/Invalid...", gatewayRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
                  channelHandlerContext,
                  HttpResponseStatus.UNAUTHORIZED,
                  "Missing or Malformed AppId Header");
          return;
        }

        // check if there is an auth token
        String authHeader = fullHttpRequest.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (Common.isEmpty(authHeader)) {
          authHeader = fullHttpRequest.headers().get(HttpHeaderNames.AUTHORIZATION.toLowerCase());
        }
        if (Common.isEmpty(authHeader) || !authHeader.startsWith(Constants.BEARER_AUTH)) {
          logger.error("[{}] Auth Header Missing/Invalid...", gatewayRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
                  channelHandlerContext,
                  HttpResponseStatus.UNAUTHORIZED,
                  "Missing or Malformed Authorization Header");
          return;
        }

        // validate the auth token
        boolean isValid = Validate.validateToken(authHeader, authHeaderAppId);
        if (!isValid) {
          logger.error("[{}] Auth Token Not Valid...", gatewayRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
                  channelHandlerContext, HttpResponseStatus.UNAUTHORIZED, "Invalid Authorization Header");
          return;
        }
      }

      // update request with basic auth after token validated
      String appUsername =
          Routes.getAuthApps().get(gatewayRequestDetails.getApiName() + Constants.AUTH_APPS_USR);
      String appPassword =
          Routes.getAuthApps().get(gatewayRequestDetails.getApiName() + Constants.AUTH_APPS_PWD);

      if (Common.isEmpty(appUsername) || Common.isEmpty(appPassword)) {
        logger.error("[{}] Auth Credentials Not Found...", gatewayRequestDetails.getRequestId());
        Gateway.sendErrorResponse(
            channelHandlerContext,
            HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED,
            "Missing Auth Credentials");
        return;
      }

      fullHttpRequest
          .headers()
          .set(HttpHeaderNames.AUTHORIZATION, Common.getBasicAuth(appUsername, appPassword));
      logger.debug("[{}] Auth Header Updated...", gatewayRequestDetails.getRequestId());
    }

    super.channelRead(channelHandlerContext, object);
  }
}
