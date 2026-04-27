package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public interface AttendanceCalendarService {
    List<AttendanceDayDto> getCalendar(String company, String empCode, YearMonth ym);
    Map<String, String>    getEmpInfo(String company, String empCode);
    List<EmpSimpleDto>     getEmpsByDept(String company, String deptCode);
}