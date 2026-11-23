package com.pafisolutions.tarjepafi.academicattendance.infrastructure.messaging;

import com.pafisolutions.tarjepafi.academicattendance.application.AttendanceTapService;
import com.pafisolutions.tarjepafi.shared.application.TapContext;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.MessagingConfiguration;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.Topology;
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
 * TS13: listens to {@code iot.card-tap} and lets attendance resolve the taps that are its own (classroom
 * and meeting taps). A message that cannot be read, or fails three times, ends in the dead letter queue.
 */
@Configuration
public class CardTapEventConsumer {

  public static final String QUEUE = "attendance.card-tap.queue";

  private static final TypeReference<Map<String, Object>> DATA = new TypeReference<>() {};

  private final AttendanceTapService attendance;
  private final ObjectMapper mapper;

  public CardTapEventConsumer(AttendanceTapService attendance, ObjectMapper mapper) {
    this.attendance = attendance;
    this.mapper = mapper;
  }

  @Bean
  Declarables attendanceCardTapTopology() {
    Queue queue = Topology.queue(QUEUE);
    Queue dead = Topology.deadLetterQueue(QUEUE);
    return new Declarables(
        queue,
        dead,
        Topology.deadLetterBinding(QUEUE, dead),
        Topology.bind(queue, MessagingConfiguration.EVENTS_EXCHANGE, "iot.card-tap"));
  }

  @RabbitListener(queues = QUEUE)
  public void onMessage(Message message) {
    Map<String, Object> data = mapper.readValue(message.getBody(), DATA);
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) data.get("data");
    attendance.onCardTap(TapContext.from(payload));
  }
}
