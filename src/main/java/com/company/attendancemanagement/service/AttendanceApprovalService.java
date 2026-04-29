package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.approval.ApprovalDetailDto;
import com.company.attendancemanagement.dto.approval.ApprovalItemDto;
import com.company.attendancemanagement.dto.approval.ApprovalSearchDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;

import java.util.List;

public interface AttendanceApprovalService {

    List<ApprovalItemDto> searchApprovals(ApprovalSearchDto search, LoginUserDto loginUser);

    int countPendingApprovals(LoginUserDto loginUser);

    ApprovalDetailDto getDetail(String requestId, LoginUserDto loginUser);

    void approveRequest(String requestId, LoginUserDto loginUser);

    void rejectRequest(String requestId, String rejectReason, LoginUserDto loginUser);
}
