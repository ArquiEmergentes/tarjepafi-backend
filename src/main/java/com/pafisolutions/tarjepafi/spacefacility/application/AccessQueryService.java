package com.pafisolutions.tarjepafi.spacefacility.application;

import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.AccessRecord;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.WorkShift;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.AccessRecordRepository;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.SpaceRepository;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.WorkShiftRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Polling-friendly reads of access records and work shifts (D13), occupancy and campus presences (TS19, TS30). */
@Service
@Transactional(readOnly = true)
public class AccessQueryService {

  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 200;

  public record AccessPage(List<AccessRecord> items, String nextCursor) {}

  /** {@code tracked} is false for spaces whose occupancy is not counted (only the campus is). */
  public record Occupancy(int capacity, int currentOccupancy, boolean isFull, boolean tracked) {}

  /** A stay on campus: from a granted entry to the next granted exit. {@code status}: OPEN or CLOSED. */
  public record CampusPresence(
      LocalDateTime entryAt, LocalDateTime exitAt, Integer durationMinutes, String status) {}

  public record Presences(List<CampusPresence> items, int totalMinutes) {}

  private static final Set<String> CAMPUS_TYPES = Set.of("CAMPUS_ENTRANCE", "CAMPUS_EXIT");

  private final AccessRecordRepository accessRecords;
  private final WorkShiftRepository shifts;
  private final SpaceRepository spaces;
  private final CampusClock clock;

  public AccessQueryService(
      AccessRecordRepository accessRecords,
      WorkShiftRepository shifts,
      SpaceRepository spaces,
      CampusClock clock) {
    this.accessRecords = accessRecords;
    this.shifts = shifts;
    this.spaces = spaces;
    this.clock = clock;
  }

  /** Access records after a cursor, newest first. */
  public AccessPage accessRecords(
      String spaceId, String cardHolderId, String decision, String after, Integer limit) {
    long cursor = parseCursor(after);
    int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
    List<AccessRecord> items =
        accessRecords.findAfter(cursor, spaceId, cardHolderId, decision, PageRequest.of(0, size));
    long next = items.isEmpty() ? cursor : items.get(0).getSeq();
    return new AccessPage(items, String.valueOf(next));
  }

  public List<WorkShift> workShifts(String cardHolderId, String status, LocalDate date) {
    return shifts.search(cardHolderId, status, date);
  }

  /**
   * TS19. Only the campus is counted: the people who entered today and have not left, by their last
   * granted entry or exit. The other spaces report {@code tracked: false} and 0.
   */
  public Occupancy occupancy(String spaceId) {
    Space space =
        spaces
            .findById(spaceId)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        Kind.NOT_FOUND, "SPACE_NOT_FOUND", "Unknown space " + spaceId));
    if (!CAMPUS_TYPES.contains(space.getType())) {
      return new Occupancy(space.getCapacity(), 0, false, false);
    }
    LocalDateTime startOfDay = clock.now().toLocalDate().atStartOfDay();
    Set<String> inside = new HashSet<>();
    for (AccessRecord record : campusMovements(null, startOfDay, clock.now())) {
      if ("CAMPUS_ENTRY".equals(record.getAccessType())) {
        inside.add(record.getCardHolderId());
      } else {
        inside.remove(record.getCardHolderId());
      }
    }
    return new Occupancy(space.getCapacity(), inside.size(), inside.size() >= space.getCapacity(), true);
  }

  /** TS30: the holder's stays on campus in the range and the minutes of the closed ones. */
  public Presences presences(String cardHolderId, LocalDateTime from, LocalDateTime to) {
    if (from.isAfter(to)) {
      throw new BusinessRuleException(Kind.INVALID, "INVALID_RANGE", "from must not be after to");
    }
    List<CampusPresence> presences = new ArrayList<>();
    LocalDateTime openSince = null;
    int total = 0;
    for (AccessRecord record : campusMovements(cardHolderId, from, to)) {
      if ("CAMPUS_ENTRY".equals(record.getAccessType())) {
        if (openSince == null) {
          openSince = record.getAttemptedAt();
        }
      } else if (openSince != null) {
        int minutes = (int) java.time.Duration.between(openSince, record.getAttemptedAt()).toMinutes();
        presences.add(new CampusPresence(openSince, record.getAttemptedAt(), minutes, "CLOSED"));
        total += minutes;
        openSince = null;
      }
    }
    if (openSince != null) {
      presences.add(new CampusPresence(openSince, null, null, "OPEN"));
    }
    return new Presences(presences, total);
  }

  /** Granted entries and exits of the campus in time order. */
  private List<AccessRecord> campusMovements(
      String cardHolderId, LocalDateTime from, LocalDateTime to) {
    return accessRecords.findCampusMovements(cardHolderId, from, to).stream()
        .sorted(Comparator.comparing(AccessRecord::getAttemptedAt).thenComparing(AccessRecord::getSeq))
        .toList();
  }

  private static long parseCursor(String after) {
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
