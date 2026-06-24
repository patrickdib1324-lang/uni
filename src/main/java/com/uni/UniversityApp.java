package com.uni;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// This is the START of the whole app. Run this → server starts on http://localhost:8080
@SpringBootApplication
public class UniversityApp {
    public static void main(String[] args) {
        SpringApplication.run(UniversityApp.class, args);
    }
}
