package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging;

import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceEventHandler;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * TS10: consumes what the readers publish. A message that cannot be parsed or handled is retried (see
 * {@code spring.rabbitmq.listener.simple.retry}) and then dead-lettered to {@code iot.device-events.queue.dlq}.
 */
@Component
public class DeviceEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(DeviceEventConsumer.class);

  private final DevicePayloadAdapter adapter;
  private final DeviceEventHandler handler;

  public DeviceEventConsumer(DevicePayloadAdapter adapter, DeviceEventHandler handler) {
    this.adapter = adapter;
    this.handler = handler;
  }

  @RabbitListener(
      queues = IotMessagingTopology.DEVICE_EVENTS_QUEUE,
      concurrency = "${tarjepafi.amqp.concurrency.device-events:4-8}")
  public void onMessage(Message message) {
    String routingKey = message.getMessageProperties().getReceivedRoutingKey();
    DeviceMessage parsed = adapter.parse(routingKey, message.getBody());
    switch (parsed) {
      case DeviceMessage.TapReceived tap -> handler.handle(tap);
      case DeviceMessage.TelemetryReceived telemetry -> handler.handle(telemetry);
      case DeviceMessage.HeartbeatReceived heartbeat -> handler.handle(heartbeat);
      case DeviceMessage.ReaderErrorReceived error -> handler.handle(error);
    }
    log.debug("Handled {} from {}", routingKey, parsed.readerId());
  }
}
