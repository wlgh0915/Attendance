package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import com.company.attendancemanagement.mapper.AttendanceRecordMapper;
import com.company.attendancemanagement.mapper.pattern.WorkPatternMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttendanceRecordServiceImpl implements AttendanceRecordService {

    private static final int MAX_WEEK_MIN = 3120; // 52시간
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final AttendanceRecordMapper recordMapper;
    private final WorkPatternMapper      workPatternMapper;

    @Override
    public List<AttendanceRecordDto> findByMonth(String company, String empCode,
                                                  String startYmd, String endYmd) {
        return recordMapper.findByMonth(company, empCode, startYmd, endYmd);
    }

    @Override
    public Map<String, Object> getPlannedShift(String company, String empCode, String yyyymmdd) {
        Map<String, Object> planned = recordMapper.findPlannedShift(company, empCode, yyyymmdd);
        return planned != null ? planned : Map.of();
    }

    @Override
    public void upsert(AttendanceRecordDto dto) {
        String company  = dto.getCompany();
        String empCode  = dto.getEmpCode();
        String yyyymmdd = dto.getYyyymmdd();

        // 1. 계획 시프트 조회 및 WORK_MIN 재계산
        Map<String, Object> planned = recordMapper.findPlannedShift(company, empCode, yyyymmdd);
        int workMin = calculateWorkMin(dto, planned, company, empCode, yyyymmdd);
        dto.setWorkMin(workMin);

        // 2. 주 52시간 초과 검증
        String weekStart   = toWeekBound(yyyymmdd, true);
        String weekEnd     = toWeekBound(yyyymmdd, false);
        int weekOtherMin   = recordMapper.sumWeekWorkMin(company, empCode, weekStart, weekEnd, yyyymmdd);
        int weekTotalMin   = weekOtherMin + workMin;
        if (weekTotalMin > MAX_WEEK_MIN) {
            int remaining = MAX_WEEK_MIN - weekOtherMin;
            throw new IllegalArgumentException(String.format(
                "주 52시간을 초과합니다. 이번 주 잔여 가능 시간: %d시간 %d분", remaining / 60, remaining % 60));
        }

        calculateLate(dto);
        recordMapper.upsert(dto);
    }

    private int calculateWorkMin(AttendanceRecordDto dto, Map<String, Object> planned,
                                 String company, String empCode, String yyyymmdd) {
        String checkIn  = dto.getCheckIn();
        String checkOut = dto.getCheckOut();

        // 출퇴근 시간 중 하나라도 없으면 수동 입력값 사용
        if (isBlank(checkIn) || isBlank(checkOut)) {
            return dto.getWorkMin() != null ? dto.getWorkMin() : 0;
        }

        LocalTime actualIn  = LocalTime.parse(checkIn);
        LocalTime actualOut = LocalTime.parse(checkOut);

        // 계획 시프트 없으면 실제 시간 그대로
        if (planned == null
                || planned.get("workOnHhmm") == null
                || planned.get("workOffHhmm") == null) {
            return rawMinutes(actualIn, actualOut);
        }

        String wdt = (String) planned.get("workDayType");
        // 휴무/휴일 → 실제 시간 그대로 (휴일근무 신청 처리)
        if ("OFF".equals(wdt) || "HOLIDAY".equals(wdt)) {
            return rawMinutes(actualIn, actualOut);
        }

        LocalTime planStart = LocalTime.parse((String) planned.get("workOnHhmm"));
        LocalTime planEnd   = LocalTime.parse((String) planned.get("workOffHhmm"));

        // 승인된 연장근무 신청 조회
        List<Map<String, Object>> reqs =
                recordMapper.findApprovedOvertimeRequests(company, empCode, yyyymmdd);

        // 유효 시작: planStart, 조출연장 승인 시 더 이른 시간으로 확장
        LocalTime effectiveStart = planStart;
        for (Map<String, Object> req : reqs) {
            if ("조출연장".equals(req.get("reqType")) && req.get("startTime") != null) {
                LocalTime approved = LocalTime.parse((String) req.get("startTime"));
                if (approved.isBefore(effectiveStart)) effectiveStart = approved;
            }
        }
        // 실제 체크인보다 이를 수 없음 (실제로 안 왔으면 카운트 불가)
        if (actualIn.isAfter(effectiveStart)) effectiveStart = actualIn;

        // 유효 종료: planEnd, 연장근무 승인 시 더 늦은 시간으로 확장
        LocalTime effectiveEnd = planEnd;
        for (Map<String, Object> req : reqs) {
            if ("연장".equals(req.get("reqType")) && req.get("endTime") != null) {
                LocalTime approved = LocalTime.parse((String) req.get("endTime"));
                if (approved.isAfter(effectiveEnd)) effectiveEnd = approved;
            }
        }
        // 실제 체크아웃보다 늦을 수 없음
        if (actualOut.isBefore(effectiveEnd)) effectiveEnd = actualOut;

        // 야간 근무 처리 (planEnd < planStart 이면 익일 종료)
        boolean overnight = planEnd.isBefore(planStart);
        int minutes = (int) Duration.between(effectiveStart, effectiveEnd).toMinutes();
        if (overnight && minutes < 0) minutes += 24 * 60;

        return Math.max(0, minutes);
    }

    private int rawMinutes(LocalTime from, LocalTime to) {
        int m = (int) Duration.between(from, to).toMinutes();
        return m < 0 ? m + 24 * 60 : m;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String toWeekBound(String yyyymmdd, boolean monday) {
        LocalDate date = LocalDate.parse(yyyymmdd, YMD);
        LocalDate bound = monday
                ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return bound.format(YMD);
    }

    @Override
    public void delete(String company, String empCode, String yyyymmdd) {
        recordMapper.delete(company, empCode, yyyymmdd);
    }

    private void calculateLate(AttendanceRecordDto dto) {
        if (dto.getCheckIn() == null || dto.getShiftCode() == null) {
            dto.setLateYn("N");
            dto.setLateMin(0);
            return;
        }

        ShiftCodeDto shift = workPatternMapper.findShiftByCode(dto.getCompany(), dto.getShiftCode());
        if (shift == null || shift.getWorkOnHhmm() == null
                || "OFF".equals(shift.getWorkDayType())
                || "HOLIDAY".equals(shift.getWorkDayType())) {
            dto.setLateYn("N");
            dto.setLateMin(0);
            return;
        }

        int scheduled = toMinutes(shift.getWorkOnHhmm());
        int actual    = toMinutes(dto.getCheckIn());

        if (actual > scheduled) {
            dto.setLateYn("Y");
            dto.setLateMin(actual - scheduled);
        } else {
            dto.setLateYn("N");
            dto.setLateMin(0);
        }
    }

    private int toMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
