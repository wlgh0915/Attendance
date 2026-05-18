package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.calendar.WeeklyEmpDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;

import java.time.LocalDate;
import java.util.List;

public interface WeeklyAttendanceService {
    List<WeeklyEmpDto> getWeeklyByDept(String company, String deptCode, LocalDate weekStart);
    List<DepartmentDto> getAccessibleDepts(String company, String deptCode);
}
