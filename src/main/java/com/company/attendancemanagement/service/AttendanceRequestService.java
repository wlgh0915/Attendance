package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.request.AttendanceEmpRowDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestSearchDto;

import java.util.List;
import java.util.Map;

public interface AttendanceRequestService {

    Map<String, Object> getFormData(LoginUserDto loginUser);

    List<AttendanceEmpRowDto> searchEmployees(AttendanceRequestSearchDto search, LoginUserDto loginUser);

    AttendanceRequestDto saveRequest(AttendanceRequestDto dto, LoginUserDto loginUser);

    void deleteRequest(Long requestId, LoginUserDto loginUser);

    void submitRequest(Long requestId, LoginUserDto loginUser);

    void cancelSubmit(Long requestId, LoginUserDto loginUser);
}