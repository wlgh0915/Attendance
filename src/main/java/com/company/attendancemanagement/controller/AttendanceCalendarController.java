package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.mapper.AttendanceRequestMapper;
import com.company.attendancemanagement.service.AttendanceCalendarService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance/calendar")
public class AttendanceCalendarController {

    private final AttendanceCalendarService calendarService;
    private final AttendanceRequestMapper   requestMapper;

    @GetMapping
    public String calendarPage(HttpSession session, Model model,
                               @RequestParam(required = false) String empCode,
                               @RequestParam(required = false) String deptCode,
                               @RequestParam(required = false) String ym) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        String company       = loginUser.getCompany();
        boolean isAdmin      = "ADMIN".equals(loginUser.getRoleCode());
        String  deptLeader   = requestMapper.findDeptLeader(company, loginUser.getDeptCode());
        boolean isDeptLeader = loginUser.getEmpCode().equals(deptLeader);
        boolean canViewAll   = isAdmin || isDeptLeader;

        YearMonth yearMonth  = (ym == null || ym.isBlank()) ? YearMonth.now() : YearMonth.parse(ym);

        // 부서 결정: canViewAll이면 URL 파라미터 deptCode 우선 사용
        String selectedDept;
        if (canViewAll && deptCode != null && !deptCode.isBlank()) {
            selectedDept = deptCode;
        } else {
            selectedDept = loginUser.getDeptCode();
        }

        // 사원 목록 조회
        List<EmpSimpleDto> emps = canViewAll
                ? calendarService.getEmpsByDept(company, selectedDept) : List.of();

        // 사원 결정: 파라미터 → 부서 첫 사원(관리자/부서장) → 본인
        String targetEmp;
        if (canViewAll && empCode != null && !empCode.isBlank()) {
            targetEmp = empCode;
        } else if (canViewAll && !emps.isEmpty()) {
            // 부서 변경 시 첫 번째 사원 자동 선택
            targetEmp = emps.get(0).getEmpCode();
        } else {
            targetEmp = loginUser.getEmpCode();
        }

        List<AttendanceDayDto> days    = calendarService.getCalendar(company, targetEmp, yearMonth);
        Map<String, String>    empInfo = calendarService.getEmpInfo(company, targetEmp);
        List<DepartmentDto>    depts   = isAdmin ? requestMapper.findDeptListForDropdown(company) : List.of();

        model.addAttribute("days",         days);
        model.addAttribute("ymDisplay",    yearMonth.getYear() + "년 " + yearMonth.getMonthValue() + "월");
        model.addAttribute("prevYm",       yearMonth.minusMonths(1).toString());
        model.addAttribute("nextYm",       yearMonth.plusMonths(1).toString());
        model.addAttribute("currentYm",    yearMonth.toString());
        model.addAttribute("targetEmp",    targetEmp);
        model.addAttribute("selectedDept", selectedDept);
        model.addAttribute("empInfo",      empInfo);
        model.addAttribute("isAdmin",      isAdmin);
        model.addAttribute("isDeptLeader", isDeptLeader);
        model.addAttribute("canViewAll",   canViewAll);
        model.addAttribute("depts",        depts);
        model.addAttribute("emps",         emps);

        return "attendance/calendar";
    }

    // 부서 변경 시 사원 목록 AJAX
    @GetMapping("/emps")
    @ResponseBody
    public ResponseEntity<?> getEmps(@RequestParam String deptCode, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();

        boolean isAdmin    = "ADMIN".equals(loginUser.getRoleCode());
        String  deptLeader = requestMapper.findDeptLeader(loginUser.getCompany(), loginUser.getDeptCode());
        boolean isDeptLeader = loginUser.getEmpCode().equals(deptLeader);

        if (!isAdmin && !isDeptLeader) return ResponseEntity.status(403).build();

        List<EmpSimpleDto> emps = calendarService.getEmpsByDept(loginUser.getCompany(), deptCode);
        return ResponseEntity.ok(emps);
    }

    private LoginUserDto getLoginUser(HttpSession session) {
        return (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
    }
}