package com.pafisolutions.tarjepafi.shared.infrastructure.messaging;

import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Sends {@link IntegrationEvent}s to {@code tarjepafi.events} after the publishing transaction has
 * committed, so a rolled-back change never leaves an event behind. A broker outage is logged and does
 * not undo the change that was already committed.
 *
 * <p>Envelope: {@code {"eventId", "type", "occurredAt", "data"}}.
 */
@Component
class IntegrationEventForwarder {

  private static final Logger log = LoggerFactory.getLogger(IntegrationEventForwarder.class);

  private final RabbitTemplate rabbit;
  private final ObjectMapper mapper;
  private final CampusClock clock;

  IntegrationEventForwarder(RabbitTemplate rabbit, ObjectMapper mapper, CampusClock clock) {
    this.rabbit = rabbit;
    this.mapper = mapper;
    this.clock = clock;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void forward(IntegrationEvent event) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("eventId", UUID.randomUUID().toString());
    envelope.put("type", event.routingKey());
    envelope.put("occurredAt", clock.now().toString());
    envelope.put("data", event.data());
    try {
      Message message =
          MessageBuilder.withBody(mapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8))
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setContentEncoding(StandardCharsets.UTF_8.name())
              .build();
      rabbit.send(MessagingConfiguration.EVENTS_EXCHANGE, event.routingKey(), message);
    } catch (AmqpException | tools.jackson.core.JacksonException exception) {
      log.error("Integration event {} was not published", event.routingKey(), exception);
    }
  }
}
