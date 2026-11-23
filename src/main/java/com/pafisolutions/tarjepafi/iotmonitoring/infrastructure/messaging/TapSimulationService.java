package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.CardTapEvent;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.CardTapEventRepository;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import com.pafisolutions.tarjepafi.shared.infrastructure.messaging.MessagingConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Plan phase 4, step 6: an administrator taps a card on a reader through the API. The tap travels the same
 * path as a real one (it is published to {@code tarjepafi.devices} as the reader would and consumed by
 * {@code DeviceEventConsumer}); the call then waits a little for the answer.
 */
@Service
public class TapSimulationService {

  /** How long the call waits for the resolution before answering with the pending event. */
  public static final Duration WAIT = Duration.ofSeconds(3);

  private static final Duration POLL = Duration.ofMillis(100);

  private final RabbitTemplate rabbit;
  private final ObjectMapper mapper;
  private final CardTapEventRepository taps;

  public TapSimulationService(RabbitTemplate rabbit, ObjectMapper mapper, CardTapEventRepository taps) {
    this.rabbit = rabbit;
    this.mapper = mapper;
    this.taps = taps;
  }

  /** The event as it is after at most {@link #WAIT}; it may still be PENDING. Null if it never arrived. */
  public CardTapEvent tap(String readerId, String rawUid) {
    String uid = CardTapEvent.normalizeUid(rawUid);
    if (!CardTapEvent.isValidUid(uid)) {
      throw new BusinessRuleException(
          Kind.INVALID, "INVALID_CARD_UID", "A card UID has 4 to 7 hexadecimal bytes, like 04:3D:9B:12:8F");
    }
    String eventId = UUID.randomUUID().toString();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("v", 1);
    payload.put("type", "CardTapped");
    payload.put("eventId", eventId);
    payload.put("readerId", readerId);
    payload.put("cardUid", uid);
    payload.put("tappedAt", Instant.now().toString());
    payload.put("buffered", false);
    payload.put("source", "SIMULATOR");
    try {
      Message message =
          MessageBuilder.withBody(mapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8))
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();
      rabbit.send(MessagingConfiguration.DEVICES_EXCHANGE, "tarjepafi.readers." + readerId + ".tap", message);
    } catch (JacksonException exception) {
      throw new IllegalStateException("The tap could not be serialized", exception);
    }
    return awaitResolution(eventId);
  }

  private CardTapEvent awaitResolution(String eventId) {
    long deadline = System.nanoTime() + WAIT.toNanos();
    CardTapEvent latest = null;
    while (System.nanoTime() < deadline) {
      latest = taps.findById(eventId).orElse(latest);
      if (latest != null && !latest.isPending()) {
        return latest;
      }
      try {
        Thread.sleep(POLL.toMillis());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return latest == null ? taps.findById(eventId).orElse(null) : latest;
  }
}
