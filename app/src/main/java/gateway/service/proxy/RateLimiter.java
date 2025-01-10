package gateway.service.proxy;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter {
  private final Integer MAX_REQUESTS;
  private final Long TIME_WINDOW_MILLIS;

  private final AtomicLong REQUEST_COUNT = new AtomicLong(0);
  private final AtomicLong LAST_REQUEST_TIME = new AtomicLong(0);

  public RateLimiter(int maxRequests, long timeWindowMillis) {
    this.MAX_REQUESTS = maxRequests;
    this.TIME_WINDOW_MILLIS = timeWindowMillis;
  }

  public boolean allowRequest() {
    long currentTime = System.currentTimeMillis();

    if (currentTime - LAST_REQUEST_TIME.get() > TIME_WINDOW_MILLIS) {
      REQUEST_COUNT.set(0);
    }

    if (REQUEST_COUNT.incrementAndGet() <= MAX_REQUESTS) {
      LAST_REQUEST_TIME.set(currentTime);
      return true;
    } else {
      REQUEST_COUNT.decrementAndGet();
      return false;
    }
  }

  @Override
  public String toString() {
    return "RateLimiter: [ "
        + "Request Count: "
        + REQUEST_COUNT
        + "Last Request Time: "
        + new Date(LAST_REQUEST_TIME.get())
        + " ]";
  }
}
