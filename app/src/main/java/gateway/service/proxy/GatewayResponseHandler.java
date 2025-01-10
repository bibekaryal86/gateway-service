package gateway.service.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
  private static final Logger log = LoggerFactory.getLogger(GatewayResponseHandler.class);
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
    log.debug("Received response from backend: {}", fullHttpResponse.status());
    channelHandlerContextClient.writeAndFlush(fullHttpResponse.retain());
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
    log.error("Exception in Gateway Response Handler...", throwable);
    circuitBreaker.markFailure();
    channelHandlerContext.close();
    channelHandlerContextClient.close();
  }
}
