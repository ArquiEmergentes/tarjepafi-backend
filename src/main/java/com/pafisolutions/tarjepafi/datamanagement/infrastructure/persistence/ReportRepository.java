package com.pafisolutions.tarjepafi.datamanagement.infrastructure.persistence;

import com.pafisolutions.tarjepafi.datamanagement.domain.model.Report;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, String> {

  /** GENERATING reports requested before {@code cutoff} (real time): the ones the job should complete. */
  List<Report> findByStatusAndRequestedAtBeforeOrderBySeqAsc(Report.Status status, Instant cutoff);
}
