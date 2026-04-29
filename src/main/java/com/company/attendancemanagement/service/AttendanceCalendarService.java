package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public interface AttendanceCalendarService {
    List<AttendanceDayDto> getCalendar(String company, String empCode, YearMonth ym);
    Map<String, String>    getEmpInfo(String company, String empCode);
    List<EmpSimpleDto>     getEmpsByDept(String company, String deptCode);
    /** ADMIN의 접근 가능 부서 목록 (본인 부서 + 하위 부서 재귀) */
    List<DepartmentDto>    getAccessibleDepts(String company, String deptCode);
}