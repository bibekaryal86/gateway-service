/*
 * This source file was generated by the Gradle 'init' task
 */
package gateway.service;

import gateway.service.proxy.NettyServer;
import gateway.service.utils.Constants;
import gateway.service.utils.Routes;
import io.github.bibekaryal86.shdsvc.helpers.CommonUtilities;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
  private static final Logger logger = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) throws Exception {
    logger.info("Starting Gateway App...");
    validateInitArgs();
    Routes.refreshRoutes();
    new NettyServer().start();
    logger.info("Started Gateway App...");
  }

  private static void validateInitArgs() {
    final Map<String, String> properties =
        CommonUtilities.getSystemEnvProperties(Constants.ENV_KEY_NAMES);
    final List<String> requiredEnvProperties =
        Constants.ENV_KEY_NAMES.stream().filter(key -> !Constants.ENV_PORT.equals(key)).toList();
    final List<String> errors =
        requiredEnvProperties.stream().filter(key -> properties.get(key) == null).toList();
    if (!errors.isEmpty()) {
      throw new IllegalStateException(
          "One or more environment configurations could not be accessed...");
    }
  }
}
