package gateway.service.proxy;

import gateway.service.logging.LogLogger;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.Routes;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class GatewayService {

  private final String TESTS_PING = "/tests/ping";
  private final String TESTS_RESET = "/tests/reset";
  private final String TESTS_LOGS = "/tests/logs";

  public boolean handleGatewayServiceRequests(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    String requestUri = fullHttpRequest.retain().uri();
    requestUri = requestUri.replace("/" + Constants.THIS_APP_NAME + "/", "");

    if (Common.isEmpty(requestUri) || requestUri.startsWith(TESTS_PING)) {
      return handleTestsPing(channelHandlerContext, fullHttpRequest);
    } else if (requestUri.startsWith(TESTS_RESET)) {
      return handleTestsReset(channelHandlerContext, fullHttpRequest);
    } else if (requestUri.startsWith(TESTS_LOGS)) {
      return handleTestsLogs(channelHandlerContext, fullHttpRequest);
    }

    return false;
  }

  private boolean handleTestsPing(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    String jsonResponse = "{\"ping\": \"successful\"}";
    FullHttpResponse response =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(jsonResponse.getBytes()));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, Constants.CONTENT_TYPE_JSON);
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonResponse.length());
    channelHandlerContext.writeAndFlush(response);
    channelHandlerContext.close();
    return true;
  }

  public boolean handleTestsReset(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    Routes.refreshRoutes();

    String jsonResponse = "{\"reset\": \"successful\"}";
    FullHttpResponse response =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(jsonResponse.getBytes()));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, Constants.CONTENT_TYPE_JSON);
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonResponse.length());
    channelHandlerContext.writeAndFlush(response);
    channelHandlerContext.close();
    return true;
  }

  public boolean handleTestsLogs(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(fullHttpRequest.uri());
    final Map<String, List<String>> parameters = queryStringDecoder.parameters();
    final List<String> logLevels = parameters.get("level");

    if (Common.isEmpty(logLevels)) {
      return false;
    }

    final String logLevel = logLevels.getFirst();
    LogLogger.configureGlobalLogging(checkLogLevel(logLevel));
    return true;
  }

  private Level checkLogLevel(final String logLevel) {
    switch (logLevel) {
      case "DEBUG" -> {
        return Level.FINE;
      }
      case "WARN" -> {
        return Level.WARNING;
      }
      case "ERROR" -> {
        return Level.SEVERE;
      }
    }
    return Level.INFO;
  }
}
