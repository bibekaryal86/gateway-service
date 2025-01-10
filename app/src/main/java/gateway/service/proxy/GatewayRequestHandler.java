package gateway.service.proxy;

import gateway.service.utils.Routes;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
  private static final Logger log = LoggerFactory.getLogger(GatewayRequestHandler.class);

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
    final String requestUri = fullHttpRequest.retain().uri();

    // Check if the request is for the base path
    if ("/".equals(requestUri) || "".equals(requestUri)) {
      sendDefaultResponse(channelHandlerContext);
      return;
    }

    final String apiName = extractApiName(requestUri);

    final CircuitBreaker circuitBreaker =
        circuitBreakers.computeIfAbsent(
            apiName, key -> new CircuitBreaker(key, 3, Duration.ofSeconds(5)));

    if (!circuitBreaker.allowRequest()) {
      sendErrorResponse(channelHandlerContext, HttpResponseStatus.SERVICE_UNAVAILABLE);
      return;
    }

    String clientId = extractClientId(channelHandlerContext);
    RateLimiter rateLimiter =
        rateLimiters.computeIfAbsent(
            clientId, key -> new RateLimiter(10, 1000)); // 10 requests per second

    if (!rateLimiter.allowRequest()) {
      circuitBreaker.markFailure();
      sendErrorResponse(channelHandlerContext, HttpResponseStatus.TOO_MANY_REQUESTS);
      return;
    }

    final URL transformedUrl = transformRequestUrl(apiName);
    if (transformedUrl == null) {
      circuitBreaker.markFailure();
      sendErrorResponse(channelHandlerContext, HttpResponseStatus.BAD_REQUEST);
      return;
    }

    final Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(this.workerGroup)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 5 seconds connect timeout
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(@NotNull final SocketChannel socketChannel)
                  throws Exception {
                socketChannel
                    .pipeline()
                    .addLast(new HttpClientCodec())
                    .addLast(new HttpObjectAggregator(1048576)) // 1MB, same as in Server
                    .addLast(new HttpResponseDecoder())
                    .addLast(new GatewayResponseHandler(channelHandlerContext, circuitBreaker));
              }
            });

    final ChannelFuture channelFuture =
        bootstrap.connect(extractHost(transformedUrl), extractPort(transformedUrl));
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
                                log.error(
                                    "Failed to write request to response handler...",
                                    futureResponse.cause());
                                sendErrorResponse(
                                    channelHandlerContext, HttpResponseStatus.BAD_GATEWAY);
                              }
                            });
              } else {
                if (futureRequest.cause() != null) {
                  log.error("Connection to API timed out...");
                  sendErrorResponse(channelHandlerContext, HttpResponseStatus.GATEWAY_TIMEOUT);
                } else {
                  log.error("Failed to connect to API...", futureRequest.cause());
                  sendErrorResponse(channelHandlerContext, HttpResponseStatus.SERVICE_UNAVAILABLE);
                }
              }
            });
    circuitBreaker.markSuccess();
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
    log.error("Exception in Gateway Request Handler...", throwable);

    String backendHost = "";
    final CircuitBreaker circuitBreaker =
        circuitBreakers.computeIfAbsent(
            backendHost, key -> new CircuitBreaker(key, 3, Duration.ofSeconds(5)));
    circuitBreaker.markFailure();

    sendErrorResponse(channelHandlerContext, HttpResponseStatus.INTERNAL_SERVER_ERROR);
  }

  private String extractApiName(final String requestUri) {
    if (requestUri.startsWith("/")) {
      return requestUri.substring(1);
    }
    return requestUri;
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

  private URL transformRequestUrl(final String apiName) {
    final String baseUrl = Routes.getTargetBaseUrl(apiName);
    if (baseUrl == null) {
      return null;
    }
    try {
      return new URI(baseUrl).toURL();
    } catch (MalformedURLException | URISyntaxException e) {
      return null;
    }
  }

  private String extractHost(final URL url) {
    return url.getHost();
  }

  private int extractPort(final URL url) {
    int port = url.getPort();
    if (port == -1) {
      if (url.getProtocol().equals("https")) {
        port = 443;
      } else {
        port = 80;
      }
    }
    return port;
  }

  private void sendErrorResponse(
      final ChannelHandlerContext channelHandlerContext, final HttpResponseStatus status) {
    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
    channelHandlerContext.writeAndFlush(response);
    channelHandlerContext.close();
  }

  private void sendDefaultResponse(final ChannelHandlerContext channelHandlerContext) {
    String jsonResponse = "{\"ping\": \"successful\"}";
    FullHttpResponse response =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(jsonResponse.getBytes()));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonResponse.length());
    channelHandlerContext.writeAndFlush(response);
    channelHandlerContext.close();
  }
}
