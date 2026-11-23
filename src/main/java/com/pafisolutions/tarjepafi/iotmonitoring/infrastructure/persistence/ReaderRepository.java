package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Reader;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReaderRepository extends JpaRepository<Reader, String> {

  @Query(
      """
      select r from Reader r
      where (:status is null or r.status = :status)
        and (:spaceId is null or r.spaceId = :spaceId)
      order by r.id
      """)
  List<Reader> search(@Param("status") ReaderStatus status, @Param("spaceId") String spaceId);
}
