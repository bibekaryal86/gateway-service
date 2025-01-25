package gateway.service.utils;

import gateway.service.logging.LogLogger;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

public class Validate {
  private static final LogLogger logger = LogLogger.getLogger(Validate.class);

  private static final String VALIDATE_TOKEN_API =
      Common.getSystemEnvProperty(Constants.VALIDATE_TOKEN_URL);

  public static boolean validateToken(final String tokenToValidate, final int appIdToValidate) {
    logger.debug("Validating Token...");
    final String validateTokenApiUrl = String.format(VALIDATE_TOKEN_API, appIdToValidate);
    return Connector.sendRequest(
                validateTokenApiUrl, HttpMethod.GET.name(), "", null, tokenToValidate)
            .statusCode()
        == HttpResponseStatus.OK.code();
  }
}
