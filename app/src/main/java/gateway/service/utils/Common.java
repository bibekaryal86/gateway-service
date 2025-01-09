package gateway.service.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Common {

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

  public static boolean isProduction() {
    return Constants.PRODUCTION_ENV.equalsIgnoreCase(
        getSystemEnvProperty(Constants.SPRING_PROFILES_ACTIVE));
  }

  public static ObjectMapper objectMapperProvider() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper;
  }
}
