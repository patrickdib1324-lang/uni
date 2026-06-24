package com.uni.service;

import com.uni.model.Enrollment;
import com.uni.repo.EnrollmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Computes a student's GPA from the courses they SELECTED and passed.
@Service
public class GpaService {

    private final EnrollmentRepository enrollments;

    public GpaService(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    // GPA = sum(gradePoints * credits) / sum(credits)   (credit-weighted, 4.0 scale)
    public double calculateGpa(Long studentId) {
        List<Enrollment> list = enrollments.findByStudentId(studentId); // courses they took

        double totalPoints = 0;   // gradePoints * credits, summed
        int totalCredits = 0;     // credits, summed

        for (Enrollment e : list) {
            if (e.getScore() == null) continue;          // skip not-yet-graded
            int credits = e.getCourse().getCredits();
            totalPoints += e.getGradePoints() * credits; // weight by credits
            totalCredits += credits;
        }

        if (totalCredits == 0) return 0.0;               // avoid divide-by-zero
        return Math.round((totalPoints / totalCredits) * 100.0) / 100.0; // round to 2 decimals
    }

    // How many of the selected courses the student PASSED.
    public long countPassed(Long studentId) {
        return enrollments.findByStudentId(studentId).stream()
                .filter(Enrollment::isPassed)
                .count();
    }

    // GPA using ONLY passed courses (ignore failed ones).
    public double gpaPassedOnly(Long studentId) {
        List<Enrollment> list = enrollments.findByStudentId(studentId);
        double points = 0; int credits = 0;
        for (Enrollment e : list) {
            if (!e.isPassed()) continue;                 // only passed
            int c = e.getCourse().getCredits();
            points += e.getGradePoints() * c;
            credits += c;
        }
        if (credits == 0) return 0.0;
        return Math.round((points / credits) * 100.0) / 100.0;
    }
}
