package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import com.company.attendancemanagement.mapper.AttendanceRecordMapper;
import com.company.attendancemanagement.service.AnnualLeaveService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final AttendanceRecordMapper recordMapper;
    private final AnnualLeaveService     annualLeaveService;

    private static final DateTimeFormatter YMD       = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String[]          DAY_LABELS = {"일", "월", "화", "수", "목", "금", "토"};

    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
        if (loginUser == null) return "redirect:/login";

        model.addAttribute("loginUser", loginUser);

        String    company = loginUser.getCompany();
        String    empCode = loginUser.getEmpCode();
        LocalDate today   = LocalDate.now();
        YearMonth ym      = YearMonth.of(today.getYear(), today.getMonth());
        String    startYmd = ym.atDay(1).format(YMD);
        String    endYmd   = ym.atEndOfMonth().format(YMD);

        // 1. 오늘 정규 근무시간 조회
        String workOnHhmm  = null;
        String workOffHhmm = null;
        try {
            Map<String, Object> shift = recordMapper.findPlannedShift(company, empCode, today.format(YMD));
            if (shift != null) {
                Object on  = shift.getOrDefault("workOnHhmm",  shift.get("WORKONHHMM"));
                Object off = shift.getOrDefault("workOffHhmm", shift.get("WORKOFFHHMM"));
                if (on  != null && !on.toString().isBlank())  workOnHhmm  = on.toString();
                if (off != null && !off.toString().isBlank()) workOffHhmm = off.toString();
            }
        } catch (Exception ignored) {}
        model.addAttribute("workOnHhmm",  workOnHhmm);
        model.addAttribute("workOffHhmm", workOffHhmm);

        // 2. 이번 달 출퇴근 실적
        List<AttendanceRecordDto> monthRecords = recordMapper.findByMonth(company, empCode, startYmd, endYmd);

        // 이번 주 누적 근무시간 (급여 산정 기준: checkIn 있는 날 workMin 합산)
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        int weekActualMin = monthRecords.stream()
                .filter(r -> {
                    if (r.getCheckIn() == null || r.getCheckIn().isBlank() || r.getWorkMin() == null) return false;
                    LocalDate d = LocalDate.parse(r.getYyyymmdd(), YMD);
                    return !d.isBefore(weekStart) && !d.isAfter(today);
                })
                .mapToInt(AttendanceRecordDto::getWorkMin)
                .sum();
        int weekGoalMin  = 2400; // 40h
        int weekPct      = Math.min(100, weekGoalMin > 0 ? weekActualMin * 100 / weekGoalMin : 0);
        int remainMin    = Math.max(0, weekGoalMin - weekActualMin);
        model.addAttribute("weekActualTime",  fmtMin(weekActualMin));
        model.addAttribute("weekPct",         weekPct);
        model.addAttribute("weekRemainTime",  fmtMin(remainMin));
        model.addAttribute("weekGoalReached", weekActualMin >= weekGoalMin);

        // 지각 횟수 (이번 달)
        long lateCount = monthRecords.stream()
                .filter(r -> "Y".equals(r.getLateYn()))
                .count();
        model.addAttribute("lateCount", lateCount);

        // 최근 출근 기록 (checkIn 있는 것 최신 3건)
        List<AttendanceRecordDto> recentRecords = monthRecords.stream()
                .filter(r -> r.getCheckIn() != null && !r.getCheckIn().isBlank())
                .sorted(Comparator.comparing(AttendanceRecordDto::getYyyymmdd).reversed())
                .limit(3)
                .peek(r -> {
                    LocalDate d = LocalDate.parse(r.getYyyymmdd(), YMD);
                    String day = DAY_LABELS[d.getDayOfWeek().getValue() % 7];
                    r.setDateDisplay(String.format("%02d.%02d (%s)", d.getMonthValue(), d.getDayOfMonth(), day));
                })
                .collect(Collectors.toList());
        model.addAttribute("recentRecords", recentRecords);

        // 3. 잔여 연차
        String availableLeave = "0";
        try {
            BigDecimal leave = annualLeaveService.availableDay(company, empCode, today.getYear());
            availableLeave = leave.stripTrailingZeros().toPlainString();
        } catch (Exception ignored) {}
        model.addAttribute("availableLeave", availableLeave);

        return "main";
    }

    private String fmtMin(int min) {
        return (min / 60) + "h " + (min % 60) + "m";
    }
}