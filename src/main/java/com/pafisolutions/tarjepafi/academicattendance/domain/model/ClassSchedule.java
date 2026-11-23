package com.pafisolutions.tarjepafi.academicattendance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

/** A weekly class slot. {@code dayOfWeek} is 1 (Monday) to 7 (Sunday). */
@Entity
@Table(name = "class_schedules")
public class ClassSchedule {

  @Id private String id;

  @Column(name = "course_id", nullable = false)
  private String courseId;

  @Column(nullable = false)
  private String section;

  @Column(name = "teacher_id", nullable = false)
  private String teacherId;

  @Column(name = "space_id", nullable = false)
  private String spaceId;

  @Column(name = "day_of_week", nullable = false)
  private int dayOfWeek;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(nullable = false)
  private String term;

  protected ClassSchedule() {}

  public ClassSchedule(
      String id,
      String courseId,
      String section,
      String teacherId,
      String spaceId,
      int dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      String term) {
    this.id = id;
    this.courseId = courseId;
    this.section = section;
    this.teacherId = teacherId;
    this.spaceId = spaceId;
    this.dayOfWeek = dayOfWeek;
    this.startTime = startTime;
    this.endTime = endTime;
    this.term = term;
  }

  public String getId() {
    return id;
  }

  public String getCourseId() {
    return courseId;
  }

  public String getSection() {
    return section;
  }

  public String getTeacherId() {
    return teacherId;
  }

  public String getSpaceId() {
    return spaceId;
  }

  public int getDayOfWeek() {
    return dayOfWeek;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public String getTerm() {
    return term;
  }
}
