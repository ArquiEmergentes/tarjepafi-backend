package com.pafisolutions.tarjepafi.credentials.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the enrollment verification (US15) once a day after midnight, campus time. The run does nothing
 * until the deadline of the settings has passed. Disable it with
 * {@code tarjepafi.credentials.enrollment-verification.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "tarjepafi.credentials.enrollment-verification.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EnrollmentVerificationJob {

  private final EnrollmentVerificationService verification;

  public EnrollmentVerificationJob(EnrollmentVerificationService verification) {
    this.verification = verification;
  }

  @Scheduled(
      cron = "${tarjepafi.credentials.enrollment-verification.cron:0 5 0 * * *}",
      zone = "${tarjepafi.clock.zone:America/Lima}")
  void daily() {
    verification.run();
  }
}
