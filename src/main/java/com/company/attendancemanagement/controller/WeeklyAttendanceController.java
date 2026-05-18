package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.calendar.WeeklyEmpDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.service.WeeklyAttendanceService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance/department/week")
public class WeeklyAttendanceController {

    private final WeeklyAttendanceService weeklyService;

    @GetMapping
    public String weeklyPage(HttpSession session, Model model,
                             @RequestParam(required = false) String workDate,
                             @RequestParam(required = false) String deptCode) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";
        if (!"ADMIN".equals(loginUser.getRoleCode())) return "redirect:/attendance/calendar";

        String company = loginUser.getCompany();

        LocalDate date;
        try {
            date = (workDate == null || workDate.isBlank()) ? LocalDate.now() : LocalDate.parse(workDate);
        } catch (Exception e) {
            date = LocalDate.now();
        }

        // 주 시작(일요일) 계산: SUNDAY=7 → 0, MON=1, ..., SAT=6
        int       daysBack  = date.getDayOfWeek().getValue() % 7;
        LocalDate weekStart = date.minusDays(daysBack);
        LocalDate weekEnd   = weekStart.plusDays(6);

        List<DepartmentDto> depts = weeklyService.getAccessibleDepts(company, loginUser.getDeptCode());

        String selectedDept;
        if (deptCode != null && !deptCode.isBlank()) {
            boolean allowed = depts.stream().anyMatch(d -> d.getDeptCode().equals(deptCode));
            selectedDept = allowed ? deptCode : loginUser.getDeptCode();
        } else {
            selectedDept = loginUser.getDeptCode();
        }

        List<WeeklyEmpDto> weeklyData = weeklyService.getWeeklyByDept(company, selectedDept, weekStart);

        String[] dayDates = new String[7];
        for (int i = 0; i < 7; i++) {
            dayDates[i] = weekStart.plusDays(i).toString();
        }

        String[] dayNames = {"일","월","화","수","목","금","토"};

        model.addAttribute("weeklyData",   weeklyData);
        model.addAttribute("weekStart",    weekStart.toString());
        model.addAttribute("weekEnd",      weekEnd.toString());
        model.addAttribute("workDate",     date.toString());
        model.addAttribute("selectedDept", selectedDept);
        model.addAttribute("depts",        depts);
        model.addAttribute("dayDates",     dayDates);
        model.addAttribute("dayNames",     dayNames);
        model.addAttribute("prevWeekDate", weekStart.minusDays(1).toString());
        model.addAttribute("nextWeekDate", weekEnd.plusDays(1).toString());

        return "attendance/weekly";
    }

    private LoginUserDto getLoginUser(HttpSession session) {
        return (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
    }
}
