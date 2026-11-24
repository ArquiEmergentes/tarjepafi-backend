package com.pafisolutions.tarjepafi.spacefacility.application;

import com.pafisolutions.tarjepafi.spacefacility.application.ReservationService.ReservationForfeited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * TS23: tells the holder their reservation was released, asynchronously and only once the sweep's transaction
 * committed. A mail service that is down does not undo the forfeit: the error is logged and nothing else happens.
 */
@Component
class ReservationNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(ReservationNotificationListener.class);

  private final ReservationNotificationPort notifications;

  ReservationNotificationListener(ReservationNotificationPort notifications) {
    this.notifications = notifications;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void onForfeited(ReservationForfeited event) {
    try {
      notifications.reservationForfeited(event.notice());
    } catch (RuntimeException exception) {
      log.error(
          "The release notice of reservation {} could not be sent to {}",
          event.notice().reservationCode(),
          event.notice().holderEmail(),
          exception);
    }
  }
}
