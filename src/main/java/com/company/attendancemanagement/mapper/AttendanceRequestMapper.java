package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.request.AttendanceEmpRowDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestSearchDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AttendanceRequestMapper {

    List<AttendanceEmpRowDto> searchEmployees(AttendanceRequestSearchDto search);

    AttendanceRequestDto findByRequestId(@Param("requestId") Long requestId);

    int insertRequest(AttendanceRequestDto dto);

    int updateRequest(AttendanceRequestDto dto);

    int deleteRequest(@Param("requestId") Long requestId);

    int updateStatus(@Param("requestId") Long requestId, @Param("status") String status);

    String findDeptLeader(@Param("company") String company, @Param("deptCode") String deptCode);

    List<DepartmentDto> findDeptListForDropdown(@Param("company") String company);

    List<ShiftCodeDto> findShiftCodes(@Param("company") String company);
}