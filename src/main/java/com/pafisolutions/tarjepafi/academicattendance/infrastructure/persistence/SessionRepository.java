package com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.Session;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.SessionType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SessionRepository extends JpaRepository<Session, String> {

  /** Sessions of a space on a day, the ones a tap in that space can belong to. */
  List<Session> findBySpaceIdAndDateAndTypeOrderByScheduledStart(
      String spaceId, LocalDate date, SessionType type);

  /** Every session that is not FINISHED yet: the candidates of the automatic close. */
  @Query("select s from Session s where s.status <> 'FINISHED' order by s.scheduledEnd")
  List<Session> findOpen();

  /** The sessions of those schedules on one day, earliest first. */
  List<Session> findByScheduleIdInAndDateOrderByScheduledStart(
      Collection<String> scheduleIds, LocalDate date);
}
