package gateway.service.logging;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class LogHandler extends StreamHandler {
  private final ConcurrentLinkedQueue<LogRecord> logQueue = new ConcurrentLinkedQueue<>();
  private final ExecutorService executorService;
  private final AtomicBoolean isProcessing = new AtomicBoolean(false);

  public LogHandler() {
    executorService = Executors.newSingleThreadExecutor();
  }

  @Override
  public void publish(LogRecord record) {
    if (isLoggable(record)) {
      logQueue.offer(record);
      try {
        triggerProcessing();
      } catch (RejectedExecutionException ex) {
        System.err.println("LogHandler publish Error: " + ex.getMessage());
      }
    }
  }

  @Override
  public void flush() {
    // No-op since logs are processed asynchronously
  }

  @Override
  public void close() throws SecurityException {
    try {
      super.close();
      flushRemainingLogs();
      executorService.shutdown();
      if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("LogHandler close Error: " + e.getMessage());
    } catch (Exception e) {
      System.err.println("LogHandler close Error: " + e.getMessage());
    }
  }

  private void processQueue() {
    try {
      LogRecord record;
      while ((record = logQueue.poll()) != null) {
        try {
          System.out.write(getFormatter().format(record).getBytes());
        } catch (Exception ex) {
          System.err.println("LogHandler processQueue Error: " + ex.getMessage());
        }
      }
    } finally {
      if (isProcessing.compareAndSet(true, false) && !logQueue.isEmpty()) {
        triggerProcessing();
      }
    }
  }

  private void triggerProcessing() {
    if (isProcessing.compareAndSet(false, true)) {
      executorService.submit(this::processQueue);
    }
  }

  private void flushRemainingLogs() {
    LogRecord record;
    while ((record = logQueue.poll()) != null) {
      try {
        System.out.write(getFormatter().format(record).getBytes());
      } catch (Exception ex) {
        System.err.println("LogHandler flushRemainingLogs Error: " + ex.getMessage());
      }
    }
  }
}
