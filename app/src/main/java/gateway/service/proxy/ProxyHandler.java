package gateway.service.proxy;

import gateway.service.dtos.GatewayRequestDetails;
import gateway.service.utils.AppConfigs;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.Gateway;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyHandler extends ChannelInboundHandlerAdapter {
  private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);
  private final ProxyClient proxy;

  private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
  private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

  public ProxyHandler() {
    this.proxy = new ProxyClient();
  }

  @Override
  public void channelRead(@NotNull final ChannelHandlerContext channelHandlerContext, @NotNull final Object object)
      throws Exception {
    if (object instanceof FullHttpRequest fullHttpRequest) {
      final GatewayRequestDetails gatewayRequestDetails =
          channelHandlerContext.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();

      if (gatewayRequestDetails == null) {
        Gateway.sendErrorResponse(
            channelHandlerContext, HttpResponseStatus.BAD_REQUEST, "Gateway Request Details Error...");
        return;
      }

      final boolean isGatewaySvcResponse =
          Gateway.gatewaySvcResponse(gatewayRequestDetails, channelHandlerContext, fullHttpRequest);
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
            channelHandlerContext, HttpResponseStatus.SERVICE_UNAVAILABLE, "Maximum Failures Allowed Exceeded...");
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
            channelHandlerContext, HttpResponseStatus.TOO_MANY_REQUESTS, "Maximum Request Allowed Exceeded...");
        return;
      }

      try (Response response =
          proxy.proxy(getProxyRequest(gatewayRequestDetails, fullHttpRequest))) {

        if (response.code() > 199 && response.code() < 300) {
          circuitBreaker.markSuccess();
        } else {
          circuitBreaker.markFailure();
        }

        FullHttpResponse fullHttpResponse;
        if (response.body() == null) {
          fullHttpResponse =
              new DefaultFullHttpResponse(
                  HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(response.code()));
        } else {
          fullHttpResponse =
              new DefaultFullHttpResponse(
                  HttpVersion.HTTP_1_1,
                  HttpResponseStatus.valueOf(response.code()),
                  Unpooled.copiedBuffer(response.body().bytes()));
        }
        fullHttpResponse
            .headers()
            .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        channelHandlerContext.writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
      } catch (Exception ex) {
        circuitBreaker.markFailure();
        logger.error("[{}] Proxy Handler Error...", gatewayRequestDetails.getRequestId(), ex);
        Gateway.sendErrorResponse(channelHandlerContext, HttpResponseStatus.BAD_GATEWAY, "Proxy Handler Error...");
      }
    } else {
      super.channelRead(channelHandlerContext, object);
    }
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
    final GatewayRequestDetails gatewayRequestDetails =
        channelHandlerContext.channel().attr(Constants.GATEWAY_REQUEST_DETAILS_KEY).get();
    logger.error(
        "[{}] Proxy Handler Exception Caught...",
        Common.getRequestId(gatewayRequestDetails),
        throwable);

    Gateway.sendErrorResponse(
        channelHandlerContext,
        HttpResponseStatus.INTERNAL_SERVER_ERROR,
        "Proxy Handler Exception...");
  }

  private Request getProxyRequest(
      final GatewayRequestDetails gatewayRequestDetails, final FullHttpRequest fullHttpRequest) {
    final String url =
        gatewayRequestDetails.getTargetBaseUrl() + gatewayRequestDetails.getRequestUri();
    final RequestBody body =
        fullHttpRequest.content() == null || fullHttpRequest.content().readableBytes() == 0
            ? null
            : RequestBody.create(
                ByteBufUtil.getBytes(fullHttpRequest.content()),
                MediaType.parse(HttpHeaderValues.APPLICATION_JSON.toString()));

    final Headers.Builder headersBuilder = new Headers.Builder();
    final List<String> proxyHeaders = AppConfigs.getRoutes().getProxyHeaders();
    fullHttpRequest.headers().entries().stream()
        .filter(
            stringStringEntry -> proxyHeaders.contains(stringStringEntry.getKey().toLowerCase()))
        .forEach(httpHeaders -> headersBuilder.add(httpHeaders.getKey(), httpHeaders.getValue()));

    Request.Builder requestBuilder =
        new Request.Builder()
            .url(url)
            .method(fullHttpRequest.method().name(), body)
            .headers(headersBuilder.build());
    requestBuilder.addHeader(
        Constants.GATEWAY_REQUEST_DETAILS_KEY.name(), gatewayRequestDetails.getRequestId());

    return requestBuilder.build();
  }
}
