package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.AttendanceReqSummaryDto;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.record.DailyAttendanceDto;
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

    /** 자신의 부서 + 하위 부서 전체를 재귀적으로 조회 (ADMIN 전용) */
    List<DepartmentDto> findAccessibleDepts(@Param("company")  String company,
                                             @Param("deptCode") String deptCode);

    /** 월별 출퇴근 실적 조회 (캘린더 표시용) */
    List<DailyAttendanceDto> findRecords(@Param("company")  String company,
                                          @Param("empCode")  String empCode,
                                          @Param("startYmd") String startYmd,
                                          @Param("endYmd")   String endYmd);
}