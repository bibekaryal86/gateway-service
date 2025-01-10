package gateway.service.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import gateway.service.dtos.EnvDetailsResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Routes {
  private static final Logger log = LoggerFactory.getLogger(Routes.class);

  private static Timer timer;
  private static final long REFRESH_INTERVAL = 7 * 60 * 1000; // every 7 minutes
  private static Map<String, String> ROUTES_MAP = new HashMap<>();
  private static List<String> AUTH_EXCLUSIONS = new ArrayList<>();

  private static final String ROUTE_API_URL = Common.getSystemEnvProperty(Constants.ROUTES_MAP_URL);
  private static final String ROUTE_API_AUTH =
      Connector.createBasicAuthHeader(
          Common.getSystemEnvProperty(Constants.ENVSVC_USR),
          Common.getSystemEnvProperty(Constants.ENVSVC_PWD));

  public static void init() {
    log.info("Retrieving Env Details...");

    Connector.HttpResponse response =
        Connector.sendRequest(ROUTE_API_URL, "GET", "", null, ROUTE_API_AUTH);

    if (response.statusCode() == 200) {
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
          log.info(
              "Gateway Service Env Details Auth Exclusions List Size: [{}]",
              AUTH_EXCLUSIONS.size());

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
          log.info("Gateway Service Env Details Routes Map Size: [{}]", ROUTES_MAP.size());
        } else {
          log.info(
              "Failed to Fetch Gateway Service Env Details, Error Response: [{}]",
              envDetailResponse.getErrMsg());
        }
      } catch (Exception ex) {
        log.error("Error Retrieving Env Details, Auth Exclusions, Routes Map...", ex);
      }
    } else {
      log.info(
          "Failed to Fetch Gateway Service Env Details, Response: [{}]", response.statusCode());
    }
  }

  public static String getTargetBaseUrl(String requestPath) {
    if (requestPath.startsWith("/")) {
      requestPath = requestPath.substring(1);
    }

    for (final String route : ROUTES_MAP.keySet()) {
      if (requestPath.startsWith(route)) {
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

  // Refresh routes periodically
  public static void refreshRoutes() {
    log.info("Starting up timer...");
    timer = new Timer();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            init();
          }
        },
        0, // Initial delay
        REFRESH_INTERVAL // Subsequent delay rate
        );

    // Add a shutdown hook to gracefully stop the timer
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("Shutting down timer...");
                  if (timer != null) {
                    timer.cancel();
                    timer.purge(); // Removes all canceled tasks from the timer's task queue
                    timer = null;
                  }
                }));
  }
}
