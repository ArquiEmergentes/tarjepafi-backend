package com.pafisolutions.tarjepafi.shared.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Helpers to declare a queue the way the plan's section 4 wants it: durable, with its own dead letter
 * queue {@code <queue>.dlq} on {@code tarjepafi.dlx}, so a message that keeps failing is parked there.
 */
public final class Topology {

  private Topology() {}

  /** A durable queue that dead-letters to {@code tarjepafi.dlx} with its own name as routing key. */
  public static Queue queue(String name) {
    return QueueBuilder.durable(name)
        .deadLetterExchange(MessagingConfiguration.DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(name)
        .build();
  }

  public static Queue deadLetterQueue(String name) {
    return QueueBuilder.durable(name + ".dlq").build();
  }

  public static Binding deadLetterBinding(String name, Queue deadLetterQueue) {
    return BindingBuilder.bind(deadLetterQueue)
        .to(new TopicExchange(MessagingConfiguration.DEAD_LETTER_EXCHANGE, true, false))
        .with(name);
  }

  public static Binding bind(Queue queue, String exchange, String routingKey) {
    return BindingBuilder.bind(queue).to(new TopicExchange(exchange, true, false)).with(routingKey);
  }
}
