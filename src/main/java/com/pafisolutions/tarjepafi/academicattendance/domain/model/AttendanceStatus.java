package com.pafisolutions.tarjepafi.academicattendance.domain.model;

/** PENDING waits for the teacher to arrive; JUSTIFIED is an absence the teacher excused (TS16). */
public enum AttendanceStatus {
  PENDING,
  PRESENT,
  LATE,
  ABSENT,
  JUSTIFIED
}
