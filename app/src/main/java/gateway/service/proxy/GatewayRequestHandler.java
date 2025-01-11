package gateway.service.proxy;

import gateway.service.dtos.GatewayRequestDetails;
import gateway.service.logging.LogLogger;
import gateway.service.utils.Constants;
import gateway.service.utils.Routes;
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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AttributeKey;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
    final GatewayRequestDetails gatewayRequestDetails = extractGatewayRequestDetails(channelHandlerContext, fullHttpRequest);

    if (gatewayRequestDetails == null) {
      GatewayHelper.sendErrorResponse(channelHandlerContext, HttpResponseStatus.BAD_REQUEST);
      return;
    }

    // Store request details in Channel attributes
    channelHandlerContext
        .channel()
        .attr(AttributeKey.valueOf("REQUEST_DETAILS"))
        .set(gatewayRequestDetails);
    logger.info("Gateway Request: [{}]", gatewayRequestDetails);

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
      logger.error("CircuitBreaker Response: [{}], [{}]", gatewayRequestDetails, circuitBreaker);
      GatewayHelper.sendErrorResponse(channelHandlerContext, HttpResponseStatus.SERVICE_UNAVAILABLE);
      return;
    }

    RateLimiter rateLimiter =
        rateLimiters.computeIfAbsent(
                gatewayRequestDetails.getClientId(),
            key -> new RateLimiter(Constants.RL_MAX_REQUESTS, Constants.RL_TIME_WINDOW_MILLIS));
    if (!rateLimiter.allowRequest()) {
      logger.error("RateLimiter Response: [{}], [{}]", gatewayRequestDetails, rateLimiter);
      GatewayHelper.sendErrorResponse(channelHandlerContext, HttpResponseStatus.TOO_MANY_REQUESTS);
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
                    .addLast(
                        new GatewayResponseHandler(
                            channelHandlerContext, gatewayRequestDetails, circuitBreaker));
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
                                    "Gateway Response Handler Error: [{}]",
                                    futureResponse.cause(),
                                    gatewayRequestDetails);
                                GatewayHelper.sendErrorResponse(
                                    channelHandlerContext, HttpResponseStatus.BAD_GATEWAY);
                              }
                            });
              } else {
                logger.error("Gateway Response Handler Failure: [{}]", gatewayRequestDetails);

                if (futureRequest.cause() != null) {
                  GatewayHelper.sendErrorResponse(
                      channelHandlerContext, HttpResponseStatus.GATEWAY_TIMEOUT);
                } else {
                  GatewayHelper.sendErrorResponse(
                      channelHandlerContext, HttpResponseStatus.SERVICE_UNAVAILABLE);
                }
              }
            });
    circuitBreaker.markSuccess();
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
    final GatewayRequestDetails gatewayRequestDetails =
        (GatewayRequestDetails)
            channelHandlerContext.channel().attr(AttributeKey.valueOf("REQUEST_DETAILS")).get();
    logger.error(
        "Gateway Request Handler Exception Caught: [{}]", throwable, gatewayRequestDetails);

    String backendHost = "";
    final CircuitBreaker circuitBreaker =
        circuitBreakers.computeIfAbsent(
            backendHost,
            key -> new CircuitBreaker(Constants.CB_FAILURE_THRESHOLD, Constants.CB_OPEN_TIMEOUT));
    circuitBreaker.markFailure();

    GatewayHelper.sendErrorResponse(
        channelHandlerContext, HttpResponseStatus.INTERNAL_SERVER_ERROR);
  }

  private GatewayRequestDetails extractGatewayRequestDetails(final ChannelHandlerContext channelHandlerContext,
                                                             final FullHttpRequest fullHttpRequest) {
    final String requestUri = fullHttpRequest.retain().uri();
    final HttpMethod requestMethod = fullHttpRequest.retain().method();
    final String apiName = extractApiName(requestUri);
    final String clientId = extractClientId(channelHandlerContext);
    final String targetBaseUrl = Routes.getTargetBaseUrl(apiName);

      try {
          final URL baseUrl = new URI(targetBaseUrl).toURL();
        return new GatewayRequestDetails(requestMethod, apiName, requestUri, clientId, targetBaseUrl, extractHost(baseUrl), extractPort(baseUrl));
      } catch (MalformedURLException | URISyntaxException ignored) {
        logger.error("Extract Gateway Request Details Error: [{}],[{}],[{}],[{}],[{}]", requestUri, requestMethod, apiName, clientId, targetBaseUrl);
        return null;
      }
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

  private String extractApiName(String requestUri) {
    if ("/".equals(requestUri) || "".equals(requestUri)) {
      return Constants.THIS_APP_NAME;
    }
    if (requestUri.startsWith("/")) {
      return requestUri.substring(1);
    }
    if (requestUri.contains("?")) {
      requestUri = requestUri.split("\\?")[0];
    }
    if (requestUri.contains("/")) {
      return requestUri.split("/")[0];
    }
    return requestUri;
  }

  private String extractHost(final URL url) {
    return url.getHost();
  }

  private int extractPort(final URL url) {
    int port = url.getPort();
    if (port == -1) {
      if (url.getProtocol().equals(Constants.HTTPS_PROTOCOL)) {
        port = Constants.HTTPS_DEFAULT_PORT;
      } else {
        port = Constants.HTTP_DEFAULT_PORT;
      }
    }
    return port;
  }
}
