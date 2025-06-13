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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppConfigs {
  private static final Logger logger = LoggerFactory.getLogger(AppConfigs.class);

  public static class Routes {
    private Map<String, String> routesMap = new HashMap<>();
    private List<String> authExclusions = new ArrayList<>();
    private List<String> basicAuthApps = new ArrayList<>();
    private Map<String, String> authApps = new HashMap<>();
    private List<String> proxyHeaders = new ArrayList<>();

    public Map<String, String> getRoutesMap() {
      return routesMap;
    }

    public List<String> getAuthExclusions() {
      return authExclusions;
    }

    public List<String> getBasicAuthApps() {
      return basicAuthApps;
    }

    public Map<String, String> getAuthApps() {
      return authApps;
    }

    public List<String> getProxyHeaders() {
      return proxyHeaders;
    }

    @Override
    public String toString() {
      return "Routes{"
          + "routesMap="
          + routesMap.size()
          + ", authExclusions="
          + authExclusions.size()
          + ", basicAuthApps="
          + basicAuthApps.size()
          + ", authApps="
          + authApps.size()
          + ", proxyHeaders="
          + proxyHeaders.size()
          + '}';
    }
  }

  public static class Databases {
    private String name;
    private String url;
    private String username;
    private String password;

    public Databases() {}

    public Databases(
        final String name, final String url, final String username, final String password) {
      this.name = name;
      this.url = url;
      this.username = username;
      this.password = password;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    @Override
    public String toString() {
      return "Databases{"
          + "name='"
          + name
          + '\''
          + ", url='"
          + "****"
          + '\''
          + ", username='"
          + "****"
          + '\''
          + ", password='"
          + "****"
          + '\''
          + '}';
    }
  }

  private static Timer timer;
  private static final Routes ROUTES = new Routes();
  private static Map<String, DataSource> DATABASES = new ConcurrentHashMap<>();

  public static void init() {
    logger.info("Starting AppConfigs...");
    timer = new Timer();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            refreshAppConfigs();
          }
        },
        0,
        Constants.APP_CONFIGS_REFRESH_INTERVAL);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.info("Stopping AppConfigs...");
                  if (timer != null) {
                    timer.cancel();
                    timer.purge();
                    timer = null;
                  }
                }));
  }

  public static String getTargetBaseUrl(final String apiName) {
    return ROUTES.getRoutesMap().getOrDefault(apiName, null);
  }

  public static DataSource getTargetDataSource(final String dbName) {
    return DATABASES.getOrDefault(dbName, null);
  }

  public static Routes getRoutes() {
    return ROUTES;
  }

  public static Map<String, DataSource> getDatabases() {
    return DATABASES;
  }

  private static void refreshAppConfigs() {
    logger.debug("Retrieving Env Details...");
    List<EnvDetailsResponse.EnvDetails> envDetailsList =
        AppEnvProperty.getEnvDetailsList(Constants.THIS_APP_NAME, Boolean.TRUE);
    setAuthExclusions(envDetailsList);
    setBasicAuthApis(envDetailsList);
    setRoutesMap(envDetailsList);
    setAuthApps(envDetailsList);
    setProxyHeaders(envDetailsList);
    setDatabaseConfigs(envDetailsList);
  }

  private static void setAuthExclusions(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    ROUTES.authExclusions =
        envDetailsList.stream()
            .filter(envDetail -> envDetail.getName().equals(Constants.AUTH_EXCLUSIONS_NAME))
            .findFirst()
            .orElseThrow()
            .getListValue();
    logger.debug(
        "Gateway Service App Config Auth Exclusions Size: [{}]", ROUTES.authExclusions.size());
  }

  private static void setBasicAuthApis(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    ROUTES.basicAuthApps =
        envDetailsList.stream()
            .filter(envDetail -> envDetail.getName().equals(Constants.BASIC_AUTH_NAME))
            .findFirst()
            .orElseThrow()
            .getListValue();
    logger.debug(
        "Gateway Service App Config Basic Auth Apps Size: [{}]", ROUTES.basicAuthApps.size());
  }

  private static void setRoutesMap(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    ROUTES.routesMap =
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
    logger.debug("Gateway Service App Config Routes Map Size: [{}]", ROUTES.routesMap.size());
  }

  private static void setAuthApps(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    ROUTES.authApps =
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
    logger.debug("Gateway Service App Config Auth Apps Size: [{}]", ROUTES.authApps.size());
  }

  private static void setProxyHeaders(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    ROUTES.proxyHeaders =
        envDetailsList.stream()
            .filter(envDetail -> envDetail.getName().equals(Constants.PROXY_HEADERS))
            .findFirst()
            .orElseThrow()
            .getListValue();
    logger.debug("Gateway Service App Config Proxy Headers Size: [{}]", ROUTES.proxyHeaders.size());
  }

  private static void setDatabaseConfigs(final List<EnvDetailsResponse.EnvDetails> envDetailsList) {
    DATABASES =
        envDetailsList.stream()
            .filter(envDetail -> envDetail.getName().equals(Constants.AUTH_DBS_NAME))
            .findFirst()
            .orElseThrow()
            .getMapValue()
            .entrySet()
            .stream()
            .collect(
                Collectors.groupingBy(
                    entry -> getDbName(entry.getKey()),
                    Collectors.toMap(entry -> getDbValueKey(entry.getKey()), Map.Entry::getValue)))
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, AppConfigs::createDatabaseConfig))
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, AppConfigs::createDataSource));

    logger.debug("Gateway Service App Config Databases Size: [{}]", DATABASES.size());
  }

  private static String getDbName(final String key) {
    return key.replaceAll("(_usr|_pwd|_url).*", "");
  }

  private static String getDbValueKey(final String key) {
    if (key.contains("_usr")) return "username";
    if (key.contains("_pwd")) return "password";
    if (key.contains("_url")) return "url";
    return "unknown";
  }

  private static Databases createDatabaseConfig(Map.Entry<String, Map<String, String>> entry) {
    return new Databases(
        entry.getKey(),
        entry.getValue().getOrDefault("url", "N/A"),
        entry.getValue().getOrDefault("username", "N/A"),
        decryptSecret(entry.getValue().getOrDefault("password", ""), "password"));
  }

  private static DataSource createDataSource(Map.Entry<String, Databases> entry) {
    ConnectionFactory connectionFactory =
        new DriverManagerConnectionFactory(
            entry.getValue().getUrl(),
            entry.getValue().getUsername(),
            entry.getValue().getPassword());

    PoolableConnectionFactory poolableConnectionFactory =
        new PoolableConnectionFactory(connectionFactory, null);
    GenericObjectPool<PoolableConnection> connectionPool =
        new GenericObjectPool<>(poolableConnectionFactory);
    connectionPool.setMaxTotal(Constants.DB_CONFIG_MAX_CONNECTIONS);
    connectionPool.setMinIdle(Constants.DB_CONFIG_MIN_IDLE);

    poolableConnectionFactory.setPool(connectionPool);
    return new PoolingDataSource<>(connectionPool);
  }

  private static String decryptSecret(final String encryptedData, final String keyNameForLogging) {
    if (CommonUtilities.isEmpty(encryptedData)) {
      return "N/A";
    }

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
    String secretKey = CommonUtilities.getSystemEnvProperty(Constants.SECRET_KEY);
    byte[] secretKeyBytes = Arrays.copyOf(secretKey.getBytes(), 32);
    SecretKeySpec keySpec = new SecretKeySpec(secretKeyBytes, "AES");

    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.ENCRYPT_MODE, keySpec);
    byte[] encryptedBytes = cipher.doFinal(data.getBytes());
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }
   */
}
