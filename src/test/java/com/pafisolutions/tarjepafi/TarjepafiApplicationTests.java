package com.pafisolutions.tarjepafi;

import static org.assertj.core.api.Assertions.assertThat;

import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Starts the whole application against real PostgreSQL and RabbitMQ containers: proves that the
 * configuration, the Flyway migrations and the JPA mappings agree. Skipped when Docker is not running.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TarjepafiApplicationTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Autowired CampusClock clock;

  @Test
  void contextLoads() {
    assertThat(clock.now()).isNotNull();
  }
}
