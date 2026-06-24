package com.uni.model;

import jakarta.persistence.*;

// ENROLLMENT = a student SELECTED a course, and (later) got a score.
@Entity
@Table(name = "enrollments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_id"}))
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    private Double score;       // 0–100, null until graded

    // ===== GRADE LOGIC (same rules as the SQL view) =====

    public String getLetter() {                 // score → letter
        if (score == null) return "-";
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    public double getGradePoints() {            // score → 4.0-scale points
        if (score == null) return 0.0;
        if (score >= 90) return 4.0;
        if (score >= 80) return 3.0;
        if (score >= 70) return 2.0;
        if (score >= 60) return 1.0;
        return 0.0;
    }

    public boolean isPassed() {                 // pass = 60 and above
        return score != null && score >= 60;
    }

    // ---- getters / setters ----
    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
}
