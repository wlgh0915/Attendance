package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
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

    @GetMapping
    public String calendarPage(HttpSession session, Model model,
                               @RequestParam(required = false) String empCode,
                               @RequestParam(required = false) String deptCode,
                               @RequestParam(required = false) String ym) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        String  company = loginUser.getCompany();
        boolean isAdmin = "ADMIN".equals(loginUser.getRoleCode());

        YearMonth yearMonth = (ym == null || ym.isBlank()) ? YearMonth.now() : YearMonth.parse(ym);

        // ADMIN: 본인 부서 + 하위 부서 전체 (재귀). USER: 빈 목록
        List<DepartmentDto> depts = isAdmin
                ? calendarService.getAccessibleDepts(company, loginUser.getDeptCode())
                : List.of();

        // 부서 결정: ADMIN이고 요청한 deptCode가 접근 가능한 부서인 경우에만 허용
        String selectedDept;
        if (isAdmin && deptCode != null && !deptCode.isBlank()) {
            boolean allowed = depts.stream().anyMatch(d -> d.getDeptCode().equals(deptCode));
            selectedDept = allowed ? deptCode : loginUser.getDeptCode();
        } else {
            selectedDept = loginUser.getDeptCode();
        }

        // 사원 목록: ADMIN만 조회
        List<EmpSimpleDto> emps = isAdmin
                ? calendarService.getEmpsByDept(company, selectedDept) : List.of();

        // 사원 결정: ADMIN은 파라미터 → 부서 첫 사원 순으로, USER는 항상 본인
        String targetEmp;
        if (isAdmin && empCode != null && !empCode.isBlank()) {
            targetEmp = empCode;
        } else if (isAdmin && !emps.isEmpty()) {
            targetEmp = emps.get(0).getEmpCode();
        } else {
            targetEmp = loginUser.getEmpCode();
        }

        List<AttendanceDayDto> days    = calendarService.getCalendar(company, targetEmp, yearMonth);
        Map<String, String>    empInfo = calendarService.getEmpInfo(company, targetEmp);

        model.addAttribute("days",         days);
        model.addAttribute("ymDisplay",    yearMonth.getYear() + "년 " + yearMonth.getMonthValue() + "월");
        model.addAttribute("prevYm",       yearMonth.minusMonths(1).toString());
        model.addAttribute("nextYm",       yearMonth.plusMonths(1).toString());
        model.addAttribute("currentYm",    yearMonth.toString());
        model.addAttribute("targetEmp",    targetEmp);
        model.addAttribute("selectedDept", selectedDept);
        model.addAttribute("empInfo",      empInfo);
        model.addAttribute("isAdmin",      isAdmin);
        model.addAttribute("canViewAll",   isAdmin);
        model.addAttribute("depts",        depts);
        model.addAttribute("emps",         emps);

        return "attendance/calendar";
    }

    // 부서 변경 시 사원 목록 AJAX (ADMIN 전용)
    @GetMapping("/emps")
    @ResponseBody
    public ResponseEntity<?> getEmps(@RequestParam String deptCode, HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();

        if (!"ADMIN".equals(loginUser.getRoleCode())) return ResponseEntity.status(403).build();

        // 요청된 부서가 ADMIN의 접근 가능 범위인지 검증
        List<DepartmentDto> accessible = calendarService.getAccessibleDepts(
                loginUser.getCompany(), loginUser.getDeptCode());
        boolean allowed = accessible.stream().anyMatch(d -> d.getDeptCode().equals(deptCode));
        if (!allowed) return ResponseEntity.status(403).build();

        List<EmpSimpleDto> emps = calendarService.getEmpsByDept(loginUser.getCompany(), deptCode);
        return ResponseEntity.ok(emps);
    }

    private LoginUserDto getLoginUser(HttpSession session) {
        return (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
    }
}