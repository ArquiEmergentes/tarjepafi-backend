package com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence;

import com.pafisolutions.tarjepafi.spacefacility.domain.model.WorkShift;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkShiftRepository extends JpaRepository<WorkShift, String> {

  Optional<WorkShift> findFirstByCardHolderIdAndStatus(String cardHolderId, String status);

  boolean existsByCardHolderIdAndDate(String cardHolderId, LocalDate date);

  @Query(
      """
      select w from WorkShift w
      where (:cardHolderId is null or w.cardHolderId = :cardHolderId)
        and (:status is null or w.status = :status)
        and (:date is null or w.date = :date)
      order by w.entryAt desc
      """)
  List<WorkShift> search(
      @Param("cardHolderId") String cardHolderId,
      @Param("status") String status,
      @Param("date") LocalDate date);
}
