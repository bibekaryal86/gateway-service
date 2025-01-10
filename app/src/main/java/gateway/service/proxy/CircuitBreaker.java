package gateway.service.proxy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircuitBreaker {
  private enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

  private CircuitBreakerState state = CircuitBreakerState.CLOSED;
  private final AtomicInteger FAILURE_COUNT = new AtomicInteger(0);
  private final AtomicLong LAST_FAILURE_TIME = new AtomicLong(0);

  private final String API_NAME;
  private final Integer FAILURE_THRESHOLD;
  private final Duration OPEN_TIMEOUT;

  public CircuitBreaker(
      final String apiName, final int failureThreshold, final Duration openTimeout) {
    this.API_NAME = apiName;
    this.FAILURE_THRESHOLD = failureThreshold;
    this.OPEN_TIMEOUT = openTimeout;
  }

  public boolean allowRequest() {
    return switch (state) {
      case CLOSED -> true;
      case OPEN -> {
        if ((System.currentTimeMillis() - LAST_FAILURE_TIME.get()) >= OPEN_TIMEOUT.toMillis()) {
          state = CircuitBreakerState.HALF_OPEN;
          FAILURE_COUNT.set(0);
        }
        yield false;
      }
      case HALF_OPEN -> {
        if (FAILURE_COUNT.incrementAndGet() >= FAILURE_THRESHOLD) {
          state = CircuitBreakerState.OPEN;
          LAST_FAILURE_TIME.set(System.currentTimeMillis());
        }
        yield true;
      }
    };
  }

  public void markSuccess() {
    if (state == CircuitBreakerState.HALF_OPEN) {
      FAILURE_COUNT.set(0);
      state = CircuitBreakerState.CLOSED;
    }
  }

  public void markFailure() {
    FAILURE_COUNT.incrementAndGet();
    LAST_FAILURE_TIME.set(System.currentTimeMillis());

    if (FAILURE_COUNT.get() >= FAILURE_THRESHOLD) {
      log.error("Transitioning to OPEN state: [{}]", API_NAME);
      state = CircuitBreakerState.OPEN;
    }
  }
}
