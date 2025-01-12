package gateway.service.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GatewayHelper {
  public static final LogLogger logger = LogLogger.getLogger(GatewayHelper.class);

  public static boolean gatewaySvcResponse(
      final GatewayRequestDetails gatewayRequestDetails,
      final ChannelHandlerContext channelHandlerContext,
      final FullHttpRequest fullHttpRequest) {
    boolean isGatewaySvcResponse = false;
    if (gatewayRequestDetails.getApiName().equals(Constants.THIS_APP_NAME)) {
      final String requestUri = gatewayRequestDetails.getRequestUri();

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
    FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
    fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, Constants.CONTENT_TYPE_JSON);
    channelHandlerContext.writeAndFlush(fullHttpResponse);
    channelHandlerContext.close();
  }

  public static void sendResponse(
      final String jsonResponse, final ChannelHandlerContext channelHandlerContext) {
    FullHttpResponse fullHttpResponse =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(jsonResponse.getBytes()));
    fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, Constants.CONTENT_TYPE_JSON);
    fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonResponse.length());
    channelHandlerContext.writeAndFlush(fullHttpResponse);
    channelHandlerContext.close();
  }

  private static void handleTestsPing(final ChannelHandlerContext channelHandlerContext) {
    sendResponse(Constants.TESTS_PING_RESPONSE, channelHandlerContext);
  }

  private static void handleTestsReset(final ChannelHandlerContext channelHandlerContext) {
    Routes.refreshRoutes();
    sendResponse(Constants.TESTS_RESET_RESPONSE, channelHandlerContext);
  }

  private static void handleTestsLogs(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    final QueryStringDecoder queryStringDecoder =
        new QueryStringDecoder(fullHttpRequest.retain().uri());
    final Map<String, List<String>> parameters = queryStringDecoder.parameters();
    final List<String> logLevels = parameters.get(Constants.TEST_LOGS_PARAM_LEVEL);

    if (Common.isEmpty(logLevels)) {
      return;
    }

    final String currentLogLevel = Common.transformLogLevel(LogLogger.getCurrentLogLevel());
    final String proposedLogLevel = logLevels.getFirst();
    LogLogger.configureGlobalLogging(Common.transformLogLevel(proposedLogLevel));

    sendResponse(
        generateTestsLogsResponse(currentLogLevel, proposedLogLevel),
        channelHandlerContext);
  }

  private static String generateTestsLogsResponse(
      final String oldLogLevel, final String newLogLevel) {
    Map<String, Object> testsLogsResponse = new HashMap<>();
    testsLogsResponse.put("oldLogLevel", oldLogLevel);
    testsLogsResponse.put("newLogLevel", newLogLevel);
    try {
      return Common.objectMapperProvider().writeValueAsString(testsLogsResponse);
    } catch (JsonProcessingException e) {
      return Constants.TESTS_LOGS_RESPONSE;
    }
  }
}
