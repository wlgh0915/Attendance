package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.request.AttendanceEmpRowDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestSearchDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AttendanceRequestMapper {

    List<AttendanceEmpRowDto> searchEmployees(AttendanceRequestSearchDto search);

    AttendanceRequestDto findByRequestId(@Param("requestId") String requestId);

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

    String findDeptLeader(@Param("company") String company, @Param("deptCode") String deptCode);

    List<DepartmentDto> findDeptListForDropdown(@Param("company") String company);

    List<DepartmentDto> findAccessibleDepts(@Param("company") String company, @Param("deptCode") String deptCode);

    List<ShiftCodeDto> findShiftCodes(@Param("company") String company);
}