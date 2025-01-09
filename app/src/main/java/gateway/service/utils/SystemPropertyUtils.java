package gateway.service.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SystemPropertyUtils {

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
}
