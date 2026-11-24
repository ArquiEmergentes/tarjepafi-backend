package com.pafisolutions.tarjepafi.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** QAS09: the lock after repeated failed sign-ins, with a clock the test moves. */
class SignInThrottleTest {

  /** A clock that only moves when told to. */
  private static final class MovableClock extends Clock {
    private Instant now = Instant.parse("2026-11-24T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MovableClock clock = new MovableClock();
  // 3 failures lock an e-mail, 5 lock an address, failures count for 10 minutes, a lock lasts 2 minutes.
  private final SignInThrottle throttle = new SignInThrottle(clock, 3, 5, 600, 120);

  private void fail(String email, String client, int times) {
    for (int i = 0; i < times; i++) {
      throttle.check(email, client);
      throttle.failed(email, client);
    }
  }

  @Test
  void check_belowTheLimit_letsTheAttemptThrough() {
    fail("ana@upc.edu.pe", "10.0.0.1", 2);

    assertThatCode(() -> throttle.check("ana@upc.edu.pe", "10.0.0.1")).doesNotThrowAnyException();
  }

  @Test
  void check_afterTooManyFailuresOfAnEmail_locksItWithTheTimeToWait() {
    fail("ana@upc.edu.pe", "10.0.0.1", 3);

    assertThatThrownBy(() -> throttle.check("ana@upc.edu.pe", "10.0.0.2"))
        .isInstanceOfSatisfying(
            BusinessRuleException.class,
            exception -> {
              assertThat(exception.kind()).isEqualTo(Kind.TOO_MANY_REQUESTS);
              assertThat(exception.code()).isEqualTo("TOO_MANY_ATTEMPTS");
              assertThat((Long) exception.properties().get("retryAfterSeconds")).isBetween(1L, 120L);
            });
  }

  @Test
  void check_lockOfOneEmail_doesNotAffectAnotherOne() {
    fail("ana@upc.edu.pe", "10.0.0.1", 3);

    assertThatCode(() -> throttle.check("ben@upc.edu.pe", "10.0.0.2")).doesNotThrowAnyException();
  }

  @Test
  void check_emailIsComparedIgnoringCaseAndSpaces() {
    fail("ana@upc.edu.pe", "10.0.0.1", 3);

    assertThatThrownBy(() -> throttle.check("  ANA@upc.edu.pe ", "10.0.0.9")).isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void check_whenTheLockEnds_anAttemptIsAllowedAgainAndTheCountStartsOver() {
    fail("ana@upc.edu.pe", "10.0.0.1", 3);

    clock.advance(Duration.ofSeconds(121));

    assertThatCode(() -> throttle.check("ana@upc.edu.pe", "10.0.0.1")).doesNotThrowAnyException();
    fail("ana@upc.edu.pe", "10.0.0.2", 2);
    assertThatCode(() -> throttle.check("ana@upc.edu.pe", "10.0.0.3")).doesNotThrowAnyException();
  }

  @Test
  void failed_failuresOlderThanTheWindow_areForgotten() {
    fail("ana@upc.edu.pe", "10.0.0.1", 2);

    clock.advance(Duration.ofSeconds(601));
    fail("ana@upc.edu.pe", "10.0.0.1", 2);

    assertThatCode(() -> throttle.check("ana@upc.edu.pe", "10.0.0.1")).doesNotThrowAnyException();
  }

  @Test
  void succeeded_clearsTheFailuresOfTheEmail() {
    fail("ana@upc.edu.pe", "10.0.0.1", 2);
    throttle.succeeded("ana@upc.edu.pe");
    fail("ana@upc.edu.pe", "10.0.0.1", 2);

    assertThatCode(() -> throttle.check("ana@upc.edu.pe", "10.0.0.1")).doesNotThrowAnyException();
  }

  @Test
  void check_manyFailuresFromOneAddress_lockEveryEmailFromIt() {
    for (int i = 0; i < 5; i++) {
      throttle.failed("user" + i + "@upc.edu.pe", "10.0.0.7");
    }

    assertThatThrownBy(() -> throttle.check("fresh@upc.edu.pe", "10.0.0.7")).isInstanceOf(BusinessRuleException.class);
    assertThatCode(() -> throttle.check("fresh@upc.edu.pe", "10.0.0.8")).doesNotThrowAnyException();
  }

  @Test
  void failed_anInvalidEmail_countsAgainstTheAddressOnly() {
    for (int i = 0; i < 5; i++) {
      throttle.failed(null, "10.0.0.7");
    }

    assertThatThrownBy(() -> throttle.check(null, "10.0.0.7")).isInstanceOf(BusinessRuleException.class);
  }
}
