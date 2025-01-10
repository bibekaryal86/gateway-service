package gateway.service.proxy;

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
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
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
    final String backendHost = "";
    final int backendPort = -1;

    final CircuitBreaker circuitBreaker =
        circuitBreakers.computeIfAbsent(
            backendHost, key -> new CircuitBreaker(key, 3, Duration.ofSeconds(5)));

    if (circuitBreaker.allowRequest()) {
      String clientId = extractClientId(channelHandlerContext);
      RateLimiter rateLimiter =
          rateLimiters.computeIfAbsent(
                  clientId, key -> new RateLimiter(10, 1000)); // 10 requests per second

      if (rateLimiter.allowRequest()) {
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

        final ChannelFuture channelFuture = bootstrap.connect(backendHost, backendPort);
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
                                    channelHandlerContext.writeAndFlush(
                                        new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.INTERNAL_SERVER_ERROR));
                                  }
                                });
                  } else {
                    if (futureRequest.cause() != null) {
                      log.error("Connection to API timed out...");
                      channelHandlerContext.writeAndFlush(
                          new DefaultFullHttpResponse(
                              HttpVersion.HTTP_1_1, HttpResponseStatus.GATEWAY_TIMEOUT));
                    } else {
                      log.error("Failed to connect to API...", futureRequest.cause());
                      channelHandlerContext.writeAndFlush(
                          new DefaultFullHttpResponse(
                              HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE));
                    }
                  }
                });
        circuitBreaker.markSuccess();
      } else {
        circuitBreaker.markFailure();
        channelHandlerContext.writeAndFlush(
            new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.TOO_MANY_REQUESTS));
      }
    } else {
      channelHandlerContext.writeAndFlush(
          new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE));
    }
  }

    private String extractClientId(final ChannelHandlerContext ctx) {
        String remoteAddress = ctx.channel().remoteAddress().toString();

        if (remoteAddress.contains("/")) {
            remoteAddress = remoteAddress.substring(remoteAddress.indexOf("/") + 1);
        }
        if (remoteAddress.contains(":")) {
            remoteAddress = remoteAddress.substring(0, remoteAddress.indexOf(":"));
        }

        return remoteAddress;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
        log.error("Exception in Gateway Request Handler...", throwable);
        channelHandlerContext.close();
    }
}
