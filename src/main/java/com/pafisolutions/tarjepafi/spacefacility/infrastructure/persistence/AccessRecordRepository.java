package com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence;

import com.pafisolutions.tarjepafi.spacefacility.domain.model.AccessRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessRecordRepository extends JpaRepository<AccessRecord, String> {

  boolean existsByTapEventId(String tapEventId);

  /** Newest first, only what is after the cursor, with optional filters. */
  @Query(
      """
      select r from AccessRecord r
      where r.seq > :after
        and (:spaceId is null or r.spaceId = :spaceId)
        and (:cardHolderId is null or r.cardHolderId = :cardHolderId)
        and (:decision is null or r.decision = :decision)
      order by r.seq desc
      """)
  List<AccessRecord> findAfter(
      @Param("after") long after,
      @Param("spaceId") String spaceId,
      @Param("cardHolderId") String cardHolderId,
      @Param("decision") String decision,
      Pageable page);

  /** Granted campus entries and exits of one holder (or of everybody when null) between two moments. */
  @Query(
      """
      select r from AccessRecord r
      where r.decision = 'GRANTED'
        and r.accessType in ('CAMPUS_ENTRY', 'CAMPUS_EXIT')
        and r.cardHolderId is not null
        and (:cardHolderId is null or r.cardHolderId = :cardHolderId)
        and r.attemptedAt >= :from and r.attemptedAt <= :to
      """)
  List<AccessRecord> findCampusMovements(
      @Param("cardHolderId") String cardHolderId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
