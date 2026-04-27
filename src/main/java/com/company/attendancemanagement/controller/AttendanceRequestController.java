package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.request.AttendanceEmpRowDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestSearchDto;
import com.company.attendancemanagement.service.AttendanceRequestService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance/request")
public class AttendanceRequestController {

    private final AttendanceRequestService requestService;

    @GetMapping("/general")
    public String generalPage(HttpSession session, Model model) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        Map<String, Object> formData = requestService.getFormData(loginUser);
        model.addAllAttributes(formData);
        model.addAttribute("today", LocalDate.now().toString());
        return "attendance/request/general";
    }

    @GetMapping("/other")
    public String otherPage(HttpSession session, Model model) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        Map<String, Object> formData = requestService.getFormData(loginUser);
        model.addAllAttributes(formData);
        model.addAttribute("today", LocalDate.now().toString());
        return "attendance/request/other";
    }

    @GetMapping("/general/search")
    @ResponseBody
    public ResponseEntity<?> searchGeneral(AttendanceRequestSearchDto search, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            List<AttendanceEmpRowDto> rows = requestService.searchEmployees(search, loginUser);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/other/search")
    @ResponseBody
    public ResponseEntity<?> searchOther(AttendanceRequestSearchDto search, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            search.setRequestCategory("OTHER");
            List<AttendanceEmpRowDto> rows = requestService.searchEmployees(search, loginUser);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<?> save(@RequestBody AttendanceRequestDto dto, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            AttendanceRequestDto saved = requestService.saveRequest(dto, loginUser);
            return ResponseEntity.ok(Map.of("success", true, "requestId", saved.getRequestId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/delete")
    @ResponseBody
    public ResponseEntity<?> delete(@RequestBody Map<String, Object> body, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            String requestId = body.get("requestId").toString();
            requestService.deleteRequest(requestId, loginUser);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/submit")
    @ResponseBody
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            String requestId = body.get("requestId").toString();
            requestService.submitRequest(requestId, loginUser);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/cancel-submit")
    @ResponseBody
    public ResponseEntity<?> cancelSubmit(@RequestBody Map<String, Object> body, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        try {
            String requestId = body.get("requestId").toString();
            requestService.cancelSubmit(requestId, loginUser);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private LoginUserDto getLoginUser(HttpSession session) {
        return (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
    }
}