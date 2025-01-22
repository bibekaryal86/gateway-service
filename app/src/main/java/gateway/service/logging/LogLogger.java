package gateway.service.logging;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LogLogger {
  private final Logger logger;
  private static final AtomicReference<Level> CURRENT_LOG_LEVEL = new AtomicReference<>(Level.INFO);

  static {
    configureGlobalLogging(CURRENT_LOG_LEVEL.get());
  }

  private LogLogger(final Class<?> clazz) {
    this.logger = Logger.getLogger(clazz.getName());
  }

  public static LogLogger getLogger(final Class<?> clazz) {
    return new LogLogger(clazz);
  }

  public static Level getCurrentLogLevel() {
    return CURRENT_LOG_LEVEL.get();
  }

  public void debug(final String message, final Object... params) {
    if (isDebugEnabled()) {
      log(Level.FINE, formatMessage(message, params), null);
    }
  }

  public void info(final String message, final Object... params) {
    if (isInfoEnabled()) {
      log(Level.INFO, formatMessage(message, params), null);
    }
  }

  public void warn(final String message, final Object... params) {
    if (isWarnEnabled()) {
      log(Level.WARNING, formatMessage(message, params), null);
    }
  }

  public void error(final String message, final Object... params) {
    // error is always enabled
    log(Level.SEVERE, formatMessage(message, params), null);
  }

  public void error(final String message, final Throwable throwable, final Object... params) {
    // error is always enabled
    log(Level.SEVERE, formatMessage(message, params), throwable);
  }

  private void log(final Level level, final String message, final Throwable throwable) {
    logger.log(level, message);
    if (throwable != null) {
      logger.log(level, logThrowable(throwable));
    }
  }

  private String formatMessage(final String message, final Object... params) {
    if (params == null || params.length == 0) {
      return message;
    }

    final StringBuilder formattedMessage = new StringBuilder(message);
    for (final Object param : params) {
      int placeholderIndex = formattedMessage.indexOf("{}");
      if (placeholderIndex != -1) {
        formattedMessage.replace(placeholderIndex, placeholderIndex + 2, toString(param));
      }
    }
    return formattedMessage.toString();
  }

  private String toString(final Object obj) {
    return obj == null ? "!!null!!" : obj.toString();
  }

  public static void configureGlobalLogging(final Level level) {
    CURRENT_LOG_LEVEL.set(level);

    final Logger rootLogger = Logger.getLogger("");
    rootLogger.setLevel(level);

    for (final Handler handler : rootLogger.getHandlers()) {
      rootLogger.removeHandler(handler);
    }

    final LogHandler asyncLogHandler = new LogHandler();
    asyncLogHandler.setFormatter(new LogFormatter());
    asyncLogHandler.setLevel(level);

    rootLogger.addHandler(asyncLogHandler);
    rootLogger.setUseParentHandlers(false);
  }

  //  private static boolean isErrorEnabled() {
  //    return isWarnEnabled() || CURRENT_LOG_LEVEL.get() == Level.SEVERE;
  //  }

  private static boolean isWarnEnabled() {
    return isInfoEnabled() || CURRENT_LOG_LEVEL.get() == Level.WARNING;
  }

  private static boolean isInfoEnabled() {
    return isDebugEnabled() || CURRENT_LOG_LEVEL.get() == Level.INFO;
  }

  private static boolean isDebugEnabled() {
    return CURRENT_LOG_LEVEL.get() == Level.FINE;
  }

  private static String logThrowable(final Throwable exception) {
    StringBuilder buf = new StringBuilder();
    for (StackTraceElement element : exception.getStackTrace()) {
      buf.append(element).append("\n");
    }
    return buf.toString();
  }
}
