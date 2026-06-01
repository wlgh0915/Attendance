package com.company.attendancemanagement.dto.calendar;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WeeklyEmpDto {
    private String empCode;
    private String empName;
    private String deptName;
    private String positionName;
    private List<AttendanceDayDto> days; // 7일 (일~토)
}
