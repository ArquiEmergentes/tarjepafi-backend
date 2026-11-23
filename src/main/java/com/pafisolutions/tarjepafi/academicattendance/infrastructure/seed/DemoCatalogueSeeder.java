package com.pafisolutions.tarjepafi.academicattendance.infrastructure.seed;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.ClassSchedule;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Course;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Enrollment;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.ClassScheduleRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.CourseRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.EnrollmentRepository;
import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Imports the demo courses, class schedules and enrollments. Existing rows are left alone. */
@Component
@Profile(DemoSeed.PROFILE)
@Order(30)
public class DemoCatalogueSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoCatalogueSeeder.class);

  private final DemoSeed seed;
  private final CourseRepository courses;
  private final ClassScheduleRepository schedules;
  private final EnrollmentRepository enrollments;

  public DemoCatalogueSeeder(
      DemoSeed seed,
      CourseRepository courses,
      ClassScheduleRepository schedules,
      EnrollmentRepository enrollments) {
    this.seed = seed;
    this.courses = courses;
    this.schedules = schedules;
    this.enrollments = enrollments;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<Course> newCourses = new ArrayList<>();
    for (JsonNode row : seed.collection("courses")) {
      if (!courses.existsById(row.path("id").asString())) {
        newCourses.add(
            new Course(
                row.path("id").asString(),
                row.path("code").asString(),
                row.path("name").asString(),
                row.path("subject").asString()));
      }
    }
    courses.saveAll(newCourses);

    List<ClassSchedule> newSchedules = new ArrayList<>();
    for (JsonNode row : seed.collection("classSchedules")) {
      if (!schedules.existsById(row.path("id").asString())) {
        newSchedules.add(
            new ClassSchedule(
                row.path("id").asString(),
                row.path("courseId").asString(),
                row.path("section").asString(),
                row.path("teacherId").asString(),
                row.path("spaceId").asString(),
                row.path("dayOfWeek").asInt(),
                LocalTime.parse(row.path("startTime").asString()),
                LocalTime.parse(row.path("endTime").asString()),
                row.path("term").asString()));
      }
    }
    schedules.saveAll(newSchedules);

    List<Enrollment> newEnrollments = new ArrayList<>();
    for (JsonNode row : seed.collection("enrollments")) {
      if (!enrollments.existsById(row.path("id").asString())) {
        newEnrollments.add(
            new Enrollment(
                row.path("id").asString(),
                row.path("studentId").asString(),
                row.path("scheduleId").asString(),
                row.path("term").asString(),
                row.path("status").asString()));
      }
    }
    enrollments.saveAll(newEnrollments);

    if (!newCourses.isEmpty() || !newSchedules.isEmpty() || !newEnrollments.isEmpty()) {
      log.info(
          "Demo seed: {} courses, {} class schedules, {} enrollments imported",
          newCourses.size(),
          newSchedules.size(),
          newEnrollments.size());
    }
  }
}
