package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.approval.ApprovalDetailDto;
import com.company.attendancemanagement.dto.approval.ApprovalItemDto;
import com.company.attendancemanagement.dto.approval.ApprovalSearchDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.service.AttendanceApprovalService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance/approval")
public class AttendanceApprovalController {

    private final AttendanceApprovalService approvalService;

    @GetMapping
    public String approvalPage(HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";
        return "attendance/approval";
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<?> list(ApprovalSearchDto search, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            List<ApprovalItemDto> rows = approvalService.searchApprovals(search, loginUser);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/pending-count")
    @ResponseBody
    public ResponseEntity<?> pendingCount(HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of("count", approvalService.countPendingApprovals(loginUser)));
    }

    @GetMapping("/detail")
    @ResponseBody
    public ResponseEntity<?> detail(@RequestParam String requestId, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            ApprovalDetailDto detail = approvalService.getDetail(requestId, loginUser);
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/approve")
    @ResponseBody
    public ResponseEntity<?> approve(@RequestBody Map<String, Object> body, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            @SuppressWarnings("unchecked")
            List<String> requestIds = (List<String>) body.get("requestIds");
            if (requestIds == null || requestIds.isEmpty()) {
                throw new IllegalArgumentException("선택된 항목이 없습니다.");
            }
            for (String requestId : requestIds) {
                approvalService.approveRequest(requestId, loginUser);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/reject")
    @ResponseBody
    public ResponseEntity<?> reject(@RequestBody Map<String, Object> body, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            @SuppressWarnings("unchecked")
            List<String> requestIds = (List<String>) body.get("requestIds");
            String rejectReason = body.get("rejectReason") != null ? body.get("rejectReason").toString() : "";
            if (requestIds == null || requestIds.isEmpty()) {
                throw new IllegalArgumentException("선택된 항목이 없습니다.");
            }
            if (rejectReason.isBlank()) {
                throw new IllegalArgumentException("반려 사유를 입력하세요.");
            }
            for (String requestId : requestIds) {
                approvalService.rejectRequest(requestId, rejectReason, loginUser);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private LoginUserDto getLoginUser(HttpSession session) {
        return (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
    }
}
