package com.pafisolutions.tarjepafi.iotmonitoring.application;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.CardTapEvent;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.CardTapEventRepository;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Polling-friendly reads of the tap events (D13). */
@Service
@Transactional(readOnly = true)
public class TapEventQueryService {

  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 200;

  /** The events after a cursor, newest first, and the cursor to send next time. */
  public record Page(List<CardTapEvent> items, String nextCursor) {}

  private final CardTapEventRepository taps;

  public TapEventQueryService(CardTapEventRepository taps) {
    this.taps = taps;
  }

  /**
   * @param readerId only this reader, or every reader when null
   * @param after the {@code nextCursor} of the previous call; null or blank for the latest events
   * @param limit at most this many events (default 50, at most 200)
   */
  public Page list(String readerId, String after, Integer limit) {
    long cursor = parse(after);
    int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
    List<CardTapEvent> items = taps.findAfter(readerId, cursor, PageRequest.of(0, size));
    long next = items.isEmpty() ? cursor : items.get(0).getCursor();
    return new Page(items, String.valueOf(next));
  }

  public CardTapEvent get(String eventId) {
    return taps
        .findById(eventId)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    Kind.NOT_FOUND, "TAP_EVENT_NOT_FOUND", "Unknown tap event " + eventId));
  }

  private static long parse(String after) {
    if (after == null || after.isBlank()) {
      return 0;
    }
    try {
      return Math.max(0, Long.parseLong(after.trim()));
    } catch (NumberFormatException exception) {
      throw new BusinessRuleException(Kind.INVALID, "INVALID_CURSOR", "The cursor must be a number");
    }
  }
}
