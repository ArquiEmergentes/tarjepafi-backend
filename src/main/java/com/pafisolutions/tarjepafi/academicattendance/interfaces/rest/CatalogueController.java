package com.pafisolutions.tarjepafi.academicattendance.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.ClassSchedule;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Course;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Enrollment;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.ClassScheduleRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.CourseRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.EnrollmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only academic catalogues that the screens need before the attendance rules exist. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Academic catalogues")
@Transactional(readOnly = true)
public class CatalogueController {

  public record CourseResource(@Schema(example = "crs-01") String id, @Schema(example = "1ASI0728") String code, @Schema(example = "Aplicaciones Web") String name, @Schema(example = "Desarrollo de Software") String subject) {
    static CourseResource from(Course course) {
      return new CourseResource(
          course.getId(), course.getCode(), course.getName(), course.getSubject());
    }
  }

  /** {@code dayOfWeek}: 1 = Monday ... 7 = Sunday. Times are {@code HH:mm}. */
  public record ClassScheduleResource(
      @Schema(example = "sch-02") String id,
      @Schema(example = "crs-01") String courseId,
      @Schema(example = "SW51") String section,
      @Schema(example = "ch-d0001") String teacherId,
      @Schema(example = "sp-a301") String spaceId,
      @Schema(example = "3") int dayOfWeek,
      @Schema(example = "10:00") String startTime,
      @Schema(example = "11:30") String endTime,
      @Schema(example = "2026-2") String term) {
    static ClassScheduleResource from(ClassSchedule schedule) {
      return new ClassScheduleResource(
          schedule.getId(),
          schedule.getCourseId(),
          schedule.getSection(),
          schedule.getTeacherId(),
          schedule.getSpaceId(),
          schedule.getDayOfWeek(),
          schedule.getStartTime().toString(),
          schedule.getEndTime().toString(),
          schedule.getTerm());
    }
  }

  /** {@code status}: ACTIVE or WITHDRAWN. */
  public record EnrollmentResource(
      @Schema(example = "enr-00022") String id, @Schema(example = "ch-u202312966") String studentId, @Schema(example = "sch-01") String scheduleId, @Schema(example = "2026-2") String term, @Schema(example = "ACTIVE") String status) {
    static EnrollmentResource from(Enrollment enrollment) {
      return new EnrollmentResource(
          enrollment.getId(),
          enrollment.getStudentId(),
          enrollment.getScheduleId(),
          enrollment.getTerm(),
          enrollment.getStatus());
    }
  }

  private final CourseRepository courses;
  private final ClassScheduleRepository schedules;
  private final EnrollmentRepository enrollments;

  public CatalogueController(
      CourseRepository courses, ClassScheduleRepository schedules, EnrollmentRepository enrollments) {
    this.courses = courses;
    this.schedules = schedules;
    this.enrollments = enrollments;
  }

  @GetMapping("/courses")
  @Operation(summary = "List the courses ordered by code")
  public List<CourseResource> courses() {
    return courses.findAllByOrderByCode().stream().map(CourseResource::from).toList();
  }

  @GetMapping("/class-schedules")
  @Operation(
      summary = "List the weekly class slots",
      description = "Optional filters: `teacherId` (a card holder id), `spaceId`, `term`.")
  public List<ClassScheduleResource> classSchedules(
      @RequestParam(required = false) String teacherId,
      @RequestParam(required = false) String spaceId,
      @RequestParam(required = false) String term) {
    return schedules.search(teacherId, spaceId, term).stream()
        .map(ClassScheduleResource::from)
        .toList();
  }

  @GetMapping("/enrollments")
  @Operation(
      summary = "List the enrollments",
      description = "Optional filters: `studentId` (a card holder id), `scheduleId`, `term`, `status`.")
  public List<EnrollmentResource> enrollments(
      @RequestParam(required = false) String studentId,
      @RequestParam(required = false) String scheduleId,
      @RequestParam(required = false) String term,
      @RequestParam(required = false) String status) {
    return enrollments.search(studentId, scheduleId, term, status).stream()
        .map(EnrollmentResource::from)
        .toList();
  }
}
