package com.pafisolutions.tarjepafi.shared.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The exchanges of the messaging backbone (plan section 4). Declaring them here also makes the broker
 * image create the restricted reader user, whose topic permissions need {@code tarjepafi.devices} to
 * exist. Queues and bindings are declared by the contexts that consume (phase 4 onwards).
 */
@Configuration
public class MessagingConfiguration {

  public static final String DEVICES_EXCHANGE = "tarjepafi.devices";
  public static final String EVENTS_EXCHANGE = "tarjepafi.events";
  public static final String DEAD_LETTER_EXCHANGE = "tarjepafi.dlx";

  @Bean
  TopicExchange devicesExchange() {
    return new TopicExchange(DEVICES_EXCHANGE, true, false);
  }

  @Bean
  TopicExchange eventsExchange() {
    return new TopicExchange(EVENTS_EXCHANGE, true, false);
  }

  @Bean
  TopicExchange deadLetterExchange() {
    return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
  }
}
