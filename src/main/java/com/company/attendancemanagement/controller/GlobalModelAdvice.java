package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.service.AttendanceApprovalService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final AttendanceApprovalService approvalService;

    @ModelAttribute("pendingApprovalCount")
    public int pendingApprovalCount(HttpSession session) {
        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
        if (loginUser == null) return 0;
        return approvalService.countPendingApprovals(loginUser);
    }
}
