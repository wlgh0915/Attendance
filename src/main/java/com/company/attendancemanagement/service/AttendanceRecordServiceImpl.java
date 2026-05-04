package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import com.company.attendancemanagement.mapper.AttendanceRecordMapper;
import com.company.attendancemanagement.mapper.pattern.WorkPatternMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttendanceRecordServiceImpl implements AttendanceRecordService {

    private static final int MAX_WEEK_MIN = 3120;
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final AttendanceRecordMapper recordMapper;
    private final WorkPatternMapper workPatternMapper;

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
        String company = dto.getCompany();
        String empCode = dto.getEmpCode();
        String yyyymmdd = dto.getYyyymmdd();

        Map<String, Object> planned = recordMapper.findPlannedShift(company, empCode, yyyymmdd);
        int workMin = calculateWorkMin(dto, planned, company, empCode, yyyymmdd);
        dto.setWorkMin(workMin);

        String weekStart = toWeekBound(yyyymmdd, true);
        String weekEnd = toWeekBound(yyyymmdd, false);
        int weekOtherMin = recordMapper.sumWeekWorkMin(company, empCode, weekStart, weekEnd, yyyymmdd);
        int weekTotalMin = weekOtherMin + workMin;
        if (weekTotalMin > MAX_WEEK_MIN) {
            int remaining = MAX_WEEK_MIN - weekOtherMin;
            throw new IllegalArgumentException(String.format(
                    "주 52시간을 초과합니다. 이번 주 잔여 가능 시간: %d시간 %d분",
                    remaining / 60, remaining % 60));
        }

        calculateLate(dto);
        recordMapper.upsert(dto);
    }

    private int calculateWorkMin(AttendanceRecordDto dto, Map<String, Object> planned,
                                 String company, String empCode, String yyyymmdd) {
        String checkIn = dto.getCheckIn();
        String checkOut = dto.getCheckOut();

        if (isBlank(checkIn) || isBlank(checkOut)) {
            return dto.getWorkMin() != null ? dto.getWorkMin() : 0;
        }

        int actualInMin = toMinutes(checkIn);
        int actualOutMin = toNextDayMinute(toMinutes(checkOut), actualInMin);

        if (planned == null
                || planned.get("workOnHhmm") == null
                || planned.get("workOffHhmm") == null) {
            return Math.max(0, actualOutMin - actualInMin);
        }

        String workDayType = (String) planned.get("workDayType");
        if ("OFF".equals(workDayType) || "HOLIDAY".equals(workDayType)) {
            int raw = actualOutMin - actualInMin;
            return Math.max(0, raw - breakOverlapMin(planned, actualInMin, actualOutMin));
        }

        int planStartMin = toMinutes((String) planned.get("workOnHhmm"));
        int planEndMin = toNextDayMinute(toMinutes((String) planned.get("workOffHhmm")), planStartMin);

        List<Map<String, Object>> reqs =
                recordMapper.findApprovedOvertimeRequests(company, empCode, yyyymmdd);

        int effectiveStartMin = planStartMin;
        int effectiveEndMin = planEndMin;
        for (Map<String, Object> req : reqs) {
            if ("조출연장".equals(req.get("reqType")) && req.get("startTime") != null) {
                int approvedMin = absoluteMinute((String) req.get("startTimeType"), (String) req.get("startTime"));
                if (approvedMin < effectiveStartMin) effectiveStartMin = approvedMin;
            }
            if ("연장".equals(req.get("reqType")) && req.get("endTime") != null) {
                int approvedMin = absoluteMinute((String) req.get("endTimeType"), (String) req.get("endTime"));
                if (approvedMin > effectiveEndMin) effectiveEndMin = approvedMin;
            }
        }

        if (actualInMin > effectiveStartMin) effectiveStartMin = actualInMin;
        if (actualOutMin < effectiveEndMin) effectiveEndMin = actualOutMin;

        int minutes = effectiveEndMin - effectiveStartMin;
        minutes -= breakOverlapMin(planned, effectiveStartMin, effectiveEndMin);
        return Math.max(0, minutes);
    }

    private int breakOverlapMin(Map<String, Object> planned, int startMin, int endMin) {
        return overlapBreak(planned.get("break1StartHhmm"), planned.get("break1EndHhmm"), startMin, endMin)
                + overlapBreak(planned.get("break2StartHhmm"), planned.get("break2EndHhmm"), startMin, endMin);
    }

    private int overlapBreak(Object breakStartValue, Object breakEndValue, int startMin, int endMin) {
        if (breakStartValue == null || breakEndValue == null) return 0;
        String breakStartText = breakStartValue.toString();
        String breakEndText = breakEndValue.toString();
        if (breakStartText.isBlank() || breakEndText.isBlank()) return 0;
        int breakStart = toMinutes(breakStartText);
        int breakEnd = toNextDayMinute(toMinutes(breakEndText), breakStart);
        if (endMin > 1440 && breakStart < startMin) {
            breakStart += 1440;
            breakEnd += 1440;
        }
        return Math.max(0, Math.min(endMin, breakEnd) - Math.max(startMin, breakStart));
    }

    private int absoluteMinute(String timeType, String hhmm) {
        return ("N1".equals(timeType) ? 1440 : 0) + toMinutes(hhmm);
    }

    private int toNextDayMinute(int minute, int baseMinute) {
        return minute < baseMinute ? minute + 1440 : minute;
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
        int actual = toMinutes(dto.getCheckIn());

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
