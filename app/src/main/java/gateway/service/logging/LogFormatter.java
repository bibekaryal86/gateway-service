package gateway.service.logging;

import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
  private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  private static final String TIME_ZONE = "America/Denver";
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN);

  static {
    dateFormat.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));
  }

  /**
   * Format the given log logRecord and return the formatted string.
   *
   * <p>The resulting formatted String will normally include a localized and formatted version of
   * the LogRecord's message field. It is recommended to use the {@link Formatter#formatMessage}
   * convenience method to localize and format the message field.
   *
   * @param logRecord the log logRecord to be formatted.
   * @return the formatted log logRecord
   */
  @Override
  public String format(final LogRecord logRecord) {
    String timestamp = dateFormat.format(new Date(logRecord.getMillis()));
    String threadName = Thread.currentThread().getName();
    String level = Common.transformLogLevel(logRecord.getLevel());
    String loggerName = getShortLoggerName(logRecord.getLoggerName());
    String message = formatMessage(logRecord);

    return String.format(
        "[%s][%s][%s][%s][%s]---%s%n",
        timestamp, Constants.THIS_APP_NAME, threadName, level, loggerName, message);
  }

  private String getShortLoggerName(String loggerName) {
    if (loggerName == null || !loggerName.contains(".")) {
      return loggerName;
    }
    return loggerName.substring(loggerName.lastIndexOf('.') + 1);
  }
}
