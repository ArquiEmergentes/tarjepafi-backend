package com.pafisolutions.tarjepafi.iotmonitoring.application;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.CardTapEvent;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TapOutcome;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.CardTapEventRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies the answer of the context that resolved a tap: stores the outcome and the text, and sends the
 * text to the reader. Exactly one context answers each tap, and a second answer is ignored.
 */
@Service
public class TapResolutionService {

  private static final Logger log = LoggerFactory.getLogger(TapResolutionService.class);

  private final CardTapEventRepository taps;
  private final ApplicationEventPublisher events;
  private final ObjectMapper mapper;

  public TapResolutionService(
      CardTapEventRepository taps, ApplicationEventPublisher events, ObjectMapper mapper) {
    this.taps = taps;
    this.events = events;
    this.mapper = mapper;
  }

  /**
   * @param outcome ATTENDANCE, ACCESS, WORK_SHIFT or REJECTED
   * @param tone {@code success}, {@code info} or {@code error}
   * @param messageKey a {@code tap.*} key of {@link DisplayMessages}
   */
  @Transactional
  public void apply(
      String tapEventId, String outcome, String tone, String messageKey, Map<String, Object> params) {
    CardTapEvent event = taps.findById(tapEventId).orElse(null);
    if (event == null) {
      log.warn("A resolution arrived for the unknown tap {}", tapEventId);
      return;
    }
    if (!event.isPending()) {
      log.debug("Tap {} is already {}; the resolution is ignored", tapEventId, event.getOutcome());
      return;
    }
    TapOutcome result = TapOutcome.valueOf(outcome);
    if (result == TapOutcome.PENDING) {
      throw new IllegalArgumentException("A resolution cannot leave the tap PENDING");
    }
    String text = DisplayMessages.render(messageKey, params);
    event.resolve(result, tone, messageKey, serialize(params), text);
    events.publishEvent(
        new ReaderDisplayRequested(event.getReaderId(), event.getId(), tone, DisplayMessages.lines(text)));
  }

  private String serialize(Map<String, Object> params) {
    if (params == null || params.isEmpty()) {
      return null;
    }
    try {
      return mapper.writeValueAsString(params);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("The message params are not valid JSON", exception);
    }
  }
}
