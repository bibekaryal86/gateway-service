package gateway.service.proxy;

import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.Map;

public class GatewayHelper {

  public boolean gatewaySvcResponse(
      final String apiName,
      final ChannelHandlerContext channelHandlerContext,
      final FullHttpRequest fullHttpRequest) {
    if (Common.isEmpty(apiName)) {
      return new GatewayService()
          .handleGatewayServiceRequests(channelHandlerContext, fullHttpRequest);
    } else if (Constants.THIS_APP_NAME.equals(apiName)) {
      return new GatewayService()
          .handleGatewayServiceRequests(channelHandlerContext, fullHttpRequest);
    }
    return false;
  }

  /** returns true if an error response HAS NOT been sent */
  public boolean circuitBreakerResponse(
      final String apiName,
      final ChannelHandlerContext channelHandlerContext,
      final CircuitBreaker circuitBreaker) {
    if (circuitBreaker.allowRequest()) {
      return false;
    }

    sendErrorResponse(channelHandlerContext, HttpResponseStatus.SERVICE_UNAVAILABLE);
    return true;
  }

  /** returns true if an error response HAS NOT been sent */
  public boolean rateLimiterResponse(
      final String apiName,
      final ChannelHandlerContext channelHandlerContext,
      final Map<String, RateLimiter> rateLimiters) {
    String clientId = extractClientId(channelHandlerContext);
    RateLimiter rateLimiter =
        rateLimiters.computeIfAbsent(
            clientId,
            key -> new RateLimiter(Constants.RL_MAX_REQUESTS, Constants.RL_TIME_WINDOW_MILLIS));

    if (rateLimiter.allowRequest()) {
      return false;
    }

    sendErrorResponse(channelHandlerContext, HttpResponseStatus.TOO_MANY_REQUESTS);
    return true;
  }

  public void sendErrorResponse(
      final ChannelHandlerContext channelHandlerContext, final HttpResponseStatus status) {
    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, Constants.CONTENT_TYPE_JSON);
    channelHandlerContext.writeAndFlush(response);
    channelHandlerContext.close();
  }

  private String extractClientId(final ChannelHandlerContext channelHandlerContext) {
    String remoteAddress = channelHandlerContext.channel().remoteAddress().toString();

    if (remoteAddress.contains("/")) {
      remoteAddress = remoteAddress.substring(remoteAddress.indexOf("/") + 1);
    }
    if (remoteAddress.contains(":")) {
      remoteAddress = remoteAddress.substring(0, remoteAddress.indexOf(":"));
    }

    return remoteAddress;
  }
}
