package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging;

import com.pafisolutions.tarjepafi.iotmonitoring.application.TapResolutionService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives {@code <context>.tap-resolved} events: {@code data = { tapEventId, outcome, tone, messageKey,
 * params }}. Whoever resolved the tap (attendance, space access, or the phase 4 fallback) answers here.
 */
@Component
public class TapResolvedConsumer {

  private static final TypeReference<Map<String, Object>> PARAMS = new TypeReference<>() {};

  private final TapResolutionService resolution;
  private final ObjectMapper mapper;

  public TapResolvedConsumer(TapResolutionService resolution, ObjectMapper mapper) {
    this.resolution = resolution;
    this.mapper = mapper;
  }

  @RabbitListener(queues = IotMessagingTopology.TAP_RESOLVED_QUEUE)
  public void onMessage(Message message) {
    JsonNode data;
    try {
      data = mapper.readTree(message.getBody()).path("data");
    } catch (JacksonException exception) {
      throw new MalformedDevicePayloadException("The resolution is not JSON", exception);
    }
    String tapEventId = text(data, "tapEventId");
    String outcome = text(data, "outcome");
    String tone = text(data, "tone");
    String messageKey = text(data, "messageKey");
    if (tapEventId == null || outcome == null || tone == null || messageKey == null) {
      throw new MalformedDevicePayloadException("The resolution misses tapEventId, outcome, tone or messageKey");
    }
    Map<String, Object> params =
        data.path("params").isObject() ? mapper.convertValue(data.path("params"), PARAMS) : new LinkedHashMap<>();
    resolution.apply(tapEventId, outcome, tone, messageKey, params);
  }

  private static String text(JsonNode data, String field) {
    JsonNode node = data.path(field);
    return node.isString() ? node.asString() : null;
  }
}
