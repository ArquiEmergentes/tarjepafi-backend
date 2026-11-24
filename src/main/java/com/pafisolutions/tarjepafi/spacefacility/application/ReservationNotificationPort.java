package com.pafisolutions.tarjepafi.spacefacility.application;

import java.time.LocalDateTime;

/**
 * The e-mail service of TS-CON07, as Space &amp; Facility sees it. The adapter sends through SMTP when
 * {@code spring.mail.host} is set and only logs the message otherwise.
 */
public interface ReservationNotificationPort {

  /** What the holder is told when their reservation was released. */
  record ForfeitedNotice(
      String reservationCode,
      String holderName,
      String holderEmail,
      String spaceName,
      LocalDateTime startTime,
      LocalDateTime endTime) {}

  /** @throws RuntimeException when the message could not be handed to the mail service */
  void reservationForfeited(ForfeitedNotice notice);
}
