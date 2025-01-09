package gateway.service.utils;

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
  public static final String PRODUCTION_ENV = "PRODUCTION";
}
