package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging;

import com.pafisolutions.tarjepafi.iotmonitoring.application.ReaderDisplayRequested;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.MessagingConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Sends the reply for a reader to {@code tarjepafi.devices} after the transaction that decided it has
 * committed. The routing key {@code tarjepafi.readers.<id>.display} reaches the reader as the MQTT topic
 * {@code tarjepafi/readers/<id>/display} (device contract v1: {@code {eventId, tone, lines}}).
 */
@Component
class ReaderDisplayForwarder {

  private static final Logger log = LoggerFactory.getLogger(ReaderDisplayForwarder.class);

  private final RabbitTemplate rabbit;
  private final ObjectMapper mapper;

  ReaderDisplayForwarder(RabbitTemplate rabbit, ObjectMapper mapper) {
    this.rabbit = rabbit;
    this.mapper = mapper;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void forward(ReaderDisplayRequested request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", request.eventId());
    payload.put("tone", request.tone());
    payload.put("lines", request.lines());
    try {
      Message message =
          MessageBuilder.withBody(mapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8))
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();
      rabbit.send(
          MessagingConfiguration.DEVICES_EXCHANGE,
          "tarjepafi.readers." + request.readerId() + ".display",
          message);
    } catch (AmqpException | JacksonException exception) {
      log.error("The display reply for reader {} was not sent", request.readerId(), exception);
    }
  }
}
