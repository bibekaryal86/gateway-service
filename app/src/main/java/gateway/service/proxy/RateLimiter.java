package gateway.service.proxy;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimiter {
  private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

  private final Integer MAX_REQUESTS;
  private final Long TIME_WINDOW_MILLIS;

  private final AtomicLong requestCount = new AtomicLong(0);
  private final AtomicLong lastRequestTime = new AtomicLong(0);

  public RateLimiter(int maxRequests, long timeWindowMillis) {
    this.MAX_REQUESTS = maxRequests;
    this.TIME_WINDOW_MILLIS = timeWindowMillis;
  }

  public boolean allowRequest() {
    long currentTime = System.currentTimeMillis();

    if (currentTime - lastRequestTime.get() > TIME_WINDOW_MILLIS) {
      requestCount.set(0);
    }

    if (requestCount.incrementAndGet() <= MAX_REQUESTS) {
      lastRequestTime.set(currentTime);
      return true;
    } else {
      requestCount.decrementAndGet();
      return false;
    }
  }
}
