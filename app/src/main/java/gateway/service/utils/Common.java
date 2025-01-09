package gateway.service.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Map;

public class Common {

  public static boolean isEmpty(final String s) {
    return s == null || s.trim().isEmpty();
  }

  public static boolean isEmpty(final Collection<?> c) {
    return (c == null || c.isEmpty());
  }

  public static boolean isEmpty(final Map<?, ?> m) {
    return (m == null || m.isEmpty());
  }

  public static String isProduction() {
    return SystemPropertyUtils.getSystemEnvProperty(Constants.SPRING_PROFILES_ACTIVE);
  }

  public static ObjectMapper objectMapperProvider() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper;
  }
}
