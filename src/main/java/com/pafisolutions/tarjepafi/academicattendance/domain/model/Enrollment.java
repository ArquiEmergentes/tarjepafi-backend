package com.pafisolutions.tarjepafi.academicattendance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A student enrolled in a class schedule. {@code status} is ACTIVE or WITHDRAWN. */
@Entity
@Table(name = "enrollments")
public class Enrollment {

  @Id private String id;

  @Column(name = "student_id", nullable = false)
  private String studentId;

  @Column(name = "schedule_id", nullable = false)
  private String scheduleId;

  @Column(nullable = false)
  private String term;

  @Column(nullable = false)
  private String status;

  protected Enrollment() {}

  public Enrollment(String id, String studentId, String scheduleId, String term, String status) {
    this.id = id;
    this.studentId = studentId;
    this.scheduleId = scheduleId;
    this.term = term;
    this.status = status;
  }

  public String getId() {
    return id;
  }

  public String getStudentId() {
    return studentId;
  }

  public String getScheduleId() {
    return scheduleId;
  }

  public String getTerm() {
    return term;
  }

  public String getStatus() {
    return status;
  }
}
