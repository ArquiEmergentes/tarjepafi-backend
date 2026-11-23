package com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.Enrollment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {

  @Query(
      """
      select e from Enrollment e
      where (:studentId is null or e.studentId = :studentId)
        and (:scheduleId is null or e.scheduleId = :scheduleId)
        and (:term is null or e.term = :term)
        and (:status is null or e.status = :status)
      order by e.id
      """)
  List<Enrollment> search(
      @Param("studentId") String studentId,
      @Param("scheduleId") String scheduleId,
      @Param("term") String term,
      @Param("status") String status);
}
