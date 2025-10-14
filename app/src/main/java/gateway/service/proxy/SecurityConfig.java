package gateway.service.proxy;

import gateway.service.dtos.AuthToken;
import gateway.service.dtos.GatewayRequestDetails;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.Gateway;
import gateway.service.utils.Routes;
import gateway.service.utils.Validate;
import io.github.bibekaryal86.shdsvc.Secrets;
import io.github.bibekaryal86.shdsvc.dtos.HttpResponse;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityConfig extends ChannelDuplexHandler {
  private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

  @Override
  public void channelRead(
      @NotNull final ChannelHandlerContext channelHandlerContext, @NotNull final Object object)
      throws Exception {
    if (object instanceof FullHttpRequest fullHttpRequest) {
      final GatewayRequestDetails gatewayRequestDetails =
          channelHandlerContext.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();

      // check if uri is excluded from auth requirements or not needed to modify
      final boolean isNoAuth =
          Routes.getAuthExclusions().stream()
              .anyMatch(gatewayRequestDetails.getRequestUriLessApiName()::startsWith);
      final boolean isBasicAuth =
          Routes.getBasicAuthApis().stream()
              .anyMatch(gatewayRequestDetails.getRequestUri()::startsWith);
      final boolean isCheckPermissions = Common.isCheckPermissions(fullHttpRequest);

      if (isNoAuth || isBasicAuth || isCheckPermissions) {
        logger.debug(
            "[{}] Excluded From Authorization Modification for NoAuth=[{}], BasicAuth=[{}], CheckPermissions=[{}]...",
            gatewayRequestDetails.getRequestId(),
            isNoAuth,
            isBasicAuth,
            isCheckPermissions);
        super.channelRead(channelHandlerContext, fullHttpRequest);
        return;
      }

      // put this here so that it can be sent as x-auth-token
      String authHeader = fullHttpRequest.headers().get(HttpHeaderNames.AUTHORIZATION);
      if (CommonUtilities.isEmpty(authHeader)) {
        authHeader = fullHttpRequest.headers().get(HttpHeaderNames.AUTHORIZATION.toLowerCase());
      }

      HttpResponse<AuthToken> authTokenHttpResponse = null;

      if (!gatewayRequestDetails.getApiName().equals(Constants.THIS_APP_NAME)) {
        // check if there is auth header app id (to validate auth token)
        final String authAppId = fullHttpRequest.headers().get(Constants.HEADER_X_AUTH_APPID);
        final int authHeaderAppId = CommonUtilities.parseIntNoEx(authAppId);

        if (authHeaderAppId <= 0) {
          logger.error(
              "[{}] AppId Header Missing/Invalid...", gatewayRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
              channelHandlerContext,
              HttpResponseStatus.UNAUTHORIZED,
              "Missing or Malformed AppId Header");
          return;
        }

        // check if there is an auth token
        if (CommonUtilities.isEmpty(authHeader) || !authHeader.startsWith(Constants.BEARER_AUTH)) {
          logger.error("[{}] Auth Header Missing/Invalid...", gatewayRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
              channelHandlerContext,
              HttpResponseStatus.UNAUTHORIZED,
              "Missing or Malformed Authorization Header");
          return;
        }

        // validate the auth token
        authTokenHttpResponse = Validate.validateToken(authHeader, authHeaderAppId);
        if (authTokenHttpResponse.statusCode() != 200) {
          logger.error("[{}] Auth Token Not Valid...", gatewayRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
              channelHandlerContext,
              HttpResponseStatus.UNAUTHORIZED,
              "Invalid Authorization Header");
          return;
        }
      }

      // update request with basic auth after token validated
      // do not do it for authsvc, because that expects the bearer token
      if (!gatewayRequestDetails.getApiName().equals(Constants.API_NAME_AUTH_SERVICE)) {
        final String appUsername =
            Routes.getAuthApps().get(gatewayRequestDetails.getApiName() + Constants.AUTH_APPS_USR);
        final String appPassword =
            Routes.getAuthApps().get(gatewayRequestDetails.getApiName() + Constants.AUTH_APPS_PWD);

        if (CommonUtilities.isEmpty(appUsername) || CommonUtilities.isEmpty(appPassword)) {
          logger.error("[{}] Auth Credentials Not Found...", gatewayRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
              channelHandlerContext,
              HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED,
              "Missing Auth Credentials");
          return;
        }

        final String xAuthToken = authTokenHttpResponse == null ? "" : Secrets.encodeAndSign(authTokenHttpResponse.responseBody());

        fullHttpRequest
            .headers()
            .set(
                HttpHeaderNames.AUTHORIZATION,
                CommonUtilities.getBasicAuth(appUsername, appPassword))
            .set(Constants.HEADER_X_AUTH_HEADER, authHeader)
            .set(Constants.HEADER_X_AUTH_TOKEN, xAuthToken);

        logger.debug("[{}] Auth Header Updated...", gatewayRequestDetails.getRequestId());
      }
    }

    super.channelRead(channelHandlerContext, object);
  }
}
