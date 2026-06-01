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
import java.time.YearMonth;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance/department/month")
public class AttendanceDeptMonthController {

    private final WeeklyAttendanceService weeklyService;

    private static final String[] DAY_NAMES = {"일", "월", "화", "수", "목", "금", "토"};

    @GetMapping
    public String monthPage(HttpSession session, Model model,
                            @RequestParam(required = false) String ym,
                            @RequestParam(required = false) String deptCode) {

        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        String company = loginUser.getCompany();

        YearMonth yearMonth;
        try {
            yearMonth = (ym == null || ym.isBlank()) ? YearMonth.now() : YearMonth.parse(ym);
        } catch (Exception e) {
            yearMonth = YearMonth.now();
        }

        List<DepartmentDto> depts = weeklyService.getAccessibleDepts(company, loginUser.getDeptCode());

        String selectedDept;
        if (deptCode != null && !deptCode.isBlank()) {
            boolean allowed = depts.stream().anyMatch(d -> d.getDeptCode().equals(deptCode));
            selectedDept = allowed ? deptCode : loginUser.getDeptCode();
        } else {
            selectedDept = loginUser.getDeptCode();
        }

        List<WeeklyEmpDto> monthData = weeklyService.getDeptMonthly(company, selectedDept, yearMonth);

        int daysInMonth = yearMonth.lengthOfMonth();
        String[] dayDates  = new String[daysInMonth];
        String[] dayLabels = new String[daysInMonth];

        for (int i = 0; i < daysInMonth; i++) {
            LocalDate d = yearMonth.atDay(i + 1);
            dayDates[i]  = d.toString();
            int dow = d.getDayOfWeek().getValue() % 7; // 0=일,1=월,...,6=토
            dayLabels[i] = String.format("%02d(%s)", i + 1, DAY_NAMES[dow]);
        }

        model.addAttribute("monthData",    monthData);
        model.addAttribute("depts",        depts);
        model.addAttribute("selectedDept", selectedDept);
        model.addAttribute("dayDates",     dayDates);
        model.addAttribute("dayLabels",    dayLabels);
        model.addAttribute("currentYm",    yearMonth.toString());
        model.addAttribute("prevYm",       yearMonth.minusMonths(1).toString());
        model.addAttribute("nextYm",       yearMonth.plusMonths(1).toString());
        model.addAttribute("ymDisplay",      yearMonth.getYear() + "년 " + yearMonth.getMonthValue() + "월");
        model.addAttribute("currentYmStart", yearMonth.atDay(1).toString());
        model.addAttribute("currentYmEnd",   yearMonth.atEndOfMonth().toString());

        return "attendance/dept-month";
    }

    private LoginUserDto getLoginUser(HttpSession session) {
        return (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
    }
}