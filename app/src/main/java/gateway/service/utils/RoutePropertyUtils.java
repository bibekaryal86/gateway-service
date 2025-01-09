package gateway.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gateway.service.dtos.EnvDetails;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoutePropertyUtils {
  private static final Logger log = LoggerFactory.getLogger(RoutePropertyUtils.class);

  private static Map<String, String> ROUTES_MAP = new HashMap<>();
  private static List<String> AUTH_EXCLUSIONS = new ArrayList<>();

  private static final String ROUTE_API_URL =
      SystemPropertyUtils.getSystemEnvProperty(Constants.ROUTES_MAP_URL);
  private static final String ROUTE_API_AUTH =
      HttpUtils.createBasicAuthHeader(
          SystemPropertyUtils.getSystemEnvProperty(Constants.ENVSVC_USR),
          SystemPropertyUtils.getSystemEnvProperty(Constants.ENVSVC_PWD));

  public static void init() {
    refreshRoutesPeriodically();

    HttpUtils.HttpResponse response =
        HttpUtils.sendRequest(ROUTE_API_URL, "GET", "", null, ROUTE_API_AUTH);

    if (response.statusCode() == 200) {
      try {
        final ObjectMapper objectMapper = Common.objectMapperProvider();
        List<EnvDetails> envDetails =
            objectMapper.readValue(response.responseBody(), new TypeReference<>() {});
        log.info("Gateway Service Env Details Retrieved Successfully: [{}]", envDetails.size());

        AUTH_EXCLUSIONS =
            envDetails.stream()
                .filter(envDetail -> envDetail.getName().equals(Constants.AUTH_EXCLUSIONS_NAME))
                .findFirst()
                .orElseThrow()
                .getListValue();
        log.info(
            "Gateway Service Env Details Auth Exclusions List Size: [{}]", AUTH_EXCLUSIONS.size());

        ROUTES_MAP =
            envDetails.stream()
                .filter(
                    envDetail ->
                        envDetail
                            .getName()
                            .equals(
                                String.format(
                                    "%s_%s",
                                    Constants.BASE_URLS_NAME_BEGINS_WITH,
                                    SystemPropertyUtils.getSystemEnvProperty(
                                            Constants.SPRING_PROFILES_ACTIVE)
                                        .toUpperCase())))
                .findFirst()
                .orElseThrow()
                .getMapValue();
        log.info("Gateway Service Env Details Routes Map Size: [{}]", ROUTES_MAP.size());
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

  public static List<String> getAuthExclusions() {
    return AUTH_EXCLUSIONS;
  }

  // Refresh routes periodically
  private static void refreshRoutesPeriodically() {
    Timer timer = new Timer();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            init();
          }
        },
        7 * 60 * 1000,
        7 * 60 * 1000); // every 7 minutes
  }
}
