package com.pafisolutions.tarjepafi.spacefacility.infrastructure.messaging;

import com.pafisolutions.tarjepafi.shared.application.TapContext;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.MessagingConfiguration;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.Topology;
import com.pafisolutions.tarjepafi.spacefacility.application.AccessControlService;
import java.util.Map;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * TS22: listens to {@code iot.card-tap} (taps the space context resolves) and {@code iot.tap-rejected}
 * (denied attempts at readers that are not in a classroom). A message that fails three times ends in the
 * dead letter queue.
 */
@Configuration
public class SpaceCardTapEventConsumer {

  public static final String QUEUE = "space.card-tap.queue";

  private static final TypeReference<Map<String, Object>> MESSAGE = new TypeReference<>() {};

  private final AccessControlService access;
  private final ObjectMapper mapper;

  public SpaceCardTapEventConsumer(AccessControlService access, ObjectMapper mapper) {
    this.access = access;
    this.mapper = mapper;
  }

  @Bean
  Declarables spaceCardTapTopology() {
    Queue queue = Topology.queue(QUEUE);
    Queue dead = Topology.deadLetterQueue(QUEUE);
    return new Declarables(
        queue,
        dead,
        Topology.deadLetterBinding(QUEUE, dead),
        Topology.bind(queue, MessagingConfiguration.EVENTS_EXCHANGE, "iot.card-tap"),
        Topology.bind(queue, MessagingConfiguration.EVENTS_EXCHANGE, "iot.tap-rejected"));
  }

  @RabbitListener(queues = QUEUE, concurrency = "${tarjepafi.amqp.concurrency.space-card-tap:2-4}")
  @SuppressWarnings("unchecked")
  public void onMessage(Message message) {
    Map<String, Object> envelope = mapper.readValue(message.getBody(), MESSAGE);
    Map<String, Object> data = (Map<String, Object>) envelope.get("data");
    if ("iot.tap-rejected".equals(envelope.get("type"))) {
      access.onTapRejected(data);
    } else {
      access.onCardTap(TapContext.from(data));
    }
  }
}
