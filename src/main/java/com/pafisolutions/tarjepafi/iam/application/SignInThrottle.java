package com.pafisolutions.tarjepafi.iam.application;

import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Brute force protection of the sign-in (phase 9, QAS09): too many failed attempts lock the account's e-mail, and too
 * many from one address lock that address, for a while, whatever the password is. Real time, in memory: like the
 * simulated clock it only suits a single instance, which is what this deployment is. The e-mail is tracked whether or
 * not it exists, so the lock does not reveal which accounts do.
 */
@Component
public class SignInThrottle {

  /** Failures counted since {@code since}; locked until {@code lockedUntil} when that is in the future. */
  private record State(int failures, Instant since, Instant lockedUntil) {}

  private static final int PURGE_ABOVE = 10_000;

  private final Clock clock;
  private final int maxFailuresPerEmail;
  private final int maxFailuresPerClient;
  private final Duration window;
  private final Duration lock;
  private final Map<String, State> byEmail = new ConcurrentHashMap<>();
  private final Map<String, State> byClient = new ConcurrentHashMap<>();

  @Autowired
  public SignInThrottle(
      @Value("${tarjepafi.security.sign-in.max-failures-per-email:5}") int maxFailuresPerEmail,
      @Value("${tarjepafi.security.sign-in.max-failures-per-client:30}") int maxFailuresPerClient,
      @Value("${tarjepafi.security.sign-in.window-seconds:900}") long windowSeconds,
      @Value("${tarjepafi.security.sign-in.lock-seconds:300}") long lockSeconds) {
    this(Clock.systemUTC(), maxFailuresPerEmail, maxFailuresPerClient, windowSeconds, lockSeconds);
  }

  SignInThrottle(
      Clock clock,
      int maxFailuresPerEmail,
      int maxFailuresPerClient,
      long windowSeconds,
      long lockSeconds) {
    this.clock = clock;
    this.maxFailuresPerEmail = maxFailuresPerEmail;
    this.maxFailuresPerClient = maxFailuresPerClient;
    this.window = Duration.ofSeconds(windowSeconds);
    this.lock = Duration.ofSeconds(lockSeconds);
  }

  /**
   * @param email the normalized e-mail, or null when it is not a valid one
   * @throws BusinessRuleException 429 TOO_MANY_ATTEMPTS (with {@code retryAfterSeconds}) while locked
   */
  public void check(String email, String client) {
    Instant now = clock.instant();
    long wait = Math.max(remaining(byEmail.get(key(email)), now), remaining(byClient.get(key(client)), now));
    if (wait > 0) {
      throw new BusinessRuleException(
              Kind.TOO_MANY_REQUESTS,
              "TOO_MANY_ATTEMPTS",
              "Too many failed sign-in attempts, try again in " + wait + " seconds")
          .with("retryAfterSeconds", wait);
    }
  }

  /** A wrong password, an unknown e-mail or an invalid e-mail. */
  public void failed(String email, String client) {
    Instant now = clock.instant();
    if (email != null) {
      register(byEmail, key(email), maxFailuresPerEmail, now);
    }
    register(byClient, key(client), maxFailuresPerClient, now);
    if (byEmail.size() > PURGE_ABOVE || byClient.size() > PURGE_ABOVE) {
      purge(now);
    }
  }

  /** A successful sign-in clears the failures of that e-mail (not of the address, which may be shared). */
  public void succeeded(String email) {
    byEmail.remove(key(email));
  }

  private void register(Map<String, State> states, String key, int max, Instant now) {
    states.compute(
        key,
        (k, state) -> {
          if (state == null || now.isAfter(state.since().plus(window)) || isExpiredLock(state, now)) {
            state = new State(0, now, null);
          }
          int failures = state.failures() + 1;
          return failures >= max
              ? new State(0, now, now.plus(lock))
              : new State(failures, state.since(), state.lockedUntil());
        });
  }

  private static boolean isExpiredLock(State state, Instant now) {
    return state.lockedUntil() != null && !now.isBefore(state.lockedUntil());
  }

  private static long remaining(State state, Instant now) {
    if (state == null || state.lockedUntil() == null || !now.isBefore(state.lockedUntil())) {
      return 0;
    }
    return Math.max(1, Duration.between(now, state.lockedUntil()).toSeconds());
  }

  private void purge(Instant now) {
    byEmail.values().removeIf(state -> now.isAfter(state.since().plus(window)) && remaining(state, now) == 0);
    byClient.values().removeIf(state -> now.isAfter(state.since().plus(window)) && remaining(state, now) == 0);
  }

  private static String key(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }
}
