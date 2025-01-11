package gateway.service.proxy;

import gateway.service.logging.LogLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.AttributeKey;

public class GatewayResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
  private static final LogLogger logger = LogLogger.getLogger(GatewayResponseHandler.class);
  private final ChannelHandlerContext channelHandlerContextClient;
  private final CircuitBreaker circuitBreaker;

  public GatewayResponseHandler(
      final ChannelHandlerContext channelHandlerContextClient,
      final CircuitBreaker circuitBreaker) {
    this.channelHandlerContextClient = channelHandlerContextClient;
    this.circuitBreaker = circuitBreaker;
  }

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext /* unused */,
      final FullHttpResponse fullHttpResponse)
      throws Exception {
    final String requestId =
        (String) channelHandlerContext.channel().attr(AttributeKey.valueOf("REQUEST_ID")).get();
    logger.info("Gateway Response: ID=[{}], Status=[{}]", requestId, fullHttpResponse.status());

    channelHandlerContextClient.writeAndFlush(fullHttpResponse.retain());
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
    final String requestId =
        (String) channelHandlerContext.channel().attr(AttributeKey.valueOf("REQUEST_ID")).get();
    logger.info("Gateway Response: ID=[{}], Status=[{}]", requestId, throwable);
    circuitBreaker.markFailure();
    channelHandlerContext.close();
    channelHandlerContextClient.close();
  }
}
