package gateway.service.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import gateway.service.dtos.GatewayRequestDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

public class Common {

  private static final ObjectMapper OBJECT_MAPPER;
  private static final Map<String, String> propertiesMap;

  static {
    final Map<String, String> tempMap = new HashMap<>();

    final Properties systemProperties = System.getProperties();
    systemProperties.forEach(
        (key, value) -> {
          if (Constants.ENV_KEY_NAMES.contains(key.toString())) {
            tempMap.put((String) key, (String) value);
          }
        });

    final Map<String, String> envVariables = System.getenv();
    envVariables.forEach(
        (key, value) -> {
          if (Constants.ENV_KEY_NAMES.contains(key)) {
            tempMap.put(key, value);
          }
        });

    propertiesMap = Collections.unmodifiableMap(tempMap);
  }

  static {
    OBJECT_MAPPER = new ObjectMapper();
    OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public static String getSystemEnvProperty(final String key, final String defaultValue) {
    return propertiesMap.getOrDefault(key, defaultValue);
  }

  public static String getSystemEnvProperty(final String key) {
    return propertiesMap.get(key);
  }

  public static Map<String, String> getAllSystemEnvProperties() {
    return propertiesMap;
  }

  public static boolean isEmpty(final String s) {
    return s == null || s.trim().isEmpty();
  }

  public static boolean isEmpty(final Collection<?> c) {
    return (c == null || c.isEmpty());
  }

  public static boolean isEmpty(final Map<?, ?> m) {
    return (m == null || m.isEmpty());
  }

  public static int parseIntNoEx(final String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  public static boolean isProduction() {
    return Constants.PRODUCTION_ENV.equalsIgnoreCase(
        getSystemEnvProperty(Constants.SPRING_PROFILES_ACTIVE));
  }

  public static String getRequestId(final GatewayRequestDetails gatewayRequestDetails) {
    return gatewayRequestDetails == null ? "!NULL_GRD!" : gatewayRequestDetails.getRequestId().toString();
  }

  public static ObjectMapper objectMapperProvider() {
    return OBJECT_MAPPER;
  }

  public static Level transformLogLevel(final String logLevel) {
    switch (logLevel) {
      case "DEBUG" -> {
        return Level.FINE;
      }
      case "WARN" -> {
        return Level.WARNING;
      }
      case "ERROR" -> {
        return Level.SEVERE;
      }
    }
    return Level.INFO;
  }

  public static String transformLogLevel(final Level level) {
    if (level == Level.FINE) {
      return "DEBUG";
    }
    if (level == Level.WARNING) {
      return "WARN";
    }
    if (level == Level.SEVERE) {
      return "ERROR";
    }
    return level.getName();
  }
}
