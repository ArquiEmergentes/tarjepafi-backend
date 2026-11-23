package com.pafisolutions.tarjepafi.shared.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The exchanges exist after start-up and integration events reach {@code tarjepafi.events}. */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
class MessagingBackboneTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Autowired RabbitTemplate template;
  @Autowired RabbitAdmin admin;
  @Autowired ApplicationEventPublisher publisher;
  @Autowired ObjectMapper mapper;

  @Test
  void exchanges_afterStartUp_areDeclared() {
    for (String exchange : new String[] {"tarjepafi.devices", "tarjepafi.events", "tarjepafi.dlx"}) {
      Boolean declared =
          template.execute(
              channel -> {
                channel.exchangeDeclarePassive(exchange);
                return true;
              });
      assertThat(declared).as(exchange).isTrue();
    }
  }

  @Test
  void publishEvent_integrationEvent_reachesTheEventsExchangeWithTheEnvelope() throws Exception {
    Queue queue = new Queue("test.credentials-events", false, true, true);
    admin.declareQueue(queue);
    Binding binding =
        BindingBuilder.bind(queue).to(new TopicExchange("tarjepafi.events")).with("credentials.#");
    admin.declareBinding(binding);

    publisher.publishEvent(
        new IntegrationEvent("credentials.credential-status-changed", Map.of("credentialId", "cred-1")));

    Message message = template.receive(queue.getName(), 5000);
    assertThat(message).isNotNull();
    assertThat(message.getMessageProperties().getReceivedRoutingKey())
        .isEqualTo("credentials.credential-status-changed");
    JsonNode body = mapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    assertThat(body.path("type").asString()).isEqualTo("credentials.credential-status-changed");
    assertThat(body.path("eventId").asString()).isNotBlank();
    assertThat(body.path("occurredAt").asString()).startsWith("2026-10-07T");
    assertThat(body.path("data").path("credentialId").asString()).isEqualTo("cred-1");
  }
}
