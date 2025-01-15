package gateway.service.proxy;

import gateway.service.dtos.GatewayRequestDetails;
import gateway.service.logging.LogLogger;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.Gateway;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.handler.codec.http.HttpVersion;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class ProxyHandler extends ChannelInboundHandlerAdapter {
  private static final LogLogger logger = LogLogger.getLogger(ProxyHandler.class);
  private final ProxyClient proxy;

  private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
  private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

  public ProxyHandler() {
    this.proxy = new ProxyClient();
  }

  @Override
  public void channelRead(@NotNull final ChannelHandlerContext ctx, @NotNull final Object msg) throws Exception {
    if (msg instanceof FullHttpRequest fullHttpRequest) {
      final GatewayRequestDetails gatewayRequestDetails = ctx.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();

      if (gatewayRequestDetails == null) {
        Gateway.sendErrorResponse(ctx, HttpResponseStatus.BAD_REQUEST, "Gateway Request Details Error...");
        return;
      }

      final boolean isGatewaySvcResponse = Gateway.gatewaySvcResponse(gatewayRequestDetails, ctx, fullHttpRequest);
      if (isGatewaySvcResponse) {
        return;
      }

      final CircuitBreaker circuitBreaker =
              circuitBreakers.computeIfAbsent(
                      gatewayRequestDetails.getApiName(),
                      key -> new CircuitBreaker(Constants.CB_FAILURE_THRESHOLD, Constants.CB_OPEN_TIMEOUT));
      if (!circuitBreaker.allowRequest()) {
        logger.error(
                "[{}] CircuitBreaker Response: [{}]",
                gatewayRequestDetails.getRequestId(),
                circuitBreaker);
        Gateway.sendErrorResponse(
                ctx,
                HttpResponseStatus.SERVICE_UNAVAILABLE,
                "Maximum Failures Allowed Exceeded...");
        return;
      }

      RateLimiter rateLimiter =
              rateLimiters.computeIfAbsent(
                      gatewayRequestDetails.getClientId(),
                      key -> new RateLimiter(Constants.RL_MAX_REQUESTS, Constants.RL_TIME_WINDOW_MILLIS));
      if (!rateLimiter.allowRequest()) {
        logger.error(
                "[{}] RateLimiter Response: [{}]", gatewayRequestDetails.getRequestId(), rateLimiter);
        Gateway.sendErrorResponse(
                ctx,
                HttpResponseStatus.TOO_MANY_REQUESTS,
                "Maximum Request Allowed Exceeded...");
        return;
      }

      String url = gatewayRequestDetails.getTargetBaseUrl() + gatewayRequestDetails.getRequestUri();
      RequestBody body =
              fullHttpRequest.content() == null || fullHttpRequest.content().array().length == 0
                      ? null
                      : RequestBody.create(fullHttpRequest.content().array(), MediaType.parse("application/json"));
      Request.Builder requestBuilder = new Request.Builder().url(url).method(fullHttpRequest.method().name(), body);

      Response response = proxy.proxy(requestBuilder.build());
      FullHttpResponse nettyResponse = new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1,
              HttpResponseStatus.valueOf(response.code()),
              Unpooled.copiedBuffer(response.body().bytes())
      );

      ctx.writeAndFlush(nettyResponse).addListener(ChannelFutureListener.CLOSE);
    } else {
      super.channelRead(ctx, msg);
    }
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
    final GatewayRequestDetails gatewayRequestDetails =
        channelHandlerContext.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();
    logger.error(
        "[{}] Gateway Request Handler Exception Caught...{}",
            Common.getRequestId(gatewayRequestDetails),
        throwable
        );

    Gateway.sendErrorResponse(
        channelHandlerContext,
        HttpResponseStatus.INTERNAL_SERVER_ERROR,
        "Gateway Request Handler Exception...");
  }
}
