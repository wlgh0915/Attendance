package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.approval.ApprovalDetailDto;
import com.company.attendancemanagement.dto.request.AttendanceEmpRowDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestHistoryDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestHistorySearchDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestSearchDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface AttendanceRequestMapper {

    List<AttendanceEmpRowDto> searchEmployees(AttendanceRequestSearchDto search);

    List<AttendanceRequestHistoryDto> findHistory(AttendanceRequestHistorySearchDto search);

    AttendanceRequestDto findByRequestId(@Param("requestId") String requestId);

    ApprovalDetailDto findHistoryDetail(@Param("requestId") String requestId,
                                        @Param("company") String company);

    int countActiveSameWorkRequest(AttendanceRequestDto dto);

    int insertRequestHeader(AttendanceRequestDto dto);

    int insertGeneralDetail(AttendanceRequestDto dto);

    int insertOtherDetail(AttendanceRequestDto dto);

    int updateRequestHeader(AttendanceRequestDto dto);

    int updateGeneralDetail(AttendanceRequestDto dto);

    int updateOtherDetail(AttendanceRequestDto dto);

    int deleteGeneralDetail(@Param("requestId") String requestId);

    int deleteOtherDetail(@Param("requestId") String requestId);

    int deleteRequestHeader(@Param("requestId") String requestId);

    int updateStatus(@Param("requestId") String requestId, @Param("status") String status);

    int applyApprovedOtherRequestToAttendance(@Param("requestId") String requestId);

    String findDeptLeader(@Param("company") String company, @Param("deptCode") String deptCode);

    List<DepartmentDto> findDeptListForDropdown(@Param("company") String company);

    List<DepartmentDto> findAccessibleDepts(@Param("company") String company, @Param("deptCode") String deptCode);

    List<ShiftCodeDto> findShiftCodes(@Param("company") String company);

    /** 해당 날짜의 계획 근무 유형 조회 (WORK / OFF / HOLIDAY / "") */
    String findPlannedWorkDayType(@Param("company")  String company,
                                  @Param("empCode")  String empCode,
                                  @Param("workDate") String workDate);

    /** 해당 날짜의 계획 근무 시작/종료 시간 조회 (조퇴·외출 validation용) */
    Map<String, Object> findPlannedShiftInfo(@Param("company")  String company,
                                             @Param("empCode")  String empCode,
                                             @Param("workDate") String workDate);

    Map<String, Object> findShiftInfoByCodeOrName(@Param("company") String company,
                                                  @Param("shiftCodeOrName") String shiftCodeOrName);

    Map<String, Object> findEffectiveWorkTimeInfo(AttendanceRequestDto dto);

    /** 조퇴↔연장 충돌 신청 존재 여부 확인 */
    int countAttendanceCheckIn(@Param("company") String company,
                               @Param("empCode") String empCode,
                               @Param("workDate") String workDate);

    Map<String, Object> findAttendanceRecordInfo(@Param("company") String company,
                                                 @Param("empCode") String empCode,
                                                 @Param("workDate") String workDate);

    int findWeeklyPlannedWorkMin(@Param("company") String company,
                                 @Param("empCode") String empCode,
                                 @Param("workDate") String workDate);

    int sumActiveWeeklyRequestEffectMin(AttendanceRequestDto dto);

    int countActiveOverlappingRequest(AttendanceRequestDto dto);

    int countActiveConflictingRequest(AttendanceRequestDto dto);
}
