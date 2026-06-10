package com.company.attendancemanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AttendanceManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendanceManagementApplication.class, args);
    }

}
