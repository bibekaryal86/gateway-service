package gateway.service.proxy;

import gateway.service.dtos.GatewayRequestDetails;
import gateway.service.logging.LogLogger;
import gateway.service.utils.Constants;
import gateway.service.utils.GatewayHelper;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;

public class GatewaySecurityHandler extends ChannelDuplexHandler {
  private static final LogLogger logger = LogLogger.getLogger(GatewaySecurityHandler.class);

  @Override
  public void channelRead(
      @NotNull final ChannelHandlerContext channelHandlerContext, @NotNull final Object object)
      throws Exception {
    if (object instanceof FullHttpRequest fullHttpRequest) {
      GatewayRequestDetails gatewayRequestDetails =
          channelHandlerContext.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();
      logger.info("In Channel Read of Security Handler: [{}]", gatewayRequestDetails);

      GatewayHelper.sendErrorResponse(
          channelHandlerContext, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
    // TODO
    // do not place it here, put it inside the if block above
    // if there is a security issue, use GatewayHelper.sendErrorResponse
    // else use the below line
    super.channelRead(channelHandlerContext, object);
  }
}
