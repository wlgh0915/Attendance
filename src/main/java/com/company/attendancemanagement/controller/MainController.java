package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.AttendanceReqSummaryDto;
import com.company.attendancemanagement.mapper.AttendanceApprovalMapper;
import com.company.attendancemanagement.mapper.AttendanceCalendarMapper;
import com.company.attendancemanagement.mapper.AttendanceRecordMapper;
import com.company.attendancemanagement.mapper.AttendanceRequestMapper;
import com.company.attendancemanagement.service.AnnualLeaveService;
import com.company.attendancemanagement.service.AttendanceRecordService;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final AttendanceRecordMapper   recordMapper;
    private final AttendanceRequestMapper  requestMapper;
    private final AttendanceCalendarMapper calendarMapper;
    private final AttendanceApprovalMapper approvalMapper;
    private final AnnualLeaveService       annualLeaveService;
    private final AttendanceRecordService  recordService;

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
        fillNullWorkMin(monthRecords, company, empCode);

        // 이번 주 누적 근무시간 (캘린더 기준: 실출근 workMin + 연차/기타휴가 승인일 계획근무분)
        LocalDate weekStart    = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd      = weekStart.plusDays(6); // 일요일
        String    weekStartYmd = weekStart.format(YMD);
        String    weekEndYmd   = weekEnd.format(YMD);
        String    weekStartStr = weekStart.toString();   // yyyy-MM-dd
        String    weekEndStr   = weekEnd.toString();

        // 주 전체 실출근 기록 조회 (7h30m 등 실제 기록 누락 방지)
        List<AttendanceRecordDto> weekRecords =
                recordMapper.findByMonth(company, empCode, weekStartYmd, weekEndYmd);
        fillNullWorkMin(weekRecords, company, empCode);

        Set<String> checkedInDates = weekRecords.stream()
                .filter(r -> r.getCheckIn() != null && !r.getCheckIn().isBlank())
                .map(r -> {
                    String y = r.getYyyymmdd();
                    return y.substring(0, 4) + "-" + y.substring(4, 6) + "-" + y.substring(6, 8);
                })
                .collect(Collectors.toSet());

        // 출장 등 actualShiftCode 레코드(checkIn 없음)도 포함
        int weekActualMin = weekRecords.stream()
                .filter(r -> r.getWorkMin() != null && r.getWorkMin() > 0)
                .mapToInt(AttendanceRecordDto::getWorkMin)
                .sum();

        // 연차/기타 휴가 승인된 날(checkIn 없는 날) → 주 전체 범위로 계획 근무분 추가
        List<AttendanceReqSummaryDto> weekRequests =
                calendarMapper.findRequests(company, empCode, weekStartStr, weekEndStr);
        List<AttendanceDayDto> weekDays =
                calendarMapper.findDailyShifts(company, empCode, weekStartStr, weekEndStr);

        Map<String, Integer> shiftWorkMinByDate = weekDays.stream()
                .filter(d -> d.getShiftWorkMin() != null)
                .collect(Collectors.toMap(AttendanceDayDto::getWorkDate, AttendanceDayDto::getShiftWorkMin));

        int leaveMin = weekRequests.stream()
                .filter(r -> "APPROVED".equals(r.getStatus()))
                .collect(Collectors.groupingBy(AttendanceReqSummaryDto::getWorkDate))
                .entrySet().stream()
                .filter(e -> !checkedInDates.contains(e.getKey()))
                .filter(e -> e.getValue().stream().anyMatch(r ->
                        ("OTHER".equals(r.getRequestCategory()) &&
                         (r.getChangeShiftOnHhmm() == null || r.getChangeShiftOnHhmm().isBlank()))
                        || "LEAVE".equals(r.getRequestCategory())))
                .mapToInt(e -> shiftWorkMinByDate.getOrDefault(e.getKey(), 0))
                .sum();
        weekActualMin += leaveMin;
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

        // 최근 근무 기록: 실출근 또는 출장 승인(actualShiftCode 있음) 최신 3건
        List<AttendanceRecordDto> recentRecords = monthRecords.stream()
                .filter(r -> (r.getCheckIn() != null && !r.getCheckIn().isBlank())
                          || (r.getWorkMin() != null && r.getWorkMin() > 0
                              && r.getActualShiftCode() != null && !r.getActualShiftCode().isBlank()))
                .sorted(Comparator.comparing(AttendanceRecordDto::getYyyymmdd).reversed())
                .limit(3)
                .peek(r -> {
                    LocalDate d = LocalDate.parse(r.getYyyymmdd(), YMD);
                    String day = DAY_LABELS[d.getDayOfWeek().getValue() % 7];
                    r.setDateDisplay(String.format("%02d.%02d (%s)", d.getMonthValue(), d.getDayOfMonth(), day));
                    // checkIn 없는 출장 레코드 → 표시용 레이블 세팅
                    if (r.getCheckIn() == null || r.getCheckIn().isBlank()) {
                        r.setPlannedShiftName("출장");
                        r.setLateYn("N");
                    }
                })
                .collect(Collectors.toList());
        model.addAttribute("recentRecords", recentRecords);

        // 3. 잔여 연차 / 사용 연차
        String availableLeave = "0";
        String usedLeave = "0";
        try {
            BigDecimal leave = annualLeaveService.availableDay(company, empCode, today.getYear());
            availableLeave = leave.stripTrailingZeros().toPlainString();
            BigDecimal used = annualLeaveService.usedDay(company, empCode, today.getYear());
            usedLeave = used.stripTrailingZeros().toPlainString();
        } catch (Exception ignored) {}
        model.addAttribute("availableLeave", availableLeave);
        model.addAttribute("usedLeave", usedLeave);

        // 4. 오늘 출퇴근 상태
        AttendanceRecordDto todayRecord = null;
        try {
            todayRecord = recordMapper.findByDay(company, empCode, today.format(YMD));
        } catch (Exception ignored) {}
        boolean todayIsTrip = todayRecord != null
                && (todayRecord.getCheckIn() == null || todayRecord.getCheckIn().isBlank())
                && todayRecord.getActualShiftCode() != null && !todayRecord.getActualShiftCode().isBlank();
        model.addAttribute("todayIsTrip",  todayIsTrip);
        model.addAttribute("todayRecord",  todayRecord);

        // 5. 이번 달 근무 요약 (연차/기타 휴가 포함, 출장 포함)
        int monthWorkDays = (int) monthRecords.stream()
                .filter(r -> (r.getCheckIn() != null && !r.getCheckIn().isBlank())
                          || (r.getWorkMin() != null && r.getWorkMin() > 0
                              && r.getActualShiftCode() != null && !r.getActualShiftCode().isBlank()))
                .count();
        int monthWorkMin = monthRecords.stream()
                .filter(r -> r.getWorkMin() != null && r.getWorkMin() > 0)
                .mapToInt(AttendanceRecordDto::getWorkMin)
                .sum();

        // 연차/기타 휴가 승인일 추가
        String monthStartStr = ym.atDay(1).toString();
        String monthEndStr   = ym.atEndOfMonth().toString();
        Set<String> monthCheckedInDates = monthRecords.stream()
                .filter(r -> r.getCheckIn() != null && !r.getCheckIn().isBlank())
                .map(r -> { String y = r.getYyyymmdd();
                    return y.substring(0, 4) + "-" + y.substring(4, 6) + "-" + y.substring(6, 8); })
                .collect(Collectors.toSet());
        List<AttendanceReqSummaryDto> monthRequests =
                calendarMapper.findRequests(company, empCode, monthStartStr, monthEndStr);
        List<AttendanceDayDto> monthDays =
                calendarMapper.findDailyShifts(company, empCode, monthStartStr, monthEndStr);
        Map<String, Integer> monthShiftMin = monthDays.stream()
                .filter(d -> d.getShiftWorkMin() != null)
                .collect(Collectors.toMap(AttendanceDayDto::getWorkDate, AttendanceDayDto::getShiftWorkMin));
        for (Map.Entry<String, List<AttendanceReqSummaryDto>> e :
                monthRequests.stream()
                        .filter(r -> "APPROVED".equals(r.getStatus()))
                        .collect(Collectors.groupingBy(AttendanceReqSummaryDto::getWorkDate))
                        .entrySet()) {
            if (monthCheckedInDates.contains(e.getKey())) continue;
            boolean isLeave = e.getValue().stream().anyMatch(r ->
                    ("OTHER".equals(r.getRequestCategory()) &&
                     (r.getChangeShiftOnHhmm() == null || r.getChangeShiftOnHhmm().isBlank()))
                    || "LEAVE".equals(r.getRequestCategory()));
            if (isLeave) {
                monthWorkDays++;
                monthWorkMin += monthShiftMin.getOrDefault(e.getKey(), 0);
            }
        }

        model.addAttribute("monthWorkDays", monthWorkDays);
        model.addAttribute("monthWorkTime", fmtMin(monthWorkMin));

        // 6. 대기 중인 신청 건수
        int pendingCount = 0;
        try {
            pendingCount = requestMapper.countSubmittedRequests(company, empCode);
        } catch (Exception ignored) {}
        model.addAttribute("pendingCount", pendingCount);

        // 7. 관리자/팀장: 오늘 미출근자 현황
        String deptLeaderCode = requestMapper.findDeptLeader(company, loginUser.getDeptCode());
        boolean isDeptLeader  = empCode.equals(deptLeaderCode);
        boolean isAdmin       = "ADMIN".equals(loginUser.getRoleCode());
        boolean canViewAll    = isAdmin || isDeptLeader;
        model.addAttribute("canViewAll", canViewAll);

        // 내가 승인해야 할 대기 건수 (전 직원 대상)
        int approvalPendingCount = 0;
        try {
            approvalPendingCount = approvalMapper.countMyPendingApprovals(company, empCode);
        } catch (Exception ignored) {}
        model.addAttribute("approvalPendingCount", approvalPendingCount);

        if (canViewAll) {
            List<String> accessibleCodes = requestMapper.findAccessibleDepts(company, loginUser.getDeptCode())
                    .stream().map(DepartmentDto::getDeptCode).collect(Collectors.toList());
            List<Map<String, Object>> absentList = Collections.emptyList();
            try {
                if (!accessibleCodes.isEmpty()) {
                    absentList = recordMapper.findAbsentToday(company, today.format(YMD), accessibleCodes);
                }
            } catch (Exception ignored) {}
            model.addAttribute("absentList", absentList);
        }

        return "main";
    }

    private String fmtMin(int min) {
        return (min / 60) + "h " + (min % 60) + "m";
    }

    private void fillNullWorkMin(List<AttendanceRecordDto> records, String company, String empCode) {
        for (AttendanceRecordDto r : records) {
            boolean hasCheckIn     = r.getCheckIn() != null && !r.getCheckIn().isBlank();
            boolean hasActualShift = r.getActualShiftCode() != null && !r.getActualShiftCode().isBlank();
            boolean needsRecalc    = (r.getWorkMin() == null && hasCheckIn)
                    || (Integer.valueOf(0).equals(r.getWorkMin()) && !hasCheckIn && hasActualShift);
            if (needsRecalc) {
                r.setCompany(company);
                r.setEmpCode(empCode);
                try {
                    r.setWorkMin(recordService.calculateWorkMin(r));
                } catch (Exception ignored) {}
            }
        }
    }
}