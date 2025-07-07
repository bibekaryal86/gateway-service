package gateway.service.proxy;

import gateway.service.dtos.GatewayDbRequestDetails;
import gateway.service.dtos.GatewayDbResponseDetails;
import gateway.service.utils.AppConfigs;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import gateway.service.utils.Gateway;
import gateway.service.utils.Validate;
import io.github.bibekaryal86.shdsvc.dtos.ResponseMetadata;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.jetbrains.annotations.NotNull;
import org.postgresql.util.PGobject;
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

        if (!fullHttpRequest.method().equals(HttpMethod.POST)) {
          circuitBreaker.markFailure();
          logger.error("[{}] Invalid DB Proxy Request...", gatewayDbRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
              channelHandlerContext,
              HttpResponseStatus.METHOD_NOT_ALLOWED,
              "Method Not Allowed...");
          return;
        }

        final boolean isAuthorized =
            checkBasicAuthorization(gatewayDbRequestDetails.getRequestId(), fullHttpRequest);

        if (!isAuthorized) {
          circuitBreaker.markFailure();
          logger.error("[{}] Unauthorized DB Response...", gatewayDbRequestDetails.getRequestId());
          Gateway.sendErrorResponse(
              channelHandlerContext, HttpResponseStatus.UNAUTHORIZED, "Unauthorized Request...");
          return;
        }

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

    if (throwable instanceof IllegalArgumentException
        && throwable.getMessage() != null
        && throwable.getMessage().contains("Request Body Error")) {
      Gateway.sendErrorResponse(
          channelHandlerContext, HttpResponseStatus.BAD_REQUEST, throwable.getMessage());
    } else {
      Gateway.sendErrorResponse(
          channelHandlerContext,
          HttpResponseStatus.INTERNAL_SERVER_ERROR,
          "DB Proxy Handler Exception...");
    }
  }

  private GatewayDbRequestDetails extractGatewayDbRequestDetails(
      final ChannelHandlerContext channelHandlerContext, final FullHttpRequest fullHttpRequest) {
    GatewayDbRequestDetails requestBody = null;
    try {
      final ByteBuf byteBuf = fullHttpRequest.content();
      if (byteBuf != null) {
        requestBody =
            CommonUtilities.objectMapperProvider()
                .readValue(
                    (InputStream) new ByteBufInputStream(byteBuf), GatewayDbRequestDetails.class);
      }
    } catch (Exception ex) {
      throw new RuntimeException("Request Body Error: Serializing...", ex);
    }

    if (requestBody == null) {
      throw new IllegalArgumentException("Request Body Error: Missing...");
    } else {
      requestBody.setClientId(Common.extractClientId(channelHandlerContext));
    }

    final String errors = Validate.validateGatewayDbRequestDetails(requestBody);
    if (!CommonUtilities.isEmpty(errors)) {
      throw new IllegalArgumentException("Request Body Error: " + errors);
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
    final DataSource dataSource =
        AppConfigs.getTargetDataSource(gatewayDbRequestDetails.getDatabase());
    if (dataSource == null) {
      throw new IllegalStateException("Datasource not found...");
    }

    try (final Connection connection = dataSource.getConnection()) {
      return switch (gatewayDbRequestDetails.getAction()) {
        case "CREATE" -> handleCreate(connection, gatewayDbRequestDetails);
        case "READ" -> handleRead(connection, gatewayDbRequestDetails);
        case "UPDATE" -> handleUpdate(connection, gatewayDbRequestDetails);
        case "DELETE" -> handleDelete(connection, gatewayDbRequestDetails);
        case "RAW" -> handleRaw(connection, gatewayDbRequestDetails);
        default -> throw new IllegalArgumentException("Invalid Database Action Request...");
      };
    }
  }

  private GatewayDbResponseDetails handleCreate(
      final Connection connection, final GatewayDbRequestDetails gatewayDbRequestDetails)
      throws SQLException {
    final StringBuilder query =
        new StringBuilder("INSERT INTO ").append(gatewayDbRequestDetails.getTable()).append(" (");
    final StringBuilder placeholders = new StringBuilder(" VALUES (");
    final List<Object> params = new ArrayList<>();
    boolean first = true;
    int affectedRows = 0;

    for (final GatewayDbRequestDetails.GatewayDbRequestInputs input :
        gatewayDbRequestDetails.getValues()) {
      if (!first) {
        query.append(", ");
        placeholders.append(", ");
      }
      query.append(input.getTheKey());
      placeholders.append("?");
      params.add(convertValue(input.getTheValue(), input.getTheType()));
      first = false;
    }

    query.append(")").append(placeholders).append(")");

    logger.info("Query Create: {}", query);
    logger.info("Params Create: {}", params);
    try (PreparedStatement stmt = connection.prepareStatement(query.toString())) {
      for (int i = 0; i < params.size(); i++) {
        stmt.setObject(i + 1, params.get(i));
      }
      affectedRows = stmt.executeUpdate();
    }

    final ResponseMetadata.ResponseCrudInfo responseCrudInfo =
        new ResponseMetadata.ResponseCrudInfo(affectedRows, 0, 0, 0);
    final ResponseMetadata responseMetadata =
        new ResponseMetadata(
            ResponseMetadata.emptyResponseStatusInfo(),
            responseCrudInfo,
            ResponseMetadata.emptyResponsePageInfo());
    return new GatewayDbResponseDetails(
        gatewayDbRequestDetails.getRequestId(),
        Collections.emptyList(),
        gatewayDbRequestDetails.getGatewayDbRequestMetadata(),
        responseMetadata);
  }

  private GatewayDbResponseDetails handleRead(
      final Connection connection, final GatewayDbRequestDetails gatewayDbRequestDetails)
      throws SQLException {
    final StringBuilder query = new StringBuilder("SELECT ");

    // FROM
    if (CommonUtilities.isEmpty(gatewayDbRequestDetails.getColumns())) {
      query.append("*");
    } else {
      query.append(String.join(", ", gatewayDbRequestDetails.getColumns()));
    }

    query.append(" FROM ").append(gatewayDbRequestDetails.getTable());

    // WHERE
    final List<Object> params = new ArrayList<>();
    if (!CommonUtilities.isEmpty(gatewayDbRequestDetails.getWhere())) {
      query.append(" WHERE ");
      boolean first = true;

      for (final GatewayDbRequestDetails.GatewayDbRequestInputs where :
          gatewayDbRequestDetails.getWhere()) {
        if (!first) {
          query.append(" AND ");
        }

        query.append(where.getTheKey()).append(" = ?");
        params.add(convertValue(where.getTheValue(), where.getTheType()));
        first = false;
      }
    }

    // REQUEST METADATA
    final GatewayDbRequestDetails.GatewayDbRequestMetadata gatewayDbRequestMetadata =
        gatewayDbRequestDetails.getGatewayDbRequestMetadata();
    int perPage = GatewayDbRequestDetails.emptyGatewayDbRequestMetadata().getPerPage();
    int pageNumber = GatewayDbRequestDetails.emptyGatewayDbRequestMetadata().getPageNumber();

    if (gatewayDbRequestMetadata != null) {
      // ORDER BY
      if (!CommonUtilities.isEmpty(gatewayDbRequestMetadata.getSortColumn())) {
        query.append(" ORDER BY ").append(gatewayDbRequestMetadata.getSortColumn());

        if (!CommonUtilities.isEmpty(gatewayDbRequestMetadata.getSortDirection())) {
          query.append(" ").append(gatewayDbRequestMetadata.getSortDirection());
        }
      }

      // LIMIT AND OFFSET
      perPage = gatewayDbRequestMetadata.getPerPage();
      pageNumber = gatewayDbRequestMetadata.getPageNumber();

      if (perPage > 0) {
        query.append(" LIMIT ?");
        params.add(perPage);

        if (pageNumber > 0) {
          query.append(" OFFSET ?");
          params.add((pageNumber - 1) * perPage);
        }
      }
    }

    logger.info("Query Read: {}", query);
    logger.info("Params Read: {}", params);
    final List<Map<String, Object>> results = executeQuery(connection, query.toString(), params);
    final long totalItems = getTotalCount(connection, gatewayDbRequestDetails);
    final double totalPages = Math.ceil((double) totalItems / perPage);
    final ResponseMetadata.ResponsePageInfo responsePageInfo =
        new ResponseMetadata.ResponsePageInfo(
            (int) totalItems, (int) totalPages, pageNumber, perPage);
    final ResponseMetadata responseMetadata =
        new ResponseMetadata(
            ResponseMetadata.emptyResponseStatusInfo(),
            ResponseMetadata.emptyResponseCrudInfo(),
            responsePageInfo);

    return new GatewayDbResponseDetails(
        gatewayDbRequestDetails.getRequestId(),
        results,
        gatewayDbRequestDetails.getGatewayDbRequestMetadata(),
        responseMetadata);
  }

  private GatewayDbResponseDetails handleUpdate(
      final Connection connection, final GatewayDbRequestDetails gatewayDbRequestDetails)
      throws SQLException {
    final StringBuilder query =
        new StringBuilder("UPDATE ").append(gatewayDbRequestDetails.getTable()).append(" SET ");
    final List<Object> params = new ArrayList<>();
    boolean first = true;
    int affectedRows = 0;

    for (final GatewayDbRequestDetails.GatewayDbRequestInputs set :
        gatewayDbRequestDetails.getSet()) {
      if (!first) {
        query.append(", ");
      }

      query.append(set.getTheKey()).append(" = ?");
      params.add(convertValue(set.getTheValue(), set.getTheType()));
      first = false;
    }

    first = true;
    if (!CommonUtilities.isEmpty(gatewayDbRequestDetails.getWhere())) {
      query.append(" WHERE ");

      for (final GatewayDbRequestDetails.GatewayDbRequestInputs where :
          gatewayDbRequestDetails.getWhere()) {
        if (!first) {
          query.append(" AND ");
        }

        query.append(where.getTheKey()).append(" = ?");
        params.add(convertValue(where.getTheValue(), where.getTheType()));
        first = false;
      }
    }

    logger.info("Query Update: {}", query);
    logger.info("Params Update: {}", params);
    try (final PreparedStatement stmt = connection.prepareStatement(query.toString())) {
      for (int i = 0; i < params.size(); i++) {
        stmt.setObject(i + 1, params.get(i));
      }
      affectedRows = stmt.executeUpdate();
    }

    final ResponseMetadata.ResponseCrudInfo responseCrudInfo =
        new ResponseMetadata.ResponseCrudInfo(0, affectedRows, 0, 0);
    final ResponseMetadata responseMetadata =
        new ResponseMetadata(
            ResponseMetadata.emptyResponseStatusInfo(),
            responseCrudInfo,
            ResponseMetadata.emptyResponsePageInfo());
    return new GatewayDbResponseDetails(
        gatewayDbRequestDetails.getRequestId(),
        Collections.emptyList(),
        gatewayDbRequestDetails.getGatewayDbRequestMetadata(),
        responseMetadata);
  }

  private GatewayDbResponseDetails handleDelete(
      final Connection connection, final GatewayDbRequestDetails gatewayDbRequestDetails)
      throws SQLException {
    final StringBuilder query =
        new StringBuilder("DELETE FROM ").append(gatewayDbRequestDetails.getTable());
    final List<Object> params = new ArrayList<>();
    boolean first = true;
    int affectedRows = 0;

    if (!CommonUtilities.isEmpty(gatewayDbRequestDetails.getWhere())) {
      query.append(" WHERE ");

      for (final GatewayDbRequestDetails.GatewayDbRequestInputs where :
          gatewayDbRequestDetails.getWhere()) {
        if (!first) {
          query.append(" AND ");
        }

        query.append(where.getTheKey()).append(" = ?");
        params.add(convertValue(where.getTheValue(), where.getTheType()));
        first = false;
      }
    }

    logger.info("Query Delete: {}", query);
    logger.info("Params Delete: {}", params);
    try (final PreparedStatement stmt = connection.prepareStatement(query.toString())) {
      for (int i = 0; i < params.size(); i++) {
        stmt.setObject(i + 1, params.get(i));
      }
      affectedRows = stmt.executeUpdate();
    }

    final ResponseMetadata.ResponseCrudInfo responseCrudInfo =
        new ResponseMetadata.ResponseCrudInfo(0, 0, affectedRows, 0);
    final ResponseMetadata responseMetadata =
        new ResponseMetadata(
            ResponseMetadata.emptyResponseStatusInfo(),
            responseCrudInfo,
            ResponseMetadata.emptyResponsePageInfo());
    return new GatewayDbResponseDetails(
        gatewayDbRequestDetails.getRequestId(),
        Collections.emptyList(),
        gatewayDbRequestDetails.getGatewayDbRequestMetadata(),
        responseMetadata);
  }

  private GatewayDbResponseDetails handleRaw(
      final Connection connection, final GatewayDbRequestDetails gatewayDbRequestDetails)
      throws SQLException {
    if (!gatewayDbRequestDetails.getQuery().trim().toUpperCase().startsWith("SELECT")) {
      throw new IllegalArgumentException("Raw queries can only be SELECT statements...");
    }

    final List<Map<String, Object>> results =
        executeQuery(
            connection, gatewayDbRequestDetails.getQuery(), gatewayDbRequestDetails.getParams());
    return new GatewayDbResponseDetails(
        gatewayDbRequestDetails.getRequestId(),
        results,
        GatewayDbRequestDetails.emptyGatewayDbRequestMetadata(),
        ResponseMetadata.emptyResponseMetadata());
  }

  private long getTotalCount(
      final Connection connection, final GatewayDbRequestDetails gatewayDbRequestDetails)
      throws SQLException {
    final StringBuilder query =
        new StringBuilder("SELECT COUNT(*) FROM ").append(gatewayDbRequestDetails.getTable());
    final List<Object> params = new ArrayList<>();

    if (!CommonUtilities.isEmpty(gatewayDbRequestDetails.getWhere())) {
      query.append(" WHERE ");
      boolean first = true;

      for (final GatewayDbRequestDetails.GatewayDbRequestInputs where :
          gatewayDbRequestDetails.getWhere()) {
        if (!first) {
          query.append(" AND ");
        }

        query.append(where.getTheKey()).append(" = ?");
        params.add(convertValue(where.getTheValue(), where.getTheType()));
        first = false;
      }
    }

    logger.info("Query Total: {}", query);
    logger.info("Params Total: {}", params);
    try (final PreparedStatement stmt = connection.prepareStatement(query.toString())) {
      for (int i = 0; i < params.size(); i++) {
        stmt.setObject(i + 1, params.get(i));
      }

      try (final ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
        return 0;
      }
    }
  }

  private List<Map<String, Object>> executeQuery(
      Connection connection, String query, List<Object> params) throws SQLException {
    try (final PreparedStatement stmt = connection.prepareStatement(query)) {
      for (int i = 0; i < params.size(); i++) {
        stmt.setObject(i + 1, params.get(i));
      }

      final ResultSet rs = stmt.executeQuery();
      final List<Map<String, Object>> rows = new ArrayList<>();

      final int columnCount = rs.getMetaData().getColumnCount();
      while (rs.next()) {
        final Map<String, Object> row = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
          row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
        }
        rows.add(row);
      }

      return rows;
    }
  }

  private Object convertValue(final Object value, final String type)
      throws IllegalArgumentException {
    if (value == null) {
      return null;
    }

    if (type == null) {
      return value;
    }

    String stringValue = value.toString();

    try {
      switch (type) {
        // Boolean types
        case "BOOLEAN":
        case "BOOL":
          if (value instanceof Boolean) {
            return value;
          }
          stringValue = stringValue.toLowerCase();
          return stringValue.equals("true")
              || stringValue.equals("1")
              || stringValue.equals("t")
              || stringValue.equals("y");
        // Integer types
        case "INTEGER":
        case "INT":
          if (value instanceof Integer) {
            return value;
          }
          return Integer.parseInt(stringValue);
        // Long/Bigint types
        case "BIGINT":
        case "LONG":
          if (value instanceof Long) {
            return value;
          }
          return Long.parseLong(stringValue);

        // Decimal/Numeric types
        case "DECIMAL":
        case "NUMERIC":
          if (value instanceof BigDecimal) {
            return value;
          }
          return new BigDecimal(stringValue);

        // String types (no conversion needed)
        case "VARCHAR":
        case "CHAR":
        case "TEXT":
          return stringValue;

        // JSON types
        case "JSONB":
          try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(stringValue);
            return jsonObject;
          } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON value: " + stringValue, e);
          }

        // Date types
        case "DATE":
          if (value instanceof Date) {
            return value;
          }
          try {
            LocalDate date = LocalDate.parse(stringValue);
            return Date.valueOf(date);
          } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Invalid date format. Expected yyyy-MM-dd: " + stringValue, e);
          }

        // Timestamp types
        case "TIMESTAMP":
          if (value instanceof Timestamp) {
            return value;
          }
          try {
            // Try ISO-8601 format first
            LocalDateTime dateTime = LocalDateTime.parse(stringValue);
            return Timestamp.valueOf(dateTime);
          } catch (DateTimeParseException e1) {
            try {
              // Fallback to JDBC timestamp format
              return Timestamp.valueOf(stringValue);
            } catch (IllegalArgumentException e2) {
              throw new IllegalArgumentException(
                  "Invalid timestamp format. Expected yyyy-MM-dd HH:mm:ss[.SSS] or ISO-8601: "
                      + stringValue,
                  e2);
            }
          }

        default:
          return value; // No conversion for unrecognized types
      }
    } catch (Exception e) {
      throw new IllegalArgumentException(
          String.format(
              "Request Body Error: Failed to convert value '%s' to type %s: %s",
              stringValue, type, e.getMessage()),
          e);
    }
  }
}
