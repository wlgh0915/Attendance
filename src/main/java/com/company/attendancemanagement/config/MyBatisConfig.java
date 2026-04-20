package com.company.attendancemanagement.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.company.attendancemanagement.mapper")
public class MyBatisConfig {
}