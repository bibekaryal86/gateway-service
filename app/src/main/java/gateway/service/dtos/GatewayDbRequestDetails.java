package gateway.service.dtos;

import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class GatewayDbRequestDetails implements Serializable {

  private final String requestId;
  private final long startTime;

  // this is not set for logging and rate limiting
  // and not received with the request object
  private String clientId;

  // CRUD, Raw
  private final String database;
  private final String action;
  private final String table;
  // RUD
  private final List<GatewayDbRequestInputs> where;
  // C
  private final List<GatewayDbRequestInputs> values;
  // R
  private final List<String> columns;
  // U
  private final List<GatewayDbRequestInputs> set;
  // Raw
  private final String query;
  private final List<Object> params;

  // metadata
  private final GatewayDbRequestMetadata gatewayDbRequestMetadata;

  public GatewayDbRequestDetails(
      final String database,
      final String action,
      final String table,
      final List<GatewayDbRequestInputs> where,
      final List<GatewayDbRequestInputs> values,
      final List<String> columns,
      final List<GatewayDbRequestInputs> set,
      final String query,
      final List<Object> params,
      final GatewayDbRequestMetadata gatewayDbRequestMetadata) {
    this.requestId = UUID.randomUUID().toString();
    this.startTime = System.nanoTime();

    this.database = database;
    this.action = action;
    this.table = table;
    this.where = where == null ? Collections.emptyList() : where;
    this.values = values == null ? Collections.emptyList() : values;
    this.columns = columns == null ? Collections.emptyList() : columns;
    this.set = set == null ? Collections.emptyList() : set;
    this.query = query;
    this.params = params == null ? Collections.emptyList() : params;
    this.gatewayDbRequestMetadata = gatewayDbRequestMetadata == null ? GatewayDbRequestDetails.emptyGatewayDbRequestMetadata() : gatewayDbRequestMetadata;
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

  public List<GatewayDbRequestInputs> getWhere() {
    return where;
  }

  public List<GatewayDbRequestInputs> getValues() {
    return values;
  }

  public List<String> getColumns() {
    return columns;
  }

  public List<GatewayDbRequestInputs> getSet() {
    return set;
  }

  public String getQuery() {
    return query;
  }

  public List<Object> getParams() {
    return params;
  }

  public GatewayDbRequestMetadata getGatewayDbRequestMetadata() {
    return gatewayDbRequestMetadata;
  }

  public static GatewayDbRequestMetadata emptyGatewayDbRequestMetadata() {
    return new GatewayDbRequestMetadata(1, 100, "", "ASC");
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
        + ", gatewayDbRequestMetadata="
        + gatewayDbRequestMetadata
        + '}';
  }

  public String toStringLimited() {
    return "GatewayDbRequestDetails_Limited{"
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
        + ", gatewayDbRequestMetadata="
        + gatewayDbRequestMetadata
        + '}';
  }

  public static class GatewayDbRequestInputs {
    private final String theKey;
    private final Object theValue;
    private final String theType;

    public GatewayDbRequestInputs(String theKey, Object theValue, String theType) {
      this.theKey = theKey;
      this.theValue = theValue;
      this.theType = theType;
    }

    public String getTheKey() {
      return theKey;
    }

    public Object getTheValue() {
      return theValue;
    }

    public String getTheType() {
      return theType;
    }

    @Override
    public String toString() {
      return "GatewayDbRequestInputs{"
          + "theKey='"
          + theKey
          + '\''
          + ", theValue="
          + theValue
          + ", theType='"
          + theType
          + '\''
          + '}';
    }
  }

  public static class GatewayDbRequestMetadata {
    private final int pageNumber;
    private final int perPage;
    private final String sortColumn;
    private final String sortDirection;

    public GatewayDbRequestMetadata(
        final int pageNumber,
        final int perPage,
        final String sortColumn,
        final String sortDirection) {
      this.pageNumber = pageNumber;
      this.perPage = perPage;
      this.sortColumn = CommonUtilities.isEmpty(sortColumn) ? "" : sortColumn;
      this.sortDirection = CommonUtilities.isEmpty(sortDirection) ? "ASC" : sortDirection;;
    }

    public int getPageNumber() {
      return pageNumber;
    }

    public int getPerPage() {
      return perPage;
    }

    public String getSortColumn() {
      return sortColumn;
    }

    public String getSortDirection() {
      return sortDirection;
    }

    @Override
    public String toString() {
      return "GatewayDbRequestMetadata{"
          + "pageNumber="
          + pageNumber
          + ", perPage="
          + perPage
          + ", sortColumn='"
          + sortColumn
          + '\''
          + ", sortDirection='"
          + sortDirection
          + '\''
          + '}';
    }
  }
}
