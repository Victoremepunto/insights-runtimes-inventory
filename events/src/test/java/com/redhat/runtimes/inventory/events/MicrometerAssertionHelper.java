/* Copyright (C) Red Hat 2023 */
package com.redhat.runtimes.inventory.events;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link MeterRegistry#clear()} cannot be called between tests to reset counters because it would
 * remove the counters instances from the registry while we use many {@link ApplicationScoped} beans
 * which hold references pointing at the aforementioned counters instances. That's why we need this
 * helper to assert increments in a reliable way.
 *
 * <p>This class is based on MicrometerAssertionHelper from RedHatInsights/notifications-backend:
 * https://github.com/RedHatInsights/notifications-backend/blob/master/common/src/test/java/com/redhat/cloud/notifications/MicrometerAssertionHelper.java
 */
@ApplicationScoped
public class MicrometerAssertionHelper {

  @Inject MeterRegistry registry;

  private final Map<String, Double> counterValuesBeforeTest = new ConcurrentHashMap<>();

  public void saveCounterValuesBeforeTest(String... counterNames) {
    for (String counterName : counterNames) {
      counterValuesBeforeTest.put(counterName, registry.counter(counterName).count());
    }
  }

  public void assertCounterIncrement(String counterName, double expectedIncrement) {
    double actualIncrement =
        registry.counter(counterName).count()
            - counterValuesBeforeTest.getOrDefault(counterName, 0D);
    assertEquals(expectedIncrement, actualIncrement);
  }

  public void assertNoCounterIncrement(String... counterNames) {
    for (String counterName : counterNames) {
      assertCounterIncrement(counterName, 0);
    }
  }

  public void awaitAndAssertCounterIncrement(String counterName, double expectedIncrement) {
    await()
        .atMost(Duration.ofSeconds(30L))
        .until(
            () -> {
              double actualIncrement =
                  registry.counter(counterName).count()
                      - counterValuesBeforeTest.getOrDefault(counterName, 0D);
              return expectedIncrement == actualIncrement;
            });
  }

  public void awaitAndAssertTimerIncrement(String timerName, long expectedIncrement) {
    await()
        .atMost(Duration.ofSeconds(30L))
        .until(
            () -> {
              Timer timer = findTimerByNameOnly(timerName);
              if (timer == null) {
                // The timer may be created after this method is executed.
                return false;
              } else {
                long actualIncrement = Optional.ofNullable(timer.count()).orElse(0L);
                return expectedIncrement == actualIncrement;
              }
            });
  }

  public void clearSavedValues() {
    counterValuesBeforeTest.clear();
  }

  public void removeDynamicTimer(String timerName) {
    for (Timer timer : findTimersByNameOnly(timerName)) {
      registry.remove(timer);
    }
  }

  /**
   * Finds a timer from its name only, tags are ignored. If multiple timers match the name, the
   * first one will be returned.
   */
  private Timer findTimerByNameOnly(String name) {
    return registry.find(name).timer();
  }

  /** Finds a collection of timers from their name only, tags are ignored. */
  private Collection<Timer> findTimersByNameOnly(String name) {
    return registry.find(name).timers();
  }
}
