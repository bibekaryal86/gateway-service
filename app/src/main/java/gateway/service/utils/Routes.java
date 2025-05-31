package gateway.service.utils;

import io.github.bibekaryal86.shdsvc.AppEnvProperty;
import io.github.bibekaryal86.shdsvc.dtos.EnvDetailsResponse;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Routes {
  private static final Logger logger = LoggerFactory.getLogger(Routes.class);

  private static Timer timer;
  private static Map<String, String> ROUTES_MAP = new HashMap<>();
  private static List<String> AUTH_EXCLUSIONS = new ArrayList<>();
  private static List<String> BASIC_AUTH_APIS = new ArrayList<>();
  private static Map<String, String> AUTH_APPS = new HashMap<>();
  private static List<String> PROXY_HEADERS = new ArrayList<>();

  public static void init() {
    logger.debug("Retrieving Env Details...");
    List<EnvDetailsResponse.EnvDetails> envDetailsList =
        AppEnvProperty.getEnvDetailsList(Constants.THIS_APP_NAME, Boolean.TRUE);
    setAuthExclusions(envDetailsList);
    setBasicAuthApis(envDetailsList);
    setRoutesMap(envDetailsList);
    setAuthApps(envDetailsList);
    setProxyHeaders(envDetailsList);
  }

  public static String getTargetBaseUrl(String apiName) {
    for (final String route : ROUTES_MAP.keySet()) {
      if (apiName.equals(route)) {
        return ROUTES_MAP.get(route);
      }
    }
    return null;
  }

  public static Map<String, String> getRoutesMap() {
    return ROUTES_MAP;
  }

  public static List<String> getAuthExclusions() {
    return AUTH_EXCLUSIONS;
  }

  public static List<String> getBasicAuthApis() {
    return BASIC_AUTH_APIS;
  }

  public static Map<String, String> getAuthApps() {
    return AUTH_APPS;
  }

  public static List<String> getProxyHeaders() {
    return PROXY_HEADERS;
  }

  // Refresh routes periodically
  public static void refreshRoutes() {
    logger.info("Starting Routes Timer...");
    timer = new Timer();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            init();
          }
        },
        0,
        Constants.ROUTES_REFRESH_INTERVAL);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.info("Stopping Routes Timer...");
                  if (timer != null) {
                    timer.cancel();
                    timer.purge();
                    timer = null;
                  }
                }));
  }

  private static void setAuthExclusions(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    AUTH_EXCLUSIONS =
        envDetailsList.stream()
            .filter(envDetail -> envDetail.getName().equals(Constants.AUTH_EXCLUSIONS_NAME))
            .findFirst()
            .orElseThrow()
            .getListValue();
    logger.debug("Gateway Service Env Details Auth Exclusions Size: [{}]", AUTH_EXCLUSIONS.size());
  }

  private static void setBasicAuthApis(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    BASIC_AUTH_APIS =
        envDetailsList.stream()
            .filter(envDetail -> envDetail.getName().equals(Constants.BASIC_AUTH_NAME))
            .findFirst()
            .orElseThrow()
            .getListValue();
    logger.debug("Gateway Service Env Details Basic Auth Apis Size: [{}]", BASIC_AUTH_APIS.size());
  }

  private static void setRoutesMap(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    ROUTES_MAP =
        envDetailsList.stream()
            .filter(
                envDetail ->
                    envDetail
                        .getName()
                        .equals(
                            String.format(
                                "%s_%s",
                                Constants.BASE_URLS_NAME_BEGINS_WITH,
                                CommonUtilities.getSystemEnvProperty(
                                        Constants.SPRING_PROFILES_ACTIVE)
                                    .toUpperCase())))
            .findFirst()
            .orElseThrow()
            .getMapValue();
    logger.debug("Gateway Service Env Details Routes Map Size: [{}]", ROUTES_MAP.size());
  }

  private static void setAuthApps(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    AUTH_APPS =
        envDetailsList.stream()
            .filter(envDetail -> envDetail.getName().equals(Constants.AUTH_APPS_NAME))
            .findFirst()
            .orElseThrow()
            .getMapValue()
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, entry -> decryptSecret(entry.getValue(), entry.getKey())));
    logger.debug("Gateway Service Env Details Auth Map Size: [{}]", AUTH_APPS.size());
  }

  private static void setProxyHeaders(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    PROXY_HEADERS =
        envDetailsList.stream()
            .filter(envDetail -> envDetail.getName().equals(Constants.PROXY_HEADERS))
            .findFirst()
            .orElseThrow()
            .getListValue();
    logger.debug("Gateway Service Env Details Proxy Headers Size: [{}]", PROXY_HEADERS.size());
  }

  private static String decryptSecret(final String encryptedData, final String keyNameForLogging) {
    final String secretKey = CommonUtilities.getSystemEnvProperty(Constants.SECRET_KEY);
    final byte[] secretKeyBytes = Arrays.copyOf(secretKey.getBytes(), 32);
    final SecretKeySpec secretKeySpec = new SecretKeySpec(secretKeyBytes, "AES");

    try {
      Cipher cipher = Cipher.getInstance("AES");
      cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
      byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
      return new String(decryptedBytes);
    } catch (Exception ex) {
      logger.error("Error Decrypting Secret: [{}]", keyNameForLogging, ex);
      return "";
    }
  }

  /*
  Encrypt method kept here for reference only

  public static String encryptSecret(String data) throws Exception {
    String secretKey = Common.getSystemEnvProperty(Constants.SECRET_KEY);
    byte[] secretKeyBytes = Arrays.copyOf(secretKey.getBytes(), 32);
    SecretKeySpec keySpec = new SecretKeySpec(secretKeyBytes, "AES");

    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.ENCRYPT_MODE, keySpec);
    byte[] encryptedBytes = cipher.doFinal(data.getBytes());
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }
   */
}
