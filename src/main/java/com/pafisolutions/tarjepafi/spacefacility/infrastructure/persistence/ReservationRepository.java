package com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence;

import com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

  boolean existsByReservationCode(String reservationCode);

  /** A CONFIRMED or CHECKED_IN reservation of the space whose slot overlaps [start, end]. */
  @Query(
      """
      select count(r) > 0 from Reservation r
      where r.spaceId = :spaceId
        and r.status in (com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.Status.CONFIRMED,
                         com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.Status.CHECKED_IN)
        and r.startTime < :end and r.endTime > :start
      """)
  boolean existsOverlapping(
      @Param("spaceId") String spaceId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  /** Reservations in progress at {@code at}: CONFIRMED or CHECKED_IN whose slot contains it. */
  @Query(
      """
      select r from Reservation r
      where r.status in (com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.Status.CONFIRMED,
                         com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.Status.CHECKED_IN)
        and r.startTime <= :at and r.endTime >= :at
      order by r.startTime, r.seq
      """)
  List<Reservation> findActive(@Param("at") LocalDateTime at);

  /** The reservations of a space whose slot touches [from, to], oldest first. */
  @Query(
      """
      select r from Reservation r
      where r.spaceId = :spaceId and r.startTime <= :to and r.endTime >= :from
      order by r.startTime, r.seq
      """)
  List<Reservation> findBySpaceBetween(
      @Param("spaceId") String spaceId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  /** The open reservations of a holder at a space that have not ended by {@code at}, soonest first. */
  @Query(
      """
      select r from Reservation r
      where r.spaceId = :spaceId and r.cardHolderId = :cardHolderId
        and r.status in (com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.Status.CONFIRMED,
                         com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.Status.CHECKED_IN)
        and r.endTime >= :at
      order by r.startTime, r.seq
      """)
  List<Reservation> findOpenOfHolder(
      @Param("spaceId") String spaceId,
      @Param("cardHolderId") String cardHolderId,
      @Param("at") LocalDateTime at);

  /** CONFIRMED reservations whose check-in window ended before {@code at}. */
  @Query(
      """
      select r from Reservation r
      where r.status = com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.Status.CONFIRMED
        and r.checkInWindowEnd < :at
      order by r.seq
      """)
  List<Reservation> findExpiredConfirmed(@Param("at") LocalDateTime at);

  /** CHECKED_IN reservations whose slot ended at or before {@code at}. */
  @Query(
      """
      select r from Reservation r
      where r.status = com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.Status.CHECKED_IN
        and r.endTime <= :at
      order by r.seq
      """)
  List<Reservation> findFinishedCheckedIn(@Param("at") LocalDateTime at);
}
