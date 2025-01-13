package gateway.service.proxy;

import gateway.service.dtos.GatewayRequestDetails;
import gateway.service.logging.LogLogger;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.GatewayHelper;
import gateway.service.utils.Routes;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.jetbrains.annotations.NotNull;

public class GatewayLoggingHandler extends ChannelDuplexHandler {
  private static final LogLogger logger = LogLogger.getLogger(GatewayLoggingHandler.class);

  @Override
  public void channelRead(
      @NotNull final ChannelHandlerContext channelHandlerContext, @NotNull final Object object)
      throws Exception {
    if (object instanceof FullHttpRequest fullHttpRequest) {
      final GatewayRequestDetails gatewayRequestDetails =
          extractGatewayRequestDetails(channelHandlerContext, fullHttpRequest);

      if (gatewayRequestDetails == null) {
        GatewayHelper.sendErrorResponse(channelHandlerContext, HttpResponseStatus.BAD_REQUEST, "Gateway Request Details NULL Error...");
        return;
      }

      final String requestContentLength = fullHttpRequest.retain().headers().get(HttpHeaderNames.CONTENT_LENGTH, "0");
      logger.info("[{}] Request: [{}], [{}]", gatewayRequestDetails.getRequestId(), gatewayRequestDetails , requestContentLength);

      // set gateway request details in channel handler context for later use
      channelHandlerContext
              .channel()
              .attr(Constants.GATEWAY_REQUEST_DETAILS_KEY)
              .set(gatewayRequestDetails);
    }
    super.channelRead(channelHandlerContext, object);
  }

  @Override
  public void write(
      final ChannelHandlerContext channelHandlerContext,
      final Object object,
      final ChannelPromise channelPromise)
      throws Exception {
    if (object instanceof FullHttpResponse fullHttpResponse) {
      final String responseContentLength =
              fullHttpResponse.retain().headers().get(HttpHeaderNames.CONTENT_LENGTH, "0");
      final HttpResponseStatus responseStatus = fullHttpResponse.retain().status();
      GatewayRequestDetails gatewayRequestDetails = channelHandlerContext.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();
      logger.info(
          "[{}] Response: [{}], [{}]",
          Common.getRequestId(gatewayRequestDetails),
          responseStatus,
          responseContentLength);
    }
    super.write(channelHandlerContext, object, channelPromise);
  }

  private GatewayRequestDetails extractGatewayRequestDetails(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    final String requestUri = fullHttpRequest.retain().uri();
    final HttpMethod requestMethod = fullHttpRequest.retain().method();
    final String apiName = extractApiName(requestUri);
    final String clientId = extractClientId(channelHandlerContext);
    final String targetBaseUrl = Routes.getTargetBaseUrl(apiName);

    try {
      final URL baseUrl = new URI(targetBaseUrl).toURL();
      return new GatewayRequestDetails(
          requestMethod,
          requestUri,
          apiName,
          clientId,
          targetBaseUrl,
          extractHost(baseUrl),
          extractPort(baseUrl));
    } catch (MalformedURLException | URISyntaxException ignored) {
      logger.error(
          "Extract Gateway Request Details Error: [{}],[{}],[{}],[{}],[{}]",
          requestUri,
          requestMethod,
          apiName,
          clientId,
          targetBaseUrl);
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
      requestUri = requestUri.substring(1);
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
