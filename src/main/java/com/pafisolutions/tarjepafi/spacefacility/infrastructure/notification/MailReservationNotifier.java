package com.pafisolutions.tarjepafi.spacefacility.infrastructure.notification;

import com.pafisolutions.tarjepafi.spacefacility.application.ReservationNotificationPort;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the notice through SMTP (TS-CON07) when a {@link JavaMailSender} exists, that is when
 * {@code spring.mail.host} is configured; otherwise it logs the message, which is what the deployed demo does.
 */
@Component
class MailReservationNotifier implements ReservationNotificationPort {

  private static final Logger log = LoggerFactory.getLogger(MailReservationNotifier.class);
  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  private final ObjectProvider<JavaMailSender> sender;
  private final String from;

  MailReservationNotifier(
      ObjectProvider<JavaMailSender> sender,
      @Value("${tarjepafi.mail.from:no-reply@tarjepafi.local}") String from) {
    this.sender = sender;
    this.from = from;
  }

  @Override
  public void reservationForfeited(ForfeitedNotice notice) {
    String subject = "Tu reserva " + notice.reservationCode() + " fue liberada";
    String body =
        "Hola "
            + notice.holderName()
            + ",\n\nNo registraste tu tarjeta en "
            + notice.spaceName()
            + " dentro de los 10 minutos posteriores al inicio de tu reserva ("
            + notice.startTime().format(WHEN)
            + " - "
            + notice.endTime().format(WHEN)
            + "), por lo que el espacio fue liberado para otros estudiantes.\n\nTarjePAFI";
    JavaMailSender mail = sender.getIfAvailable();
    if (mail == null) {
      log.info("No mail server configured; e-mail to {} not sent: {}", notice.holderEmail(), subject);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(notice.holderEmail());
    message.setSubject(subject);
    message.setText(body);
    mail.send(message);
    log.info("Reservation {} release notice sent to {}", notice.reservationCode(), notice.holderEmail());
  }
}
