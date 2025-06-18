package gateway.service.proxy;

import gateway.service.dtos.GatewayDbRequestDetails;
import gateway.service.dtos.GatewayDbResponseDetails;
import gateway.service.utils.AppConfigs;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.Gateway;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbProxyHandler extends ChannelInboundHandlerAdapter {
  private static final Logger logger = LoggerFactory.getLogger(DbProxyHandler.class);

  private final String dbProxyEndpoint;

  public DbProxyHandler(final String dbProxyEndpoint) {
    this.dbProxyEndpoint = dbProxyEndpoint;
  }

  private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
  private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

  @Override
  public void channelRead(
      @NotNull final ChannelHandlerContext channelHandlerContext, @NotNull final Object object)
      throws Exception {
    if (object instanceof FullHttpRequest fullHttpRequest) {
      final String requestUri = fullHttpRequest.uri();
      if (requestUri.equals(dbProxyEndpoint)) {
        final GatewayDbRequestDetails gatewayDbRequestDetails =
            extractGatewayDbRequestDetails(channelHandlerContext, fullHttpRequest);
        logGatewayDbRequestDetails(gatewayDbRequestDetails);

        final CircuitBreaker circuitBreaker =
            circuitBreakers.computeIfAbsent(
                gatewayDbRequestDetails.getDatabase(),
                key ->
                    new CircuitBreaker(Constants.CB_FAILURE_THRESHOLD, Constants.CB_OPEN_TIMEOUT));
        final RateLimiter rateLimiter =
            rateLimiters.computeIfAbsent(
                gatewayDbRequestDetails.getClientId(),
                key -> new RateLimiter(Constants.RL_MAX_REQUESTS, Constants.RL_TIME_WINDOW_MILLIS));

        final boolean isAuthorized =
            checkBasicAuthorization(gatewayDbRequestDetails.getRequestId(), fullHttpRequest);

        if (isAuthorized) {
          if (!circuitBreaker.allowRequest()) {
            logger.error(
                "[{}] CircuitBreaker DB Response: [{}]",
                gatewayDbRequestDetails.getRequestId(),
                circuitBreaker);
            Gateway.sendErrorResponse(
                channelHandlerContext,
                HttpResponseStatus.SERVICE_UNAVAILABLE,
                "Maximum DB Failures Allowed Exceeded...");
            return;
          }

          if (!rateLimiter.allowRequest()) {
            logger.error(
                "[{}] RateLimiter DB Response: [{}]",
                gatewayDbRequestDetails.getRequestId(),
                rateLimiter);
            Gateway.sendErrorResponse(
                channelHandlerContext,
                HttpResponseStatus.TOO_MANY_REQUESTS,
                "Maximum Request Allowed Exceeded...");
            return;
          }
        } else {
          circuitBreaker.markFailure();
          logger.error("[{}] Unauthorized DB Response...", gatewayDbRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
              channelHandlerContext, HttpResponseStatus.UNAUTHORIZED, "Unauthorized Request...");
          return;
        }

        try {
          final GatewayDbResponseDetails gatewayDbResponseDetails =
              executeDbAction(gatewayDbRequestDetails);
          circuitBreaker.markSuccess();
          logGatewayDbResponseDetails(
              gatewayDbRequestDetails.getStartTime(), gatewayDbResponseDetails);
          Gateway.sendResponse(
              CommonUtilities.writeValueAsStringNoEx(gatewayDbResponseDetails),
              channelHandlerContext);
        } catch (Exception ex) {
          circuitBreaker.markFailure();
          logger.error("[{}] Proxy Handler Error...", gatewayDbRequestDetails.getRequestId(), ex);
          Gateway.sendErrorResponse(
              channelHandlerContext, HttpResponseStatus.BAD_GATEWAY, "DB Proxy Handler Error...");
        }
      } else {
        super.channelRead(channelHandlerContext, object);
      }
    }
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext, final Throwable throwable) {
    final GatewayDbRequestDetails gatewayDbRequestDetails =
        channelHandlerContext.channel().attr(Constants.GATEWAY_DB_REQUEST_DETAILS_KEY).get();

    logger.error(
        "[{}] DB Proxy Handler Exception Caught...",
        Common.getDbRequestId(gatewayDbRequestDetails),
        throwable);

    Gateway.sendErrorResponse(
        channelHandlerContext,
        HttpResponseStatus.INTERNAL_SERVER_ERROR,
        "DB Proxy Handler Exception...");
  }

  private GatewayDbRequestDetails extractGatewayDbRequestDetails(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    GatewayDbRequestDetails requestBody = null;
    try {
      ByteBuf byteBuf = fullHttpRequest.content();
      if (byteBuf != null) {
        requestBody =
            CommonUtilities.objectMapperProvider()
                .readValue(
                    (InputStream) new ByteBufInputStream(byteBuf), GatewayDbRequestDetails.class);
      }
    } catch (Exception ex) {
      throw new RuntimeException("Error Serializing Request Body...");
    }

    if (requestBody == null) {
      throw new IllegalArgumentException("Request Body is Missing...");
    } else {
      requestBody.setClientId(Common.extractClientId(channelHandlerContext));
    }

    final String validation = Common.validateGatewayDbRequest(requestBody);
    if (!CommonUtilities.isEmpty(validation)) {
      throw new IllegalArgumentException(validation);
    }

    return requestBody;
  }

  private void logGatewayDbRequestDetails(final GatewayDbRequestDetails gatewayDbRequestDetails) {
    logger.info(
        "[{}] DB Request IN: [{}]",
        gatewayDbRequestDetails.getRequestId(),
        gatewayDbRequestDetails.toStringLimited());
  }

  private void logGatewayDbResponseDetails(
      final long startTime, final GatewayDbResponseDetails gatewayDbResponseDetails) {
    logger.info(
        "[{}] DB Response OUT: [{}] | [{}] in [{}s]",
        gatewayDbResponseDetails.requestId(),
        gatewayDbResponseDetails.results().size(),
        gatewayDbResponseDetails.responseMetadata(),
        String.format("%.2f", (System.nanoTime() - startTime) / 1e9d));
  }

  private boolean checkBasicAuthorization(
      final String requestId, final FullHttpRequest fullHttpRequest) {
    final String authHeader = fullHttpRequest.headers().get(HttpHeaderNames.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Basic ")) {
      logger.error("[{}] DB Request NOT Authenticated...", requestId);
      return false;
    }

    final String base64Credentials = authHeader.substring("Basic ".length());
    final String credentials =
        new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);

    final String[] parts = credentials.split(":", 2);
    if (parts.length != 2) {
      logger.error("[{}] DB Request NOT Valid Format...", requestId);
      return false;
    }

    final String username = parts[0];
    final String password = parts[1];
    final String validUsername = CommonUtilities.getSystemEnvProperty(Constants.DB_PROXY_USR);
    final String validPassword = CommonUtilities.getSystemEnvProperty(Constants.DB_PROXY_PWD);

    return validUsername.equals(username) && validPassword.equals(password);
  }

  private GatewayDbResponseDetails executeDbAction(
      final GatewayDbRequestDetails gatewayDbRequestDetails) throws Exception {
    DataSource dataSource = AppConfigs.getTargetDataSource(gatewayDbRequestDetails.getDatabase());
    if (dataSource == null) {
      throw new IllegalStateException("Datasource not found...");
    }

    try (Connection connection = dataSource.getConnection()) {
      return switch (gatewayDbRequestDetails.getAction()) {
        case "CREATE" -> handleCreate(gatewayDbRequestDetails);
        case "READ" -> handleRead(gatewayDbRequestDetails);
        case "UPDATE" -> handleUpdate(gatewayDbRequestDetails);
        case "DELETE" -> handleDelete(gatewayDbRequestDetails);
        case "RAW" -> handleRaw(gatewayDbRequestDetails);
        default -> throw new IllegalArgumentException("Invalid Database Action Request...");
      };
    }
  }

  private GatewayDbResponseDetails handleCreate(
      final GatewayDbRequestDetails gatewayDbRequestDetails) {
    return null;
  }

  private GatewayDbResponseDetails handleRead(
      final GatewayDbRequestDetails gatewayDbRequestDetails) {
    return null;
  }

  private GatewayDbResponseDetails handleUpdate(
      final GatewayDbRequestDetails gatewayDbRequestDetails) {
    return null;
  }

  private GatewayDbResponseDetails handleDelete(
      final GatewayDbRequestDetails gatewayDbRequestDetails) {
    return null;
  }

  private GatewayDbResponseDetails handleRaw(
      final GatewayDbRequestDetails gatewayDbRequestDetails) {
    return null;
  }

  private List<Map<String, Object>> executeQuery(
      Connection connection, String query, List<Object> params) throws SQLException {
    try (PreparedStatement stmt = connection.prepareStatement(query)) {
      for (int i = 0; i < params.size(); i++) {
        stmt.setObject(i + 1, params.get(i));
      }

      ResultSet rs = stmt.executeQuery();
      List<Map<String, Object>> rows = new ArrayList<>();

      int columnCount = rs.getMetaData().getColumnCount();
      while (rs.next()) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
          row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
        }
        rows.add(row);
      }

      return rows;
    }
  }
}
