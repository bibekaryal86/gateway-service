package gateway.service.proxy;

import gateway.service.logging.LogLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;

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
    channelHandlerContextClient.writeAndFlush(fullHttpResponse.retain());
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
    circuitBreaker.markFailure();
    channelHandlerContext.close();
    channelHandlerContextClient.close();
  }
}
