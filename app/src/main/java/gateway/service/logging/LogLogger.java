package gateway.service.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogLogger {
    private final Logger logger;

    static {
        configureGlobalLogging();
    }

    private LogLogger(Class<?> clazz) {
        this.logger = Logger.getLogger(clazz.getName());
    }

    public static LogLogger getLogger(Class<?> clazz) {
        return new LogLogger(clazz);
    }

    public void debug(String message, Object... params) {
        log(Level.FINE, formatMessage(message, params), null);
    }

    public void info(String message, Object... params) {
        log(Level.INFO, formatMessage(message, params), null);
    }

    public void warn(String message, Object... params) {
        log(Level.WARNING, formatMessage(message, params), null);
    }

    public void error(String message, Object... params) {
        log(Level.SEVERE, formatMessage(message, params), null);
    }

    public void error(String message, Throwable throwable, Object... params) {
        log(Level.SEVERE, formatMessage(message, params), throwable);
    }

    private void log(Level level, String message, Throwable throwable) {
        logger.log(level, message);
        if (throwable != null) {
            logger.log(level, "Exception: ", throwable);
        }
    }

    private String formatMessage(String message, Object... params) {
        if (params == null || params.length == 0) {
            return message;
        }

        StringBuilder formattedMessage = new StringBuilder(message);
        for (Object param : params) {
            int placeholderIndex = formattedMessage.indexOf("{}");
            if (placeholderIndex != -1) {
                formattedMessage.replace(placeholderIndex, placeholderIndex + 2, toString(param));
            }
        }
        return formattedMessage.toString();
    }

    private String toString(Object obj) {
        return obj == null ? "!!null!!" : obj.toString();
    }

    private static void configureGlobalLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.ALL);
        LogHandler asyncLogHandler = new LogHandler();
        asyncLogHandler.setFormatter(new LogFormatter());
        asyncLogHandler.setLevel(Level.ALL);
        rootLogger.addHandler(asyncLogHandler);
        rootLogger.setUseParentHandlers(false);
    }
}

