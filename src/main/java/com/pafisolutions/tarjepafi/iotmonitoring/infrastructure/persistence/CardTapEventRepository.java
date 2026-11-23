package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.CardTapEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardTapEventRepository extends JpaRepository<CardTapEvent, String> {

  /** Newest first, only events stored after {@code after} (a cursor; 0 for all). */
  @Query(
      """
      select e from CardTapEvent e
      where (:readerId is null or e.readerId = :readerId)
        and e.cursor > :after
      order by e.cursor desc
      """)
  List<CardTapEvent> findAfter(
      @Param("readerId") String readerId, @Param("after") long after, Pageable page);
}
