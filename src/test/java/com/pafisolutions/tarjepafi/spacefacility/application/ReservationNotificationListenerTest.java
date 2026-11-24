package com.pafisolutions.tarjepafi.spacefacility.application;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.pafisolutions.tarjepafi.spacefacility.application.ReservationNotificationPort.ForfeitedNotice;
import com.pafisolutions.tarjepafi.spacefacility.application.ReservationService.ReservationForfeited;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TS23: a mail service that is down must not break the release of a reservation. */
class ReservationNotificationListenerTest {

  private static final ForfeitedNotice NOTICE =
      new ForfeitedNotice(
          "RES-2026-00001",
          "Ana Torres",
          "ana@upc.edu.pe",
          "Cubículo L01",
          LocalDateTime.parse("2026-10-07T10:00:00"),
          LocalDateTime.parse("2026-10-07T11:00:00"));

  @Test
  void onForfeited_handsTheNoticeToTheMailService() {
    List<ForfeitedNotice> sent = new ArrayList<>();
    ReservationNotificationListener listener = new ReservationNotificationListener(sent::add);

    listener.onForfeited(new ReservationForfeited(NOTICE));

    org.assertj.core.api.Assertions.assertThat(sent).containsExactly(NOTICE);
  }

  @Test
  void onForfeited_whenTheMailServiceFails_theErrorIsSwallowed() {
    ReservationNotificationListener listener =
        new ReservationNotificationListener(
            notice -> {
              throw new IllegalStateException("SMTP server down");
            });

    assertThatCode(() -> listener.onForfeited(new ReservationForfeited(NOTICE))).doesNotThrowAnyException();
  }
}
