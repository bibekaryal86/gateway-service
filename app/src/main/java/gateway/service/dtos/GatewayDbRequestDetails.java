package gateway.service.dtos;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GatewayDbRequestDetails implements Serializable {
  private final String requestId;
  private final long startTime;

  private String clientId;

  // CRUD, Raw
  private final String database;
  private final String action;
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
      final String action,
      final String table,
      final Map<String, Object> where,
      final Map<String, Object> values,
      final List<String> columns,
      final Map<String, Object> set,
      final String query,
      final List<Object> params) {
    this.requestId = UUID.randomUUID().toString();
    this.startTime = System.nanoTime();

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

  public long getStartTime() {
    return startTime;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getDatabase() {
    return database;
  }

  public String getAction() {
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
        + ", startTime="
        + startTime
        + ", clientId='"
        + clientId
        + '\''
        + ", database='"
        + database
        + '\''
        + ", action='"
        + action
        + '\''
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

  public String toStringLimited() {
    return "GatewayDbRequestDetails{"
        + "requestId='"
        + requestId
        + '\''
        + ", startTime="
        + startTime
        + ", clientId='"
        + clientId
        + '\''
        + ", database='"
        + database
        + '\''
        + ", action='"
        + action
        + '\''
        + ", table='"
        + table
        + '\''
        + '}';
  }
}
