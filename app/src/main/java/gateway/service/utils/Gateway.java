package gateway.service.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import gateway.service.dtos.GatewayRequestDetails;
import io.github.bibekaryal86.shdsvc.dtos.ResponseMetadata;
import io.github.bibekaryal86.shdsvc.dtos.ResponseWithMetadata;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Gateway {
  public static final Logger logger = LoggerFactory.getLogger(Gateway.class);

  public static boolean gatewaySvcResponse(
      final GatewayRequestDetails gatewayRequestDetails,
      final ChannelHandlerContext channelHandlerContext,
      final FullHttpRequest fullHttpRequest) {
    boolean isGatewaySvcResponse = false;
    if (gatewayRequestDetails.getApiName().equals(Constants.THIS_APP_NAME)) {
      final String requestUri = gatewayRequestDetails.getRequestUri();

      if (Objects.equals("", requestUri)
          || Objects.equals("/", gatewayRequestDetails.getRequestUri())
          || requestUri.startsWith(Constants.TESTS_PING)) {
        handleTestsPing(channelHandlerContext);
        isGatewaySvcResponse = true;
      } else if (requestUri.startsWith(Constants.TESTS_RESET)) {
        handleTestsReset(channelHandlerContext);
        isGatewaySvcResponse = true;
      } else if (requestUri.startsWith(Constants.TESTS_LOGS)) {
        handleTestsLogs(channelHandlerContext, fullHttpRequest);
        isGatewaySvcResponse = true;
      }
    }

    return isGatewaySvcResponse;
  }

  public static void sendErrorResponse(
      final ChannelHandlerContext channelHandlerContext,
      final HttpResponseStatus status,
      final String errMsg) {
    final FullHttpResponse fullHttpResponse;
    if (CommonUtilities.isEmpty(errMsg)) {
      fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
    } else {
      final ResponseWithMetadata responseWithMetadata =
          new ResponseWithMetadata(
              new ResponseMetadata(
                  new ResponseMetadata.ResponseStatusInfo(errMsg),
                  ResponseMetadata.emptyResponseCrudInfo(),
                  ResponseMetadata.emptyResponsePageInfo()));
      final String jsonResponse = CommonUtilities.writeValueAsStringNoEx(responseWithMetadata);
      fullHttpResponse =
          new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(jsonResponse.getBytes()));
      fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonResponse.length());
    }

    fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
    channelHandlerContext.writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
  }

  public static void sendResponse(
      final String jsonResponse, final ChannelHandlerContext channelHandlerContext) {
    final FullHttpResponse fullHttpResponse =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(jsonResponse.getBytes()));
    fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
    fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonResponse.length());
    channelHandlerContext.writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
  }

  private static void handleTestsPing(final ChannelHandlerContext channelHandlerContext) {
    sendResponse(Constants.TESTS_PING_RESPONSE, channelHandlerContext);
  }

  private static void handleTestsReset(final ChannelHandlerContext channelHandlerContext) {
    AppConfigs.refreshAppConfigs();
    sendResponse(Constants.TESTS_RESET_RESPONSE, channelHandlerContext);
  }

  private static void handleTestsLogs(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(fullHttpRequest.uri());
    final Map<String, List<String>> parameters = queryStringDecoder.parameters();
    final List<String> logLevels = parameters.get(Constants.TEST_LOGS_PARAM_LEVEL);

    if (CommonUtilities.isEmpty(logLevels)) {
      return;
    }

    final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    final ch.qos.logback.classic.Logger loggerContextLogger = loggerContext.getLogger("root");
    final ch.qos.logback.classic.Logger loggerContextLoggerShdsvc =
        loggerContext.getLogger("io.github.bibekaryal86");

    System.out.println(
        loggerContextLoggerShdsvc.getName() + "---" + loggerContextLoggerShdsvc.getLevel());

    final String currentLogLevel = loggerContextLogger.getLevel().toString();
    loggerContextLogger.setLevel(Level.toLevel(logLevels.getFirst()));
    loggerContextLoggerShdsvc.setLevel(Level.toLevel(logLevels.getFirst()));
    final String proposedLogLevel = loggerContextLogger.getLevel().toString();

    System.out.println(
        loggerContextLoggerShdsvc.getName() + "---" + loggerContextLoggerShdsvc.getLevel());

    sendResponse(
        generateTestsLogsResponse(currentLogLevel, proposedLogLevel), channelHandlerContext);
  }

  private static String generateTestsLogsResponse(
      final String oldLogLevel, final String newLogLevel) {
    Map<String, Object> testsLogsResponse = new HashMap<>();
    testsLogsResponse.put("oldLogLevel", oldLogLevel);
    testsLogsResponse.put("newLogLevel", newLogLevel);
    try {
      return CommonUtilities.objectMapperProvider().writeValueAsString(testsLogsResponse);
    } catch (JsonProcessingException e) {
      return Constants.TESTS_LOGS_RESPONSE;
    }
  }
}
