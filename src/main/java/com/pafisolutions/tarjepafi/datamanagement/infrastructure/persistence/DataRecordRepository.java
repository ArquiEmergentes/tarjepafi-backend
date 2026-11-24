package com.pafisolutions.tarjepafi.datamanagement.infrastructure.persistence;

import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataRecord;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataType;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.SourceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataRecordRepository extends JpaRepository<DataRecord, String> {

  boolean existsByEventIdAndDataTypeAndSubjectId(String eventId, DataType dataType, String subjectId);

  /** Newest first, only what is after the cursor, with optional filters. */
  @Query(
      """
      select r from DataRecord r
      where r.seq > :after
        and (:dataType is null or r.dataType = :dataType)
        and (:sourceContext is null or r.sourceContext = :sourceContext)
        and (:subjectId is null or r.subjectId = :subjectId)
        and r.occurredAt >= :from and r.occurredAt <= :to
      order by r.seq desc
      """)
  List<DataRecord> findAfter(
      @Param("after") long after,
      @Param("dataType") DataType dataType,
      @Param("sourceContext") SourceContext sourceContext,
      @Param("subjectId") String subjectId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      Pageable page);

  /** The facts of a period in recording order, which is what the report calculator expects. */
  List<DataRecord> findByOccurredAtBetweenOrderBySeqAsc(LocalDateTime from, LocalDateTime to);
}
