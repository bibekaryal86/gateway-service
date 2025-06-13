package gateway.service.dtos;

import io.netty.handler.codec.http.HttpMethod;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GatewayDbRequestDetails implements Serializable {
  private final String requestId;
  private final String database;
  private final HttpMethod action;

  // CRUD, Raw
  private final String table;
  // RUD
  private final Map<String, Object> where;
  // C
  private final Map<String, Object> values;
  // R
  private final List<String> columns;
  // U
  private final Map<String, Object> set;
  // Raw
  private final String query;
  private final List<Object> params;

  public GatewayDbRequestDetails(
      final String database,
      final HttpMethod action,
      final String table,
      final Map<String, Object> where,
      final Map<String, Object> values,
      final List<String> columns,
      final Map<String, Object> set,
      final String query,
      final List<Object> params) {
    this.requestId = UUID.randomUUID().toString();
    this.database = database;
    this.action = action;
    this.table = table;
    this.where = where;
    this.values = values;
    this.columns = columns;
    this.set = set;
    this.query = query;
    this.params = params;
  }

  public String getRequestId() {
    return requestId;
  }

  public String getDatabase() {
    return database;
  }

  public HttpMethod getAction() {
    return action;
  }

  public String getTable() {
    return table;
  }

  public Map<String, Object> getWhere() {
    return where;
  }

  public Map<String, Object> getValues() {
    return values;
  }

  public List<String> getColumns() {
    return columns;
  }

  public Map<String, Object> getSet() {
    return set;
  }

  public String getQuery() {
    return query;
  }

  public List<Object> getParams() {
    return params;
  }

  @Override
  public String toString() {
    return "GatewayDbRequestDetails{"
        + "requestId='"
        + requestId
        + '\''
        + ", database='"
        + database
        + '\''
        + ", action="
        + action
        + ", table='"
        + table
        + '\''
        + ", where="
        + where
        + ", values="
        + values
        + ", columns="
        + columns
        + ", set="
        + set
        + ", query='"
        + query
        + '\''
        + ", params="
        + params
        + '}';
  }
}
