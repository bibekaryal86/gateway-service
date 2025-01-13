package gateway.service.proxy;

import gateway.service.dtos.GatewayRequestDetails;
import gateway.service.logging.LogLogger;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.GatewayHelper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

public class GatewayRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
  private static final LogLogger logger = LogLogger.getLogger(GatewayRequestHandler.class);

  private final EventLoopGroup workerGroup;
  private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
  private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

  public GatewayRequestHandler(final EventLoopGroup workerGroup) {
    this.workerGroup = workerGroup;
  }

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest)
      throws Exception {
    final GatewayRequestDetails gatewayRequestDetails =
        channelHandlerContext.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();

    if (gatewayRequestDetails == null) {
      GatewayHelper.sendErrorResponse(
          channelHandlerContext,
          HttpResponseStatus.BAD_REQUEST,
          "Gateway Request Details Error...");
      return;
    }

    final boolean isGatewaySvcResponse =
        GatewayHelper.gatewaySvcResponse(
            gatewayRequestDetails, channelHandlerContext, fullHttpRequest);
    if (isGatewaySvcResponse) {
      return;
    }

    final CircuitBreaker circuitBreaker =
        circuitBreakers.computeIfAbsent(
            gatewayRequestDetails.getApiName(),
            key -> new CircuitBreaker(Constants.CB_FAILURE_THRESHOLD, Constants.CB_OPEN_TIMEOUT));
    if (!circuitBreaker.allowRequest()) {
      logger.error("[{}] CircuitBreaker Response: [{}]", gatewayRequestDetails.getRequestId(), circuitBreaker);
      GatewayHelper.sendErrorResponse(
          channelHandlerContext,
          HttpResponseStatus.SERVICE_UNAVAILABLE,
          "Maximum Failures Allowed Exceeded...");
      return;
    }

    RateLimiter rateLimiter =
        rateLimiters.computeIfAbsent(
            gatewayRequestDetails.getClientId(),
            key -> new RateLimiter(Constants.RL_MAX_REQUESTS, Constants.RL_TIME_WINDOW_MILLIS));
    if (!rateLimiter.allowRequest()) {
      logger.error("[{}] RateLimiter Response: [{}]", gatewayRequestDetails.getRequestId(), rateLimiter);
      GatewayHelper.sendErrorResponse(
          channelHandlerContext,
          HttpResponseStatus.TOO_MANY_REQUESTS,
          "Maximum Request Allowed Exceeded...");
      return;
    }

    final Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(this.workerGroup)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Constants.CONNECT_TIMEOUT_MILLIS)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(@NotNull final SocketChannel socketChannel)
                  throws Exception {
                socketChannel
                    .pipeline()
                    .addLast(new HttpClientCodec())
                    .addLast(new HttpObjectAggregator(Constants.MAX_CONTENT_LENGTH))
                    .addLast(new HttpResponseDecoder())
                    .addLast(new GatewayResponseHandler(channelHandlerContext, circuitBreaker));
              }
            });

    final ChannelFuture channelFuture =
        bootstrap.connect(
            gatewayRequestDetails.getTargetHost(), gatewayRequestDetails.getTargetPort());
    channelFuture.addListener(
        (ChannelFutureListener)
            futureRequest -> {
              if (futureRequest.isSuccess()) {
                futureRequest
                    .channel()
                    .writeAndFlush(fullHttpRequest.retain())
                    .addListener(
                        (ChannelFutureListener)
                            futureResponse -> {
                              if (!futureResponse.isSuccess()) {
                                logger.error(
                                    "[{}] Gateway Response Handler Error:", futureResponse.cause(), gatewayRequestDetails.getRequestId());
                                GatewayHelper.sendErrorResponse(
                                    channelHandlerContext,
                                    HttpResponseStatus.BAD_GATEWAY,
                                    "Gateway Response Handler Error...");
                              }
                            });
              } else {
                if (futureRequest.cause() != null) {
                  GatewayHelper.sendErrorResponse(
                      channelHandlerContext,
                      HttpResponseStatus.GATEWAY_TIMEOUT,
                      "Connection Timeout...");
                } else {
                  GatewayHelper.sendErrorResponse(
                      channelHandlerContext,
                      HttpResponseStatus.SERVICE_UNAVAILABLE,
                      "Something Went Wrong...");
                }
              }
            });
    circuitBreaker.markSuccess();
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
    final GatewayRequestDetails gatewayRequestDetails =
            channelHandlerContext.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();
    logger.error("[{}] Gateway Request Handler Exception Caught...", throwable, Common.getRequestId(gatewayRequestDetails));

    GatewayHelper.sendErrorResponse(
        channelHandlerContext,
        HttpResponseStatus.INTERNAL_SERVER_ERROR,
        "Gateway Request Handler Exception...");
  }
}
