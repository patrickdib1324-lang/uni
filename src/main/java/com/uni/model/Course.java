package com.uni.model;

import jakarta.persistence.*;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;      // e.g. "CS201"
    private String title;
    private String teacher;
    private int credits;      // used to weight the GPA

    // ---- getters / setters ----
    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }
    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }
}
