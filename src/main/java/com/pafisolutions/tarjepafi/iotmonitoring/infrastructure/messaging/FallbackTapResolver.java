package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging;

import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.MessagingConfiguration;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.Topology;
import java.util.Map;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * TEMPORARY (plan phase 4, step 8; removed in phase 5, step 5): until the attendance and access contexts
 * exist, every valid tap is answered with {@code tap.received}. It listens to {@code iot.card-tap} like the
 * real resolvers will and answers with {@code fallback.tap-resolved}.
 */
@Configuration
@ConditionalOnProperty(
    name = "tarjepafi.iot.fallback-resolver.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class FallbackTapResolver {

  static final String QUEUE = "iot.fallback.card-tap.queue";

  private final ApplicationEventPublisher events;
  private final ObjectMapper mapper;

  public FallbackTapResolver(ApplicationEventPublisher events, ObjectMapper mapper) {
    this.events = events;
    this.mapper = mapper;
  }

  @Bean
  Declarables fallbackResolverTopology() {
    Queue queue = Topology.queue(QUEUE);
    Queue dead = Topology.deadLetterQueue(QUEUE);
    return new Declarables(
        queue,
        dead,
        Topology.deadLetterBinding(QUEUE, dead),
        Topology.bind(queue, MessagingConfiguration.EVENTS_EXCHANGE, "iot.card-tap"));
  }

  @RabbitListener(queues = QUEUE)
  public void onCardTap(Message message) {
    String tapEventId;
    try {
      tapEventId = mapper.readTree(message.getBody()).path("data").path("tapEventId").asString();
    } catch (JacksonException exception) {
      throw new MalformedDevicePayloadException("The card tap event is not JSON", exception);
    }
    events.publishEvent(
        new IntegrationEvent(
            "fallback.tap-resolved",
            Map.of(
                "tapEventId", tapEventId,
                "outcome", "ACCESS",
                "tone", "info",
                "messageKey", "tap.received",
                "params", Map.of())));
  }
}
