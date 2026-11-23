package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Incident;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.IncidentType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, String> {

  List<Incident> findByReaderIdAndTypeAndResolvedAtIsNull(String readerId, IncidentType type);

  /** Newest first; {@code open} true keeps only unresolved incidents, false only resolved, null all. */
  @Query(
      """
      select i from Incident i
      where (:readerId is null or i.readerId = :readerId)
        and (:open is null or (:open = true and i.resolvedAt is null) or (:open = false and i.resolvedAt is not null))
      order by i.detectedAt desc, i.id desc
      """)
  List<Incident> search(@Param("readerId") String readerId, @Param("open") Boolean open);
}
