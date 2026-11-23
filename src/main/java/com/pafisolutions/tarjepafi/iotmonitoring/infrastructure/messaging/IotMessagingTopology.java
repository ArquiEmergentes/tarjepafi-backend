package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging;

import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.MessagingConfiguration;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.Topology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queues of IoT Monitoring: what readers publish ({@code iot.device-events.queue}) and the answers of the
 * contexts that resolve taps ({@code iot.tap-resolved.queue}), each with its dead letter queue.
 */
@Configuration
public class IotMessagingTopology {

  public static final String DEVICE_EVENTS_QUEUE = "iot.device-events.queue";
  public static final String TAP_RESOLVED_QUEUE = "iot.tap-resolved.queue";

  /** Routing keys of the device contract (v1) under the MQTT exchange. */
  private static final String[] DEVICE_KEYS = {
    "tarjepafi.readers.*.tap",
    "tarjepafi.readers.*.heartbeat",
    "tarjepafi.readers.*.telemetry",
    "tarjepafi.readers.*.error"
  };

  @Bean
  Declarables deviceEventsTopology() {
    Queue queue = Topology.queue(DEVICE_EVENTS_QUEUE);
    Queue dead = Topology.deadLetterQueue(DEVICE_EVENTS_QUEUE);
    Declarables declarables = new Declarables(queue, dead, Topology.deadLetterBinding(DEVICE_EVENTS_QUEUE, dead));
    for (String key : DEVICE_KEYS) {
      declarables.getDeclarables().add(Topology.bind(queue, MessagingConfiguration.DEVICES_EXCHANGE, key));
    }
    return declarables;
  }

  @Bean
  Declarables tapResolvedTopology() {
    Queue queue = Topology.queue(TAP_RESOLVED_QUEUE);
    Queue dead = Topology.deadLetterQueue(TAP_RESOLVED_QUEUE);
    Binding binding = Topology.bind(queue, MessagingConfiguration.EVENTS_EXCHANGE, "*.tap-resolved");
    return new Declarables(queue, dead, Topology.deadLetterBinding(TAP_RESOLVED_QUEUE, dead), binding);
  }
}
