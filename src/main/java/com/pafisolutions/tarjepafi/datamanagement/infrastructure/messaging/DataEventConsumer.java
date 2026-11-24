package com.pafisolutions.tarjepafi.datamanagement.infrastructure.messaging;

import com.pafisolutions.tarjepafi.datamanagement.application.DataIngestionService;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.MessagingConfiguration;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.Topology;
import java.time.LocalDateTime;
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
 * TS24: receives the events of IoT Monitoring, Academic Attendance, Space and Facility and Credentials and
 * records them as data. A message that fails three times ends in the dead letter queue.
 */
@Configuration
public class DataEventConsumer {

  public static final String QUEUE = "data.events.queue";

  private static final TypeReference<Map<String, Object>> MESSAGE = new TypeReference<>() {};

  private final DataIngestionService ingestion;
  private final ObjectMapper mapper;

  public DataEventConsumer(DataIngestionService ingestion, ObjectMapper mapper) {
    this.ingestion = ingestion;
    this.mapper = mapper;
  }

  @Bean
  Declarables dataEventsTopology() {
    Queue queue = Topology.queue(QUEUE);
    Queue dead = Topology.deadLetterQueue(QUEUE);
    return new Declarables(
        queue,
        dead,
        Topology.deadLetterBinding(QUEUE, dead),
        Topology.bind(queue, MessagingConfiguration.EVENTS_EXCHANGE, "iot.#"),
        Topology.bind(queue, MessagingConfiguration.EVENTS_EXCHANGE, "attendance.#"),
        Topology.bind(queue, MessagingConfiguration.EVENTS_EXCHANGE, "space.#"),
        Topology.bind(queue, MessagingConfiguration.EVENTS_EXCHANGE, "credentials.#"));
  }

  @RabbitListener(queues = QUEUE)
  @SuppressWarnings("unchecked")
  public void onMessage(Message message) {
    Map<String, Object> envelope = mapper.readValue(message.getBody(), MESSAGE);
    String occurredAt = String.valueOf(envelope.get("occurredAt"));
    ingestion.ingest(
        String.valueOf(envelope.get("eventId")),
        String.valueOf(envelope.get("type")),
        LocalDateTime.parse(occurredAt),
        (Map<String, Object>) envelope.get("data"));
  }
}
