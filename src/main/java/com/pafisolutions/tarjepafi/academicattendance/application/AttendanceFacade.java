package com.pafisolutions.tarjepafi.academicattendance.application;

import java.time.LocalDateTime;

/** What Academic Attendance offers to the other contexts. */
public interface AttendanceFacade {

  /**
   * True when a meeting is accepting marks in the space at that moment. A teacher's tap in a meeting
   * room is resolved by attendance then, and by space access otherwise.
   */
  boolean hasMeetingInProgress(String spaceId, LocalDateTime at);
}
