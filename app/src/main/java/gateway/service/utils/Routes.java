package gateway.service.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import gateway.service.dtos.EnvDetailsResponse;
import gateway.service.logging.LogLogger;
import io.netty.handler.codec.http.HttpResponseStatus;
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

public class Routes {
  private static final LogLogger logger = LogLogger.getLogger(Routes.class);

  private static Timer timer;
  private static Map<String, String> ROUTES_MAP = new HashMap<>();
  private static List<String> AUTH_EXCLUSIONS = new ArrayList<>();
  private static List<String> BASIC_AUTH_APIS = new ArrayList<>();
  private static Map<String, String> AUTH_APPS = new HashMap<>();
  private static List<String> PROXY_HEADERS = new ArrayList<>();

  private static final String ROUTE_API_URL = Common.getSystemEnvProperty(Constants.ROUTES_MAP_URL);
  private static final String ROUTE_API_AUTH =
      Common.getBasicAuth(
          Common.getSystemEnvProperty(Constants.ENVSVC_USR),
          Common.getSystemEnvProperty(Constants.ENVSVC_PWD));

  public static void init() {
    logger.debug("Retrieving Env Details...");

    Connector.HttpResponse response =
        Connector.sendRequest(ROUTE_API_URL, "GET", "", null, ROUTE_API_AUTH);

    if (response.statusCode() == HttpResponseStatus.OK.code()) {
      try {
        final ObjectMapper objectMapper = Common.objectMapperProvider();
        EnvDetailsResponse envDetailResponse =
            objectMapper.readValue(response.responseBody(), EnvDetailsResponse.class);

        if (Common.isEmpty(envDetailResponse.getErrMsg())) {
          List<EnvDetailsResponse.EnvDetails> envDetailsList = envDetailResponse.getEnvDetails();

          AUTH_EXCLUSIONS =
              envDetailsList.stream()
                  .filter(envDetail -> envDetail.getName().equals(Constants.AUTH_EXCLUSIONS_NAME))
                  .findFirst()
                  .orElseThrow()
                  .getListValue();
          logger.debug(
              "Gateway Service Env Details Auth Exclusions Size: [{}]", AUTH_EXCLUSIONS.size());

          BASIC_AUTH_APIS =
              envDetailsList.stream()
                  .filter(envDetail -> envDetail.getName().equals(Constants.BASIC_AUTH_NAME))
                  .findFirst()
                  .orElseThrow()
                  .getListValue();
          logger.debug(
              "Gateway Service Env Details Basic Auth Apis Size: [{}]", BASIC_AUTH_APIS.size());

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
                                      Common.getSystemEnvProperty(Constants.SPRING_PROFILES_ACTIVE)
                                          .toUpperCase())))
                  .findFirst()
                  .orElseThrow()
                  .getMapValue();
          logger.debug("Gateway Service Env Details Routes Map Size: [{}]", ROUTES_MAP.size());

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
                          Map.Entry::getKey,
                          entry -> decryptSecret(entry.getValue(), entry.getKey())));
          logger.debug("Gateway Service Env Details Auth Map Size: [{}]", AUTH_APPS.size());

          PROXY_HEADERS =
              envDetailsList.stream()
                  .filter(envDetail -> envDetail.getName().equals(Constants.PROXY_HEADERS))
                  .findFirst()
                  .orElseThrow()
                  .getListValue();
          logger.debug(
              "Gateway Service Env Details Proxy Headers Size: [{}]", PROXY_HEADERS.size());
        } else {
          logger.error(
              "Failed to Fetch Gateway Service Env Details, Error Response: [{}]",
              envDetailResponse.getErrMsg());
        }
      } catch (Exception ex) {
        logger.error("Error Retrieving Env Details, Auth Exclusions, Routes Map...", ex);
      }
    } else {
      logger.error(
          "Failed to Fetch Gateway Service Env Details, Response: [{}]", response.statusCode());
    }
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

  private static String decryptSecret(final String encryptedData, final String keyNameForLogging) {
    final String secretKey = Common.getSystemEnvProperty(Constants.SECRET_KEY);
    final byte[] secretKeyBytes = Arrays.copyOf(secretKey.getBytes(), 32);
    final SecretKeySpec secretKeySpec = new SecretKeySpec(secretKeyBytes, "AES");

    try {
      Cipher cipher = Cipher.getInstance("AES");
      cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
      byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
      return new String(decryptedBytes);
    } catch (Exception ex) {
      logger.error("Error Decrypting Secret: [{}]", ex, keyNameForLogging);
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
