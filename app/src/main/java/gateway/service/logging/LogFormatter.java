package gateway.service.logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
    private static final String THIS_APP_NAME = "gatewaysvc";
    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final String TIME_ZONE = "America/Denver";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN);

    static {
        dateFormat.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));
    }

    /**
     * Format the given log record and return the formatted string.
     * <p>
     * The resulting formatted String will normally include a
     * localized and formatted version of the LogRecord's message field.
     * It is recommended to use the {@link Formatter#formatMessage}
     * convenience method to localize and format the message field.
     *
     * @param record the log record to be formatted.
     * @return the formatted log record
     */
    @Override
    public String format(LogRecord record) {
        String timestamp = dateFormat.format(new Date(record.getMillis()));
        String threadName = Thread.currentThread().getName();
        String level = record.getLevel().getName();
        String loggerName = getShortLoggerName(record.getLoggerName());
        String message = formatMessage(record);

        return String.format(
                "[%s][%s][%s][%s][%s]---%s%n",
                timestamp,
                THIS_APP_NAME,
                threadName,
                level,
                loggerName,
                message
        );
    }

    private String getShortLoggerName(String loggerName) {
        if (loggerName == null || !loggerName.contains(".")) {
            return loggerName;
        }
        return loggerName.substring(loggerName.lastIndexOf('.') + 1);
    }
}
