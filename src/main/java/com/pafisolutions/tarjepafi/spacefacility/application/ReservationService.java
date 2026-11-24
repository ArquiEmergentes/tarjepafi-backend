package com.pafisolutions.tarjepafi.spacefacility.application;

import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort;
import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort.CardLookup;
import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort.HolderInfo;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import com.pafisolutions.tarjepafi.spacefacility.application.ReservationNotificationPort.ForfeitedNotice;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.CheckInResult;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.ReservationRepository;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.SpaceRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TS20 to TS23: administrators reserve and cancel, a tap of the holder at the space checks the reservation in, and
 * a sweep forfeits the ones nobody claimed within 10 minutes and completes the ones that were used.
 */
@Service
public class ReservationService {

  /** The policy of a space that can only be entered with a reservation (report: RESERVATION_REQUIRED). */
  public static final String RESERVATION_REQUIRED = "RESERVATION_REQUIRED";

  public static final String OCCUPIED = "OCCUPIED";

  private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

  private final ReservationRepository reservations;
  private final SpaceRepository spaces;
  private final CardHolderDirectoryPort directory;
  private final CampusClock clock;
  private final ApplicationEventPublisher events;

  public ReservationService(
      ReservationRepository reservations,
      SpaceRepository spaces,
      CardHolderDirectoryPort directory,
      CampusClock clock,
      ApplicationEventPublisher events) {
    this.reservations = reservations;
    this.spaces = spaces;
    this.directory = directory;
    this.clock = clock;
    this.events = events;
  }

  /** What a tap at a reservation space did, for the access record and the reader's reply. */
  public enum TapOutcome {
    CHECKED_IN,
    ALREADY_ACTIVE,
    NOT_STARTED,
    EXPIRED,
    NO_RESERVATION
  }

  public record TapDecision(TapOutcome outcome, Reservation reservation) {}

  /** The facts the e-mail needs, sent to the notifier after the transaction commits. */
  public record ReservationForfeited(ForfeitedNotice notice) {}

  // ---------- TS20 ----------

  /**
   * Reserves the space for {@code cardHolderId}. 404 SPACE_NOT_FOUND; 400 INVALID_TIME_SLOT; 422
   * SPACE_NOT_RESERVABLE, HOLDER_NOT_FOUND, HOLDER_WITHOUT_ACTIVE_CREDENTIAL, ROLE_NOT_ALLOWED, SLOT_NOT_IN_FUTURE;
   * 409 RESERVATION_OVERLAP.
   */
  @Transactional
  public Reservation create(
      String spaceId, String cardHolderId, LocalDateTime startTime, LocalDateTime endTime) {
    if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
      throw new BusinessRuleException(
          Kind.INVALID, "INVALID_TIME_SLOT", "startTime and endTime are required and the end must be after the start");
    }
    Space space = findSpace(spaceId);
    if (!RESERVATION_REQUIRED.equals(space.getAccessPolicy()) || space.isInMaintenance()) {
      throw new BusinessRuleException(
          Kind.UNPROCESSABLE,
          "SPACE_NOT_RESERVABLE",
          "The space does not take reservations: its policy must be RESERVATION_REQUIRED and it cannot be in maintenance");
    }
    HolderInfo holder =
        directory
            .findHolder(cardHolderId)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        Kind.UNPROCESSABLE, "HOLDER_NOT_FOUND", "Unknown card holder " + cardHolderId));
    Optional<CardLookup> card = directory.findCardOf(cardHolderId);
    if (card.isEmpty() || !card.get().active()) {
      throw new BusinessRuleException(
          Kind.UNPROCESSABLE,
          "HOLDER_WITHOUT_ACTIVE_CREDENTIAL",
          "The holder has no active credential");
    }
    if (!space.getAllowedRoles().contains(holder.role())) {
      throw new BusinessRuleException(
          Kind.UNPROCESSABLE, "ROLE_NOT_ALLOWED", "The space does not admit the role " + holder.role());
    }
    LocalDateTime now = clock.now();
    if (!startTime.isAfter(now)) {
      throw new BusinessRuleException(
          Kind.UNPROCESSABLE, "SLOT_NOT_IN_FUTURE", "The slot must start after " + now);
    }
    if (reservations.existsOverlapping(spaceId, startTime, endTime)) {
      throw new BusinessRuleException(
          Kind.CONFLICT, "RESERVATION_OVERLAP", "The space is already reserved in that slot");
    }
    Reservation reservation =
        reservations.save(
            new Reservation(
                "res-" + UUID.randomUUID(),
                newCode(startTime),
                spaceId,
                cardHolderId,
                startTime,
                endTime,
                now));
    publish("space.reservation-created", reservation, Map.of());
    return reservation;
  }

  /** Cancels a CONFIRMED reservation. 404 RESERVATION_NOT_FOUND, 409 INVALID_RESERVATION_STATE. */
  @Transactional
  public Reservation cancel(String reservationId) {
    Reservation reservation = find(reservationId);
    if (reservation.getStatus() != Reservation.Status.CONFIRMED) {
      throw new BusinessRuleException(
          Kind.CONFLICT,
          "INVALID_RESERVATION_STATE",
          "Only a CONFIRMED reservation can be cancelled, this one is " + reservation.getStatus());
    }
    reservation.cancel();
    publish("space.reservation-cancelled", reservations.save(reservation), Map.of());
    return reservation;
  }

  // ---------- TS21 ----------

  @Transactional(readOnly = true)
  public Reservation get(String reservationId) {
    return find(reservationId);
  }

  /** The reservations in progress now (CONFIRMED or CHECKED_IN, slot containing the campus time). */
  @Transactional(readOnly = true)
  public List<Reservation> active() {
    return reservations.findActive(clock.now());
  }

  /** The reservations of a space whose slot touches [from, to]. 404 SPACE_NOT_FOUND, 400 INVALID_RANGE. */
  @Transactional(readOnly = true)
  public List<Reservation> ofSpace(String spaceId, LocalDateTime from, LocalDateTime to) {
    findSpace(spaceId);
    if (from != null && to != null && from.isAfter(to)) {
      throw new BusinessRuleException(Kind.INVALID, "INVALID_RANGE", "from must not be after to");
    }
    return reservations.findBySpaceBetween(
        spaceId,
        from == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : from,
        to == null ? LocalDateTime.of(9999, 12, 31, 23, 59, 59) : to);
  }

  // ---------- TS22: the tap ----------

  /**
   * What the tap of {@code cardHolderId} at {@code space} means: it checks their reservation in (the space
   * becomes OCCUPIED) when it is within the 10 minutes, and tells why not otherwise. Joins the caller's transaction.
   */
  @Transactional
  public TapDecision onTap(Space space, String cardHolderId, LocalDateTime at) {
    if (cardHolderId == null) {
      return new TapDecision(TapOutcome.NO_RESERVATION, null);
    }
    List<Reservation> open = reservations.findOpenOfHolder(space.getId(), cardHolderId, at);
    if (open.isEmpty()) {
      return new TapDecision(TapOutcome.NO_RESERVATION, null);
    }
    Reservation reservation = open.get(0);
    CheckInResult result = reservation.checkIn(at);
    return switch (result) {
      case CHECKED_IN -> {
        reservations.save(reservation);
        space.changeStatus(OCCUPIED);
        spaces.save(space);
        publish("space.reservation-activated", reservation, Map.of());
        events.publishEvent(
            new IntegrationEvent(
                "space.space-occupied",
                Map.of(
                    "spaceId", space.getId(),
                    "reservationId", reservation.getId(),
                    "occupiedAt", at.toString())));
        yield new TapDecision(TapOutcome.CHECKED_IN, reservation);
      }
      case ALREADY_ACTIVE -> new TapDecision(TapOutcome.ALREADY_ACTIVE, reservation);
      case NOT_STARTED -> new TapDecision(TapOutcome.NOT_STARTED, reservation);
      case EXPIRED -> new TapDecision(TapOutcome.EXPIRED, reservation);
    };
  }

  // ---------- TS23: the sweep ----------

  /**
   * Forfeits the CONFIRMED reservations whose window ended and completes the CHECKED_IN ones whose slot ended;
   * the holders of the forfeited ones are told by e-mail after the transaction commits.
   *
   * @return how many reservations changed
   */
  @Transactional
  public int sweep(LocalDateTime now) {
    int changed = 0;
    for (Reservation reservation : reservations.findExpiredConfirmed(now)) {
      reservation.forfeit();
      reservations.save(reservation);
      publish("space.reservation-forfeited", reservation, Map.of("forfeitedAt", now.toString()));
      notifyForfeited(reservation);
      changed++;
    }
    for (Reservation reservation : reservations.findFinishedCheckedIn(now)) {
      reservation.complete();
      reservations.save(reservation);
      spaces
          .findById(reservation.getSpaceId())
          .filter(space -> OCCUPIED.equals(space.getStatus()))
          .ifPresent(
              space -> {
                space.changeStatus("AVAILABLE");
                spaces.save(space);
                events.publishEvent(
                    new IntegrationEvent(
                        "space.space-released",
                        Map.of(
                            "spaceId", space.getId(),
                            "reservationId", reservation.getId(),
                            "releasedAt", now.toString())));
              });
      changed++;
    }
    if (changed > 0) {
      log.info("Reservation sweep at {}: {} changed", now, changed);
    }
    return changed;
  }

  private void notifyForfeited(Reservation reservation) {
    Optional<HolderInfo> holder = directory.findHolder(reservation.getCardHolderId());
    if (holder.isEmpty() || holder.get().email() == null) {
      log.warn("Reservation {} forfeited but its holder has no e-mail", reservation.getReservationCode());
      return;
    }
    String spaceName = spaces.findById(reservation.getSpaceId()).map(Space::getName).orElse(reservation.getSpaceId());
    events.publishEvent(
        new ReservationForfeited(
            new ForfeitedNotice(
                reservation.getReservationCode(),
                holder.get().fullName(),
                holder.get().email(),
                spaceName,
                reservation.getStartTime(),
                reservation.getEndTime())));
  }

  // ---------- helpers ----------

  private void publish(String routingKey, Reservation reservation, Map<String, Object> extra) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("reservationId", reservation.getId());
    data.put("reservationCode", reservation.getReservationCode());
    data.put("spaceId", reservation.getSpaceId());
    data.put("cardHolderId", reservation.getCardHolderId());
    data.put("status", reservation.getStatus().name());
    data.put("startTime", reservation.getStartTime().toString());
    data.put("endTime", reservation.getEndTime().toString());
    data.putAll(extra);
    events.publishEvent(new IntegrationEvent(routingKey, data));
  }

  private String newCode(LocalDateTime startTime) {
    String code;
    do {
      code = String.format("RES-%d-%05d", startTime.getYear(), ThreadLocalRandom.current().nextInt(100000));
    } while (reservations.existsByReservationCode(code));
    return code;
  }

  private Reservation find(String reservationId) {
    return reservations
        .findById(reservationId)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    Kind.NOT_FOUND, "RESERVATION_NOT_FOUND", "Unknown reservation " + reservationId));
  }

  private Space findSpace(String spaceId) {
    return spaces
        .findById(spaceId)
        .orElseThrow(
            () -> new BusinessRuleException(Kind.NOT_FOUND, "SPACE_NOT_FOUND", "Unknown space " + spaceId));
  }
}
