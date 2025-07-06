package gateway.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import gateway.service.dtos.GatewayDbRequestDetails;
import io.github.bibekaryal86.shdsvc.Connector;
import io.github.bibekaryal86.shdsvc.dtos.Enums;
import io.github.bibekaryal86.shdsvc.dtos.EnvDetailsResponse;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.ArrayList;
import java.util.List;

public class Validate {

  private static final String VALIDATE_TOKEN_API =
      CommonUtilities.getSystemEnvProperty(Constants.VALIDATE_TOKEN_URL);

  public static boolean validateToken(final String tokenToValidate, final int appIdToValidate) {
    final String validateTokenApiUrl = String.format(VALIDATE_TOKEN_API, appIdToValidate);
    return Connector.sendRequest(
                validateTokenApiUrl,
                Enums.HttpMethod.GET,
                new TypeReference<EnvDetailsResponse>() {},
                tokenToValidate,
                null,
                null)
            .statusCode()
        == HttpResponseStatus.OK.code();
  }

  public static String validateGatewayDbRequestDetails(
      final GatewayDbRequestDetails gatewayDbRequestDetails) {
    final List<String> errors = new ArrayList<>();

    if (CommonUtilities.isEmpty(gatewayDbRequestDetails.getDatabase())) {
      errors.add("Database is Empty");
    }

    if (CommonUtilities.isEmpty(gatewayDbRequestDetails.getTable())) {
      errors.add("Table is Empty");
    }

    if (CommonUtilities.isEmpty(gatewayDbRequestDetails.getAction())) {
      errors.add("Action is Empty");
    } else {
      if (!gatewayDbRequestDetails.getAction().matches("(?i)CREATE|READ|UPDATE|DELETE|RAW")) {
        errors.add("Action is Invalid");
      }

      if (gatewayDbRequestDetails.getAction().equals("CREATE")
          && CommonUtilities.isEmpty(gatewayDbRequestDetails.getValues())) {
        errors.add("Create requires Values");
      }

      if (gatewayDbRequestDetails.getAction().equals("UPDATE")
          && CommonUtilities.isEmpty(gatewayDbRequestDetails.getSet())) {
        errors.add("Update requires Set");
      }

      if (gatewayDbRequestDetails.getAction().equals("DELETE")
          && CommonUtilities.isEmpty(gatewayDbRequestDetails.getWhere())) {
        errors.add("Delete requires Where");
      }

      if (gatewayDbRequestDetails.getAction().equals("RAW")
          && (CommonUtilities.isEmpty(gatewayDbRequestDetails.getQuery())
              || CommonUtilities.isEmpty(gatewayDbRequestDetails.getParams()))) {
        errors.add("Raw requires Query and Params");
      }
    }

    final GatewayDbRequestDetails.GatewayDbRequestMetadata gatewayDbRequestMetadata =
        gatewayDbRequestDetails.getGatewayDbRequestMetadata();
    if (gatewayDbRequestMetadata != null
        && !CommonUtilities.isEmpty(gatewayDbRequestMetadata.getSortDirection())
        && !gatewayDbRequestMetadata.getSortDirection().matches("(?i)ASC|DESC")) {
      errors.add("SortDirection is Invalid");
    }

    if (!CommonUtilities.isEmpty(gatewayDbRequestDetails.getWhere())) {
      gatewayDbRequestDetails
          .getWhere()
          .forEach(
              where -> {
                if (!CommonUtilities.isEmpty(where.getTheType())
                    && !Constants.VALID_DATA_TYPES.contains(where.getTheType())) {
                  errors.add("DataType is Invalid in Where");
                }
              });
    }

    if (!CommonUtilities.isEmpty(gatewayDbRequestDetails.getValues())) {
      gatewayDbRequestDetails
          .getValues()
          .forEach(
              value -> {
                if (!CommonUtilities.isEmpty(value.getTheType())
                    && !Constants.VALID_DATA_TYPES.contains(value.getTheType())) {
                  errors.add("DataType is Invalid in Value");
                }
              });
    }

    if (!CommonUtilities.isEmpty(gatewayDbRequestDetails.getSet())) {
      gatewayDbRequestDetails
          .getSet()
          .forEach(
              set -> {
                if (!CommonUtilities.isEmpty(set.getTheType())
                    && !Constants.VALID_DATA_TYPES.contains(set.getTheType())) {
                  errors.add("DataType is Invalid in Set");
                }
              });
    }

    return errors.isEmpty() ? "" : String.join(", ", errors);
  }
}
