package gateway.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import gateway.service.dtos.AuthToken;
import io.github.bibekaryal86.shdsvc.Connector;
import io.github.bibekaryal86.shdsvc.dtos.Enums;
import io.github.bibekaryal86.shdsvc.dtos.HttpResponse;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;

public class Validate {

  private static final String VALIDATE_TOKEN_API =
      CommonUtilities.getSystemEnvProperty(Constants.VALIDATE_TOKEN_URL);

  public static HttpResponse<AuthToken> validateToken(final String tokenToValidate, final int appIdToValidate) {
    final String validateTokenApiUrl = String.format(VALIDATE_TOKEN_API, appIdToValidate);
      return Connector.sendRequest(
              validateTokenApiUrl,
              Enums.HttpMethod.GET,
              new TypeReference<AuthToken>() {},
              tokenToValidate,
              null,
              null);
  }
}
