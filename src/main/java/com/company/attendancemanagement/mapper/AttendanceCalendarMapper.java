package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.AttendanceReqSummaryDto;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface AttendanceCalendarMapper {

    List<AttendanceDayDto> findDailyShifts(@Param("company")   String company,
                                           @Param("empCode")   String empCode,
                                           @Param("startDate") String startDate,
                                           @Param("endDate")   String endDate);

    List<AttendanceReqSummaryDto> findRequests(@Param("company")   String company,
                                               @Param("empCode")   String empCode,
                                               @Param("startDate") String startDate,
                                               @Param("endDate")   String endDate);

    List<EmpSimpleDto> findEmpsByDept(@Param("company")  String company,
                                      @Param("deptCode") String deptCode);

    Map<String, String> findEmpInfo(@Param("company") String company,
                                    @Param("empCode") String empCode);
}