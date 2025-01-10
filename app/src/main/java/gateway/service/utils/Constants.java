package gateway.service.utils;

import java.time.Duration;
import java.util.List;

public class Constants {
  // provided at runtime
  public static final String ENV_PORT = "ENV_PORT";
  public static final String SPRING_PROFILES_ACTIVE = "SPRING_PROFILES_ACTIVE";
  public static final String ENVSVC_USR = "ENVSVC_USR";
  public static final String ENVSVC_PWD = "ENVSVC_PWD";
  public static final String AUTHSVC_USR = "AUTHSVC_USR";
  public static final String AUTHSVC_PWD = "AUTHSVC_PWD";
  public static final String ROUTES_MAP_URL = "ROUTES_MAP_URL";
  public static final List<String> ENV_KEY_NAMES =
      List.of(
          ENV_PORT,
          SPRING_PROFILES_ACTIVE,
          ENVSVC_USR,
          ENVSVC_PWD,
          AUTHSVC_USR,
          AUTHSVC_PWD,
          ROUTES_MAP_URL);

  // ENV DETAILS
  public static final String AUTH_EXCLUSIONS_NAME = "AUTH_EXCLUSIONS_ENDS_WITH";
  public static final String BASE_URLS_NAME_BEGINS_WITH = "BASE_URLS";

  // OTHERS
  public static final String ENV_PORT_DEFAULT = "8000";
  public static final String PRODUCTION_ENV = "PRODUCTION";
  public static final String CONTENT_TYPE_JSON = "application/json";
  public static final String HTTPS_PROTOCOL = "https";
  public static final int HTTPS_DEFAULT_PORT = 443;
  public static final int HTTP_DEFAULT_PORT = 80;

  // UTILS
  public static final long ROUTES_REFRESH_INTERVAL = 7 * 60 * 1000; // every 7 minutes

  // PROXY
  public static final int BOSS_GROUP_THREADS = 1;
  public static final int WORKER_GROUP_THREADS = 8;
  public static final int CONNECT_TIMEOUT_MILLIS = 5000; // 5 seconds
  public static final int MAX_CONTENT_LENGTH = 1048576; // 1MB
  // CIRCUIT BREAKER
  public static final int CB_FAILURE_THRESHOLD = 3;
  public static final Duration CB_OPEN_TIMEOUT = Duration.ofSeconds(10);

  // RATE LIMITER (10 requests per second)
  public static final int RL_MAX_REQUESTS = 10;
  public static final int RL_TIME_WINDOW_MILLIS = 1;
}
