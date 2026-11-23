package com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.Course;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, String> {

  List<Course> findAllByOrderByCode();
}
