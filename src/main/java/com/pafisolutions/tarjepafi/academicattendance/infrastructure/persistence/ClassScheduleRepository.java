package com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.ClassSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, String> {

  @Query(
      """
      select s from ClassSchedule s
      where (:teacherId is null or s.teacherId = :teacherId)
        and (:spaceId is null or s.spaceId = :spaceId)
        and (:term is null or s.term = :term)
      order by s.dayOfWeek, s.startTime, s.id
      """)
  List<ClassSchedule> search(
      @Param("teacherId") String teacherId,
      @Param("spaceId") String spaceId,
      @Param("term") String term);
}
