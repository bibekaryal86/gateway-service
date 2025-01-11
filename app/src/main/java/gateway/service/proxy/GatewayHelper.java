package gateway.service.proxy;

import gateway.service.dtos.GatewayRequestDetails;
import gateway.service.logging.LogLogger;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.Routes;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GatewayHelper {

  public static boolean gatewaySvcResponse(
      final GatewayRequestDetails gatewayRequestDetails,
      final ChannelHandlerContext channelHandlerContext,
      final FullHttpRequest fullHttpRequest) {
    boolean isGatewaySvcResponse = false;
    if (gatewayRequestDetails.getApiName().equals(Constants.THIS_APP_NAME)) {
      final String requestUri =
          gatewayRequestDetails.getRequestUri().replace(Constants.THIS_APP_NAME, "").trim();

      if (Objects.equals("", requestUri)
          || Objects.equals("/", gatewayRequestDetails.getRequestUri())
          || requestUri.startsWith(Constants.TESTS_PING)) {
        handleTestsPing(channelHandlerContext);
        isGatewaySvcResponse = true;
      } else if (requestUri.startsWith(Constants.TESTS_RESET)) {
        handleTestsReset(channelHandlerContext);
        isGatewaySvcResponse = true;
      } else if (requestUri.startsWith(Constants.TESTS_LOGS)) {
        handleTestsLogs(channelHandlerContext, fullHttpRequest);
        isGatewaySvcResponse = true;
      }
    }
    return isGatewaySvcResponse;
  }

  /** returns true if an error response HAS NOT been sent */
  public static boolean circuitBreakerResponse(
      final ChannelHandlerContext channelHandlerContext, final CircuitBreaker circuitBreaker) {
    if (circuitBreaker.allowRequest()) {
      return false;
    }
    sendErrorResponse(channelHandlerContext, HttpResponseStatus.SERVICE_UNAVAILABLE);
    return true;
  }

  /** returns true if an error response HAS NOT been sent */
  public static boolean rateLimiterResponse(
      final ChannelHandlerContext channelHandlerContext, final RateLimiter rateLimiter) {
    if (rateLimiter.allowRequest()) {
      return false;
    }
    sendErrorResponse(channelHandlerContext, HttpResponseStatus.TOO_MANY_REQUESTS);
    return true;
  }

  public static void sendErrorResponse(
      final ChannelHandlerContext channelHandlerContext, final HttpResponseStatus status) {
    // TODO add response logging
    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, Constants.CONTENT_TYPE_JSON);
    channelHandlerContext.writeAndFlush(response);
    channelHandlerContext.close();
  }

  public static void sendResponse(
      final String jsonResponse, final ChannelHandlerContext channelHandlerContext) {
    // TODO, add response logging
    FullHttpResponse response =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(jsonResponse.getBytes()));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, Constants.CONTENT_TYPE_JSON);
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonResponse.length());
    channelHandlerContext.writeAndFlush(response);
    channelHandlerContext.close();
  }

  private static void handleTestsPing(final ChannelHandlerContext channelHandlerContext) {
    String jsonResponse = "{\"ping\": \"successful\"}";
    sendResponse(jsonResponse, channelHandlerContext);
  }

  private static void handleTestsReset(final ChannelHandlerContext channelHandlerContext) {
    Routes.refreshRoutes();
    String jsonResponse = "{\"reset\": \"successful\"}";
    sendResponse(jsonResponse, channelHandlerContext);
  }

  private static void handleTestsLogs(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(fullHttpRequest.uri());
    final Map<String, List<String>> parameters = queryStringDecoder.parameters();
    final List<String> logLevels = parameters.get("level");

    if (Common.isEmpty(logLevels)) {
      return;
    }

    final String logLevel = logLevels.getFirst();
    LogLogger.configureGlobalLogging(Common.transformLogLevel(logLevel));

    String jsonResponse = "{\"log\": \"successful\"}";
    sendResponse(jsonResponse, channelHandlerContext);
  }
}
