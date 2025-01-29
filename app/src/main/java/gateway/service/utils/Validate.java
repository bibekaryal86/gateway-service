package gateway.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.bibekaryal86.shdsvc.Connector;
import io.github.bibekaryal86.shdsvc.dtos.Enums;
import io.github.bibekaryal86.shdsvc.dtos.EnvDetailsResponse;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import io.netty.handler.codec.http.HttpResponseStatus;

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
}
