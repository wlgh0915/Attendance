package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.calendar.WeeklyEmpDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface WeeklyAttendanceService {
    List<WeeklyEmpDto> getWeeklyByDept(String company, String deptCode, LocalDate weekStart);
    List<WeeklyEmpDto> getDeptMonthly(String company, String deptCode, YearMonth ym);
    List<DepartmentDto> getAccessibleDepts(String company, String deptCode);
}
