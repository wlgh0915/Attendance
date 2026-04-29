package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import com.company.attendancemanagement.mapper.AttendanceRequestMapper;
import com.company.attendancemanagement.service.AttendanceCalendarService;
import com.company.attendancemanagement.service.AttendanceRecordService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance/record")
public class AttendanceRecordController {

    private final AttendanceRecordService   recordService;
    private final AttendanceCalendarService calendarService;
    private final AttendanceRequestMapper   requestMapper;

    private static final DateTimeFormatter YMD_FMT  = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String[]          DOW_LABELS = {"", "월", "화", "수", "목", "금", "토", "일"};

    /* ───────── 월별 출퇴근 실적 페이지 ───────── */
    @GetMapping
    public String recordPage(HttpSession session, Model model,
                             @RequestParam(required = false) String empCode,
                             @RequestParam(required = false) String deptCode,
                             @RequestParam(required = false) String ym) {

        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        String  company = loginUser.getCompany();
        boolean isAdmin = "ADMIN".equals(loginUser.getRoleCode());

        YearMonth yearMonth = (ym == null || ym.isBlank()) ? YearMonth.now() : YearMonth.parse(ym);

        List<DepartmentDto> depts = isAdmin
                ? calendarService.getAccessibleDepts(company, loginUser.getDeptCode()) : List.of();

        String selectedDept;
        if (isAdmin && deptCode != null && !deptCode.isBlank()) {
            boolean allowed = depts.stream().anyMatch(d -> d.getDeptCode().equals(deptCode));
            selectedDept = allowed ? deptCode : loginUser.getDeptCode();
        } else {
            selectedDept = loginUser.getDeptCode();
        }

        List<EmpSimpleDto> emps = isAdmin
                ? calendarService.getEmpsByDept(company, selectedDept) : List.of();

        String targetEmp;
        if (isAdmin && empCode != null && !empCode.isBlank()) {
            targetEmp = empCode;
        } else if (isAdmin && !emps.isEmpty()) {
            targetEmp = emps.get(0).getEmpCode();
        } else {
            targetEmp = loginUser.getEmpCode();
        }

        // DB에서 해당 월 실적 조회
        String startYmd = yearMonth.atDay(1).format(YMD_FMT);
        String endYmd   = yearMonth.atEndOfMonth().format(YMD_FMT);

        Map<String, AttendanceRecordDto> recordMap = recordService
                .findByMonth(company, targetEmp, startYmd, endYmd).stream()
                .collect(Collectors.toMap(AttendanceRecordDto::getYyyymmdd, r -> r));

        // 해당 월 전체 날짜 생성 → DB 실적과 merge
        List<AttendanceRecordDto> rows = new ArrayList<>();
        for (int d = 1; d <= yearMonth.lengthOfMonth(); d++) {
            LocalDate date = yearMonth.atDay(d);
            String ymd = date.format(YMD_FMT);

            AttendanceRecordDto row = recordMap.getOrDefault(ymd, new AttendanceRecordDto());
            row.setYyyymmdd(ymd);
            row.setDateDisplay(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            row.setDayLabel(DOW_LABELS[date.getDayOfWeek().getValue()]);
            row.setWeekend(date.getDayOfWeek() == DayOfWeek.SATURDAY
                        || date.getDayOfWeek() == DayOfWeek.SUNDAY);
            row.setHasRecord(recordMap.containsKey(ymd));
            rows.add(row);
        }

        Map<String, String> empInfo = calendarService.getEmpInfo(company, targetEmp);

        model.addAttribute("rows",         rows);
        model.addAttribute("depts",        depts);
        model.addAttribute("emps",         emps);
        model.addAttribute("empInfo",      empInfo);
        model.addAttribute("targetEmp",    targetEmp);
        model.addAttribute("selectedDept", selectedDept);
        model.addAttribute("isAdmin",      isAdmin);
        model.addAttribute("ymDisplay",    yearMonth.getYear() + "년 " + yearMonth.getMonthValue() + "월");
        model.addAttribute("currentYm",    yearMonth.toString());
        model.addAttribute("prevYm",       yearMonth.minusMonths(1).toString());
        model.addAttribute("nextYm",       yearMonth.plusMonths(1).toString());

        return "attendance/record";
    }

    /* ───────── 근태코드 목록 (모달 드롭다운용) ───────── */
    @GetMapping("/shift-codes")
    @ResponseBody
    public ResponseEntity<?> getShiftCodes(HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(requestMapper.findShiftCodes(loginUser.getCompany()));
    }

    /* ───────── 실적 저장 (UPSERT) ───────── */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> save(@RequestBody AttendanceRecordDto dto,
                                                     HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).body(fail("로그인이 필요합니다."));
        if (!"ADMIN".equals(loginUser.getRoleCode()))
            return ResponseEntity.status(403).body(fail("권한이 없습니다."));

        dto.setCompany(loginUser.getCompany());
        try {
            recordService.upsert(dto);
            return ResponseEntity.ok(Map.of("success", true, "message", "저장되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(fail(e.getMessage()));
        }
    }

    /* ───────── 실적 삭제 ───────── */
    @PostMapping("/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@RequestParam String empCode,
                                                       @RequestParam String yyyymmdd,
                                                       HttpSession session) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return ResponseEntity.status(401).body(fail("로그인이 필요합니다."));
        if (!"ADMIN".equals(loginUser.getRoleCode()))
            return ResponseEntity.status(403).body(fail("권한이 없습니다."));

        recordService.delete(loginUser.getCompany(), empCode, yyyymmdd);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private LoginUserDto getLoginUser(HttpSession session) {
        return (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
    }

    private Map<String, Object> fail(String message) {
        return Map.of("success", false, "message", message);
    }
}