package com.uni.controller;

import com.uni.model.Enrollment;
import com.uni.repo.EnrollmentRepository;
import com.uni.service.GpaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/students")
public class StudentController {

    private final EnrollmentRepository enrollments;
    private final GpaService gpa;

    public StudentController(EnrollmentRepository enrollments, GpaService gpa) {
        this.enrollments = enrollments;
        this.gpa = gpa;
    }

    // GET /students/1/courses  → the courses this student SELECTED + their grade
    @GetMapping("/{id}/courses")
    public List<Map<String,Object>> myCourses(@PathVariable Long id) {
        return enrollments.findByStudentId(id).stream().map(e -> Map.<String,Object>of(
            "code",   e.getCourse().getCode(),
            "title",  e.getCourse().getTitle(),
            "credits",e.getCourse().getCredits(),
            "score",  e.getScore(),
            "letter", e.getLetter(),
            "status", e.isPassed() ? "PASS" : "FAIL"
        )).toList();
    }

    // GET /students/1/gpa  → the final GPA result
    @GetMapping("/{id}/gpa")
    public Map<String,Object> myGpa(@PathVariable Long id) {
        return Map.of(
            "gpa",            gpa.calculateGpa(id),     // GPA over all graded courses
            "gpaPassedOnly",  gpa.gpaPassedOnly(id),    // GPA ignoring failures
            "coursesPassed",  gpa.countPassed(id)
        );
    }
}
