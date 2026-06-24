package com.uni.repo;

import com.uni.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    // SELECT * FROM enrollments WHERE student_id = ?
    List<Enrollment> findByStudentId(Long studentId);
}
