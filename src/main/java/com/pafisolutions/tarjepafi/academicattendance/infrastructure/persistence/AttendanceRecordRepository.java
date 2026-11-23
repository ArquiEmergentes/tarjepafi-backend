package com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, String> {

  List<AttendanceRecord> findBySessionId(String sessionId);

  List<AttendanceRecord> findByCardHolderId(String cardHolderId);

  boolean existsByTapEventId(String tapEventId);

  /** Newest first; with {@code after}, only what is newer than that cursor. */
  @Query(
      """
      select r from AttendanceRecord r
      where r.sessionId = :sessionId
        and (:after is null or r.seq > :after)
      order by r.seq desc
      """)
  List<AttendanceRecord> findOfSession(
      @Param("sessionId") String sessionId, @Param("after") Long after);
}
