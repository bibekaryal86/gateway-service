package gateway.service.proxy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CircuitBreaker {
  private enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private CircuitBreakerState state = CircuitBreakerState.CLOSED;
  private final AtomicInteger FAILURE_COUNT = new AtomicInteger(0);
  private final AtomicLong LAST_FAILURE_TIME = new AtomicLong(0);

  private final Integer FAILURE_THRESHOLD;
  private final Duration OPEN_TIMEOUT;

  public CircuitBreaker(final int failureThreshold, final Duration openTimeout) {
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
      state = CircuitBreakerState.OPEN;
    }
  }

  @Override
  public String toString() {
    return "CircuitBreaker: [ "
        + "State: "
        + state
        + ", Failure Count: "
        + FAILURE_COUNT
        + ", Open Timeout: "
        + OPEN_TIMEOUT
        + " ]";
  }
}
